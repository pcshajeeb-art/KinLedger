package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.auth.DriveTokenProvider
import com.example.auth.DriveTokenResult
import com.example.data.model.AccountModel
import com.example.data.model.AccountWithBalance
import com.example.data.model.CategoryModel
import com.example.data.model.DebtDetail
import com.example.data.model.DebtRecordModel
import com.example.data.model.DebtSettlementDetail
import com.example.data.model.DebtSettlementEntry
import com.example.data.model.DebtType
import com.example.data.model.DriveLedgerDocument
import com.example.data.model.DriveSyncStatus
import com.example.data.model.DriveUserIndex
import com.example.data.model.DriveWorkspaceRef
import com.example.data.model.FamilyWorkspaceModel
import com.example.data.model.LedgerUserInfo
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionModel
import com.example.data.model.TransactionType
import com.example.data.model.UserProfileModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import java.util.UUID

internal data class DriveCacheEnvelope(
    val userIndex: DriveUserIndex = DriveUserIndex(),
    val ledgersByWorkspaceId: Map<String, DriveLedgerDocument> = emptyMap(),
    val dirtyWorkspaceIds: Set<String> = emptySet(),
    val dirtyProfile: Boolean = false,
    val profileDriveFileId: String = "",
    val appFolderId: String = "",
    val lastSyncedMillis: Long = 0L
)

/**
 * Offline-first family ledger repository backed by JSON files stored in the user's Google Drive:
 * - Private user index (`kinledger_profile.json`) in Drive `appDataFolder`
 * - Ledger documents (`kinledger_<workspaceId>.json`) inside a dedicated `"KinLedger"` Drive folder
 * - Per-user atomic local JSON cache in `filesDir` for instant reads/writes offline
 */
class DriveLedgerRepository(
    private val context: Context,
    private val tokenProvider: DriveTokenProvider,
    private val transport: DriveTransport = OkHttpDriveTransport(),
    private val cacheDirProvider: () -> File = { context.filesDir }
) : LedgerRepository {

    companion object {
        private const val TAG = "DriveLedgerRepo"
        const val DRIVE_FOLDER_NAME = "KinLedger"
        const val PROFILE_FILE_NAME = "kinledger_profile.json"
        private const val PROP_WORKSPACE_ID = "kinledgerWorkspaceId"
        private const val PROP_INVITE_CODE = "kinledgerInviteCode"
        private val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
        private val RANDOM = SecureRandom()

        private val cacheMoshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        private val cacheAdapter = cacheMoshi.adapter(DriveCacheEnvelope::class.java)

        private fun safeLogWarn(message: String, error: Throwable) {
            runCatching { Log.w(TAG, message, error) }
        }

        private fun nextTimestamp(previousMillis: Long): Long =
            maxOf(System.currentTimeMillis(), previousMillis + 1L)

        private fun generateInviteCode(): String =
            buildString(6) {
                repeat(6) {
                    append(INVITE_ALPHABET[RANDOM.nextInt(INVITE_ALPHABET.size)])
                }
            }

        internal fun extractDriveFileIdOrCode(input: String): Pair<String?, String?> {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null to null
            val urlRegex = Regex("""/d/([a-zA-Z0-9_-]{10,})""")
            urlRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it to null }
            val idParamRegex = Regex("""[?&]id=([a-zA-Z0-9_-]{10,})""")
            idParamRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it to null }
            val upper = trimmed.uppercase()
            if (upper.matches(Regex("^[A-Z0-9]{6}$"))) return null to upper
            if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{12,}$"))) return trimmed to null
            return null to null
        }
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()
    private val syncMutex = Mutex()

    @Volatile
    private var loadedUserId: String? = null

    @Volatile
    private var currentUserInfo: LedgerUserInfo? = null

    private val envelopeFlow = MutableStateFlow(DriveCacheEnvelope())
    private val syncStatusFlow = MutableStateFlow<DriveSyncStatus>(DriveSyncStatus.Idle)

    override fun observeSyncStatus(): Flow<DriveSyncStatus> = syncStatusFlow

    override fun observeAvailableWorkspaces(): Flow<List<DriveWorkspaceRef>> =
        envelopeFlow.map { env ->
            val fromDocs = env.ledgersByWorkspaceId.values.map { doc ->
                DriveWorkspaceRef(
                    workspaceId = doc.workspace.id,
                    name = doc.workspace.name,
                    inviteCode = doc.workspace.inviteCode,
                    currencyCode = doc.workspace.currencyCode,
                    driveFileId = doc.workspace.driveFileId,
                    driveFileName = doc.workspace.driveFileName,
                    updatedAtMillis = maxOf(doc.updatedAtMillis, doc.workspace.updatedAtMillis)
                )
            }
            val merged = (env.userIndex.workspaceRefs + fromDocs)
                .groupBy { it.workspaceId }
                .mapNotNull { (_, items) ->
                    items.maxByOrNull { it.updatedAtMillis }
                }
                .sortedByDescending { it.updatedAtMillis }
            merged
        }

    override fun observeUserProfile(userId: String): Flow<UserProfileModel?> =
        envelopeFlow.map { env ->
            val profile = env.userIndex.userProfile
            if (profile.userId == userId && profile.id.isNotBlank()) profile else null
        }

    override fun observeWorkspace(workspaceId: String): Flow<FamilyWorkspaceModel?> =
        envelopeFlow.map { env -> env.ledgersByWorkspaceId[workspaceId]?.workspace }

    override fun observeAccountsWithBalance(workspaceId: String, userId: String): Flow<List<AccountWithBalance>> =
        envelopeFlow.map { env ->
            val doc = env.ledgersByWorkspaceId[workspaceId] ?: return@map emptyList()
            val accts = doc.accounts
                .sortedWith(compareByDescending<AccountModel> { it.defaultAccount }.thenBy { it.name.lowercase() })
            BalanceCalculator.computeAccountBalances(accts, doc.transactions, doc.debts)
        }

    override fun observeCategories(workspaceId: String, userId: String): Flow<List<CategoryModel>> =
        envelopeFlow.map { env ->
            val doc = env.ledgersByWorkspaceId[workspaceId] ?: return@map emptyList()
            doc.categories
                .sortedWith(compareBy<CategoryModel> { it.type }.thenBy { it.sortOrder }.thenBy { it.name.lowercase() })
        }

    override fun observeTransactions(workspaceId: String, userId: String): Flow<List<TransactionDetail>> =
        envelopeFlow.map { env ->
            val doc = env.ledgersByWorkspaceId[workspaceId] ?: return@map emptyList()
            val acctMap = doc.accounts.associateBy { it.id }
            val catMap = doc.categories.associateBy { it.id }
            doc.transactions
                .sortedByDescending { it.dateMillis }
                .map { tx ->
                    TransactionDetail(
                        transaction = tx,
                        account = acctMap[tx.accountId],
                        destinationAccount = tx.destinationAccountId?.let { acctMap[it] },
                        category = tx.categoryId?.let { catMap[it] }
                    )
                }
        }

    override fun observeDebtRecords(workspaceId: String, userId: String): Flow<List<DebtDetail>> =
        envelopeFlow.map { env ->
            val doc = env.ledgersByWorkspaceId[workspaceId] ?: return@map emptyList()
            val acctMap = doc.accounts.associateBy { it.id }
            doc.debts
                .sortedWith(compareBy<DebtRecordModel> { it.settled }.thenByDescending { it.dateMillis })
                .map { debt ->
                    DebtDetail(
                        debt = debt,
                        sourceAccount = acctMap[debt.sourceAccountId],
                        settlementAccount = debt.settlementAccountId?.let { acctMap[it] },
                        settlementDetails = debt.settlements
                            .sortedByDescending { it.dateMillis }
                            .map { entry ->
                                DebtSettlementDetail(
                                    entry = entry,
                                    account = acctMap[entry.accountId]
                                )
                            }
                    )
                }
        }

    private fun cacheFileFor(userId: String): File {
        val safeUid = userId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
        return File(cacheDirProvider(), "kinledger_drive_cache_$safeUid.json")
    }

    private suspend fun ensureLoadedLocked(userId: String) {
        if (loadedUserId == userId) return
        val file = cacheFileFor(userId)
        val loaded = withContext(Dispatchers.IO) {
            if (file.exists()) {
                runCatching { cacheAdapter.fromJson(file.readText()) }.getOrNull()
            } else null
        } ?: DriveCacheEnvelope()
        loadedUserId = userId
        envelopeFlow.value = loaded
        if (loaded.lastSyncedMillis > 0L && !loaded.dirtyProfile && loaded.dirtyWorkspaceIds.isEmpty()) {
            syncStatusFlow.value = DriveSyncStatus.Synced(loaded.lastSyncedMillis)
        } else if (loaded.dirtyProfile || loaded.dirtyWorkspaceIds.isNotEmpty()) {
            syncStatusFlow.value = DriveSyncStatus.PendingLocal("Saved locally • Waiting to sync")
        }
    }

    private suspend fun updateCache(
        transform: (DriveCacheEnvelope) -> DriveCacheEnvelope
    ): DriveCacheEnvelope {
        val uid = currentUserInfo?.uid ?: loadedUserId
            ?: throw IllegalStateException("User must be signed in with Google.")
        return stateMutex.withLock {
            ensureLoadedLocked(uid)
            val next = transform(envelopeFlow.value)
            envelopeFlow.value = next
            withContext(Dispatchers.IO) {
                val target = cacheFileFor(uid)
                val tmp = File(target.parentFile, "${target.name}.tmp")
                tmp.writeText(cacheAdapter.toJson(next))
                if (!tmp.renameTo(target)) {
                    target.writeText(cacheAdapter.toJson(next))
                    tmp.delete()
                }
            }
            next
        }
    }

    private fun triggerBackgroundSync() {
        repoScope.launch {
            syncWithDrive(forcePull = false)
        }
    }

    override suspend fun ensureUserProfile(user: LedgerUserInfo): Result<Unit> {
        currentUserInfo = user
        val now = System.currentTimeMillis()
        val cleanName = user.displayName.trim().ifEmpty {
            user.email.trim().ifEmpty { "Family Member" }
        }.take(80)
        val cleanEmail = user.email.trim().take(160)

        updateCache { env ->
            val existing = env.userIndex.userProfile
            val updatedProfile = if (existing.userId == user.uid && existing.id.isNotBlank()) {
                existing.copy(
                    displayName = cleanName,
                    email = cleanEmail,
                    updatedAtMillis = now
                )
            } else {
                UserProfileModel(
                    id = user.uid,
                    userId = user.uid,
                    displayName = cleanName,
                    email = cleanEmail,
                    activeWorkspaceId = null,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            }
            val isNewLocalProfile = existing.id.isBlank()
            val nameOrEmailChanged = !isNewLocalProfile && (existing.displayName != cleanName || existing.email != cleanEmail)
            env.copy(
                userIndex = env.userIndex.copy(
                    userProfile = updatedProfile,
                    updatedAtMillis = if (isNewLocalProfile) 0L else now
                ),
                dirtyProfile = env.dirtyProfile || nameOrEmailChanged
            )
        }

        // Pull from Google Drive on startup so new devices discover profile & workspaces automatically
        syncWithDrive(forcePull = true)
        return Result.success(Unit)
    }

    override suspend fun syncWithDrive(forcePull: Boolean): Result<Unit> = syncMutex.withLock {
        val user = currentUserInfo ?: return Result.failure(IllegalStateException("Not signed in with Google"))
        val currentState = envelopeFlow.value
        if (!forcePull &&
            currentState.lastSyncedMillis > 0L &&
            !currentState.dirtyProfile &&
            currentState.dirtyWorkspaceIds.isEmpty()
        ) {
            syncStatusFlow.value = DriveSyncStatus.Synced(currentState.lastSyncedMillis)
            return Result.success(Unit)
        }
        val priorStatus = syncStatusFlow.value
        syncStatusFlow.value = DriveSyncStatus.Syncing

        val token = when (val tokenRes = tokenProvider.getDriveAccessToken(forceRefresh = false)) {
            is DriveTokenResult.Token -> tokenRes.accessToken
            is DriveTokenResult.NeedsConsent -> {
                syncStatusFlow.value = DriveSyncStatus.NeedsAuthorization(tokenRes.pendingIntent)
                return Result.failure(IllegalStateException("Google Drive permission required"))
            }
            is DriveTokenResult.Failure -> {
                val msg = tokenRes.error.localizedMessage ?: "Unable to authorize Google Drive"
                syncStatusFlow.value = DriveSyncStatus.Error(msg)
                return Result.failure(tokenRes.error)
            }
        }

        return try {
            performDriveSync(token, user, forcePull)
            Result.success(Unit)
        } catch (unauth: DriveUnauthorizedException) {
            tokenProvider.invalidateAccessToken()
            when (val retryRes = tokenProvider.getDriveAccessToken(forceRefresh = true)) {
                is DriveTokenResult.Token -> {
                    try {
                        performDriveSync(retryRes.accessToken, user, forcePull)
                        Result.success(Unit)
                    } catch (e: Exception) {
                        val msg = e.localizedMessage ?: "Drive sync failed"
                        syncStatusFlow.value = DriveSyncStatus.Error(msg)
                        Result.failure(e)
                    }
                }
                is DriveTokenResult.NeedsConsent -> {
                    syncStatusFlow.value = DriveSyncStatus.NeedsAuthorization(retryRes.pendingIntent)
                    Result.failure(unauth)
                }
                is DriveTokenResult.Failure -> {
                    syncStatusFlow.value = DriveSyncStatus.Error(
                        retryRes.error.localizedMessage ?: "Drive authorization failed"
                    )
                    Result.failure(retryRes.error)
                }
            }
        } catch (c: CancellationException) {
            syncStatusFlow.value = priorStatus
            throw c
        } catch (e: Exception) {
            safeLogWarn("Drive sync error", e)
            val env = envelopeFlow.value
            syncStatusFlow.value = if (env.dirtyProfile || env.dirtyWorkspaceIds.isNotEmpty()) {
                DriveSyncStatus.PendingLocal("Offline • Local changes will sync when online")
            } else {
                DriveSyncStatus.Error(e.localizedMessage ?: "Unable to sync with Google Drive")
            }
            Result.failure(e)
        }
    }

    private suspend fun performDriveSync(
        accessToken: String,
        user: LedgerUserInfo,
        forcePull: Boolean
    ) {
        // 1. Ensure dedicated "KinLedger" folder exists in Google Drive
        val folderId = resolveOrCreateAppFolder(accessToken)

        // 2. Sync user profile index in appDataFolder
        val profileFiles = transport.listFiles(
            accessToken = accessToken,
            query = "name = '$PROFILE_FILE_NAME' and trashed = false",
            spaces = "appDataFolder"
        )
        val remoteProfileMeta = profileFiles.firstOrNull()
        val remoteIndex = remoteProfileMeta?.let { meta ->
            val raw = transport.downloadFile(accessToken, meta.id)
            DriveJsonCodec.decodeUserIndex(raw)
        }

        val currentEnv = envelopeFlow.value
        val shouldPullProfile = remoteIndex != null && (!currentEnv.dirtyProfile || (forcePull && remoteIndex.updatedAtMillis > currentEnv.userIndex.updatedAtMillis))

        if (shouldPullProfile) {
            val nonNullRemoteIndex = remoteIndex!!
            updateCache { env ->
                val activeWs = nonNullRemoteIndex.userProfile.activeWorkspaceId ?: env.userIndex.userProfile.activeWorkspaceId
                val mergedRefs = (env.userIndex.workspaceRefs + nonNullRemoteIndex.workspaceRefs)
                    .groupBy { it.workspaceId }
                    .mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAtMillis } }
                env.copy(
                    profileDriveFileId = remoteProfileMeta.id,
                    appFolderId = folderId,
                    userIndex = env.userIndex.copy(
                        userProfile = env.userIndex.userProfile.copy(
                            activeWorkspaceId = activeWs,
                            updatedAtMillis = maxOf(env.userIndex.userProfile.updatedAtMillis, nonNullRemoteIndex.userProfile.updatedAtMillis)
                        ),
                        workspaceRefs = mergedRefs,
                        updatedAtMillis = maxOf(env.userIndex.updatedAtMillis, nonNullRemoteIndex.updatedAtMillis)
                    ),
                    dirtyProfile = false
                )
            }
        } else if (remoteProfileMeta != null && currentEnv.profileDriveFileId.isBlank()) {
            updateCache { it.copy(profileDriveFileId = remoteProfileMeta.id, appFolderId = folderId) }
        } else if (currentEnv.appFolderId != folderId) {
            updateCache { it.copy(appFolderId = folderId) }
        }

        // 3. Discover JSON ledger files inside the dedicated "KinLedger" Drive folder
        val folderFiles = transport.listFiles(
            accessToken = accessToken,
            query = "'$folderId' in parents and trashed = false",
            spaces = "drive"
        )

        val discoveredRefs = mutableListOf<DriveWorkspaceRef>()
        val pulledDocs = mutableMapOf<String, DriveLedgerDocument>()
        val latestEnv = envelopeFlow.value

        // Check active workspace & any refs stored in profile or folder
        val knownFileIds = buildMap<String, String> {
            folderFiles.forEach { file ->
                val wsId = file.appProperties[PROP_WORKSPACE_ID]
                if (!wsId.isNullOrBlank()) put(wsId, file.id)
            }
            latestEnv.userIndex.workspaceRefs.forEach { ref ->
                if (ref.driveFileId.isNotBlank()) putIfAbsent(ref.workspaceId, ref.driveFileId)
            }
        }

        // Download folder ledgers that are missing locally or when pulling updates
        for ((wsId, fileId) in knownFileIds) {
            val localDoc = latestEnv.ledgersByWorkspaceId[wsId]
            val isDirty = wsId in latestEnv.dirtyWorkspaceIds
            val isActive = latestEnv.userIndex.userProfile.activeWorkspaceId == wsId
            if (localDoc == null || (!isDirty && (forcePull || isActive))) {
                val rawJson = runCatching { transport.downloadFile(accessToken, fileId) }.getOrNull() ?: continue
                val remoteDoc = DriveJsonCodec.decodeLedgerDocument(rawJson) ?: continue
                val normalizedDoc = remoteDoc.copy(
                    workspace = remoteDoc.workspace.copy(
                        driveFileId = fileId,
                        driveFileName = remoteDoc.workspace.driveFileName.ifBlank { "kinledger_${remoteDoc.workspace.id}.json" }
                    )
                )
                if (localDoc == null || normalizedDoc.updatedAtMillis >= localDoc.updatedAtMillis) {
                    pulledDocs[normalizedDoc.workspace.id] = normalizedDoc
                    discoveredRefs += DriveWorkspaceRef(
                        workspaceId = normalizedDoc.workspace.id,
                        name = normalizedDoc.workspace.name,
                        inviteCode = normalizedDoc.workspace.inviteCode,
                        currencyCode = normalizedDoc.workspace.currencyCode,
                        driveFileId = fileId,
                        driveFileName = normalizedDoc.workspace.driveFileName,
                        updatedAtMillis = normalizedDoc.updatedAtMillis
                    )
                }
            }
        }

        if (pulledDocs.isNotEmpty() || discoveredRefs.isNotEmpty()) {
            updateCache { env ->
                val mergedRefs = (env.userIndex.workspaceRefs + discoveredRefs)
                    .groupBy { it.workspaceId }
                    .mapNotNull { (_, items) -> items.maxByOrNull { it.updatedAtMillis } }
                env.copy(
                    ledgersByWorkspaceId = env.ledgersByWorkspaceId + pulledDocs,
                    userIndex = env.userIndex.copy(workspaceRefs = mergedRefs)
                )
            }
        }

        // 4. Push any dirty workspace JSON documents to Drive
        val envBeforePush = envelopeFlow.value
        val pushedIds = mutableSetOf<String>()
        val updatedDocsAfterPush = mutableMapOf<String, DriveLedgerDocument>()

        for (wsId in envBeforePush.dirtyWorkspaceIds) {
            val doc = envBeforePush.ledgersByWorkspaceId[wsId] ?: continue
            val fileName = doc.workspace.driveFileName.ifBlank { "kinledger_$wsId.json" }
            val existingFileId = doc.workspace.driveFileId.ifBlank {
                knownFileIds[wsId] ?: ""
            }
            val docToUpload = doc.copy(
                workspace = doc.workspace.copy(driveFileName = fileName)
            )
            val uploadedMeta = if (existingFileId.isNotBlank()) {
                try {
                    transport.updateJsonFile(
                        accessToken = accessToken,
                        fileId = existingFileId,
                        jsonContent = DriveJsonCodec.encodeLedgerDocument(docToUpload)
                    )
                } catch (e: DriveApiException) {
                    if (e.code == 404) {
                        transport.createJsonFile(
                            accessToken = accessToken,
                            name = fileName,
                            parentId = folderId,
                            jsonContent = DriveJsonCodec.encodeLedgerDocument(docToUpload),
                            appProperties = mapOf(
                                PROP_WORKSPACE_ID to wsId,
                                PROP_INVITE_CODE to doc.workspace.inviteCode
                            )
                        )
                    } else throw e
                }
            } else {
                transport.createJsonFile(
                    accessToken = accessToken,
                    name = fileName,
                    parentId = folderId,
                    jsonContent = DriveJsonCodec.encodeLedgerDocument(docToUpload),
                    appProperties = mapOf(
                        PROP_WORKSPACE_ID to wsId,
                        PROP_INVITE_CODE to doc.workspace.inviteCode
                    )
                )
            }
            updatedDocsAfterPush[wsId] = docToUpload.copy(
                workspace = docToUpload.workspace.copy(
                    driveFileId = uploadedMeta.id,
                    driveFileName = uploadedMeta.name.ifBlank { fileName }
                )
            )
            pushedIds += wsId
        }

        if (pushedIds.isNotEmpty()) {
            updateCache { env ->
                val nextDocs = env.ledgersByWorkspaceId.toMutableMap()
                val nextDirty = env.dirtyWorkspaceIds.toMutableSet()
                val syncedRefs = mutableListOf<DriveWorkspaceRef>()

                for ((wsId, uploadedDoc) in updatedDocsAfterPush) {
                    val currentLocal = nextDocs[wsId]
                    val fileId = uploadedDoc.workspace.driveFileId
                    val fileName = uploadedDoc.workspace.driveFileName
                    if (currentLocal == null || currentLocal.updatedAtMillis <= uploadedDoc.updatedAtMillis) {
                        nextDocs[wsId] = uploadedDoc
                        nextDirty.remove(wsId)
                        syncedRefs += DriveWorkspaceRef(
                            workspaceId = uploadedDoc.workspace.id,
                            name = uploadedDoc.workspace.name,
                            inviteCode = uploadedDoc.workspace.inviteCode,
                            currencyCode = uploadedDoc.workspace.currencyCode,
                            driveFileId = fileId,
                            driveFileName = fileName,
                            updatedAtMillis = uploadedDoc.updatedAtMillis
                        )
                    } else {
                        // A newer local edit arrived while uploading; attach Drive fileId/name and keep dirty for next push
                        nextDocs[wsId] = currentLocal.copy(
                            workspace = currentLocal.workspace.copy(
                                driveFileId = fileId,
                                driveFileName = fileName
                            )
                        )
                    }
                }

                val mergedRefs = (env.userIndex.workspaceRefs + syncedRefs)
                    .groupBy { it.workspaceId }
                    .mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAtMillis } }
                env.copy(
                    ledgersByWorkspaceId = nextDocs,
                    dirtyWorkspaceIds = nextDirty,
                    dirtyProfile = true,
                    userIndex = env.userIndex.copy(workspaceRefs = mergedRefs)
                )
            }
        }

        // 5. Push profile index to appDataFolder if dirty or missing on Drive
        val envForProfile = envelopeFlow.value
        val existingProfileId = envForProfile.profileDriveFileId.ifBlank { remoteProfileMeta?.id ?: "" }
        if (envForProfile.dirtyProfile || existingProfileId.isBlank()) {
            val profileJson = DriveJsonCodec.encodeUserIndex(envForProfile.userIndex)
            val savedProfileMeta = if (existingProfileId.isNotBlank()) {
                try {
                    transport.updateJsonFile(accessToken, existingProfileId, profileJson)
                } catch (e: DriveApiException) {
                    if (e.code == 404) {
                        transport.createJsonFile(
                            accessToken = accessToken,
                            name = PROFILE_FILE_NAME,
                            parentId = "appDataFolder",
                            jsonContent = profileJson,
                            appProperties = mapOf("userId" to user.uid)
                        )
                    } else throw e
                }
            } else {
                transport.createJsonFile(
                    accessToken = accessToken,
                    name = PROFILE_FILE_NAME,
                    parentId = "appDataFolder",
                    jsonContent = profileJson,
                    appProperties = mapOf("userId" to user.uid)
                )
            }
            val now = System.currentTimeMillis()
            updateCache { env ->
                env.copy(
                    profileDriveFileId = savedProfileMeta.id,
                    dirtyProfile = false,
                    lastSyncedMillis = now
                )
            }
            syncStatusFlow.value = DriveSyncStatus.Synced(now)
        } else {
            val now = System.currentTimeMillis()
            updateCache { it.copy(lastSyncedMillis = now) }
            syncStatusFlow.value = DriveSyncStatus.Synced(now)
        }
    }

    private suspend fun resolveOrCreateAppFolder(accessToken: String): String {
        val cached = envelopeFlow.value.appFolderId
        if (cached.isNotBlank()) {
            val meta = runCatching { transport.getFileMeta(accessToken, cached) }.getOrNull()
            if (meta != null) return meta.id
        }
        val existing = transport.listFiles(
            accessToken = accessToken,
            query = "name = '$DRIVE_FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
            spaces = "drive"
        ).firstOrNull()
        if (existing != null) return existing.id
        return transport.createFolder(accessToken, DRIVE_FOLDER_NAME).id
    }

    override suspend fun openExistingWorkspace(workspaceId: String): Result<Unit> {
        val now = System.currentTimeMillis()
        val env = envelopeFlow.value
        val hasLocal = env.ledgersByWorkspaceId.containsKey(workspaceId)
        val hasRef = env.userIndex.workspaceRefs.any { it.workspaceId == workspaceId }
        if (!hasLocal && !hasRef) {
            return Result.failure(IllegalArgumentException("Ledger not found"))
        }
        updateCache { current ->
            current.copy(
                userIndex = current.userIndex.copy(
                    userProfile = current.userIndex.userProfile.copy(
                        activeWorkspaceId = workspaceId,
                        updatedAtMillis = now
                    ),
                    updatedAtMillis = now
                ),
                dirtyProfile = true
            )
        }
        syncWithDrive(forcePull = true)
        return Result.success(Unit)
    }

    override suspend fun createFamilyWorkspace(name: String, currencyCode: String): Result<String> {
        val user = currentUserInfo ?: return Result.failure(IllegalStateException("Sign in with Google first."))
        val cleanName = name.trim().ifEmpty { "Family Ledger" }.take(60)
        val cleanCurrency = currencyCode.trim().ifEmpty { "INR" }.take(6)
        val wsId = "ws_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val inviteCode = generateInviteCode()
        val now = System.currentTimeMillis()
        val members = listOf(user.uid)
        val fileName = "kinledger_$wsId.json"

        val workspace = FamilyWorkspaceModel(
            id = wsId,
            name = cleanName,
            inviteCode = inviteCode,
            ownerId = user.uid,
            memberUids = members,
            currencyCode = cleanCurrency,
            driveFileName = fileName,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val cash = AccountModel(
            id = "acct_${UUID.randomUUID().toString().take(8)}",
            workspaceId = wsId,
            name = "Cash",
            initialBalanceMinor = 0L,
            cachedBalanceMinor = 0L,
            iconKey = "wallet",
            colorArgb = 0xFF0D9488L,
            defaultAccount = true,
            memberUids = members,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val bank = AccountModel(
            id = "acct_${UUID.randomUUID().toString().take(8)}",
            workspaceId = wsId,
            name = "Bank",
            initialBalanceMinor = 0L,
            cachedBalanceMinor = 0L,
            iconKey = "bank",
            colorArgb = 0xFF0F766EL,
            defaultAccount = true,
            memberUids = members,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val categories = seedDefaultCategories(wsId, members, now)
        val doc = DriveLedgerDocument(
            workspace = workspace,
            accounts = listOf(cash, bank),
            categories = categories,
            transactions = emptyList(),
            debts = emptyList(),
            updatedAtMillis = now
        )
        val ref = DriveWorkspaceRef(
            workspaceId = wsId,
            name = cleanName,
            inviteCode = inviteCode,
            currencyCode = cleanCurrency,
            driveFileId = "",
            driveFileName = fileName,
            updatedAtMillis = now
        )

        updateCache { env ->
            env.copy(
                userIndex = env.userIndex.copy(
                    userProfile = env.userIndex.userProfile.copy(
                        activeWorkspaceId = wsId,
                        updatedAtMillis = now
                    ),
                    workspaceRefs = (env.userIndex.workspaceRefs.filterNot { it.workspaceId == wsId } + ref),
                    updatedAtMillis = now
                ),
                ledgersByWorkspaceId = env.ledgersByWorkspaceId + (wsId to doc),
                dirtyWorkspaceIds = env.dirtyWorkspaceIds + wsId,
                dirtyProfile = true
            )
        }
        triggerBackgroundSync()
        return Result.success(wsId)
    }

    private fun seedDefaultCategories(
        wsId: String,
        members: List<String>,
        now: Long
    ): List<CategoryModel> {
        data class SeedCat(
            val key: String,
            val name: String,
            val type: TransactionType,
            val icon: String,
            val color: Long,
            val primary: Boolean,
            val order: Int
        )
        val seeds = listOf(
            SeedCat("grocery", "Grocery & Household", TransactionType.EXPENSE, "shopping_cart", 0xFF0D9488L, true, 1),
            SeedCat("dining", "Dining & Food Delivery", TransactionType.EXPENSE, "restaurant", 0xFFE11D48L, false, 2),
            SeedCat("gift", "Gift", TransactionType.EXPENSE, "card_giftcard", 0xFF8B5CF6L, false, 3),
            SeedCat("medical", "Hospital & Medical Expense", TransactionType.EXPENSE, "medical_services", 0xFFEF4444L, false, 4),
            SeedCat("utilities", "Utilities & Bills", TransactionType.EXPENSE, "receipt_long", 0xFFD97706L, false, 5),
            SeedCat("misc", "Miscellaneous", TransactionType.EXPENSE, "category", 0xFF64748BL, false, 6),
            SeedCat("remittance", "NRI Remittance", TransactionType.INCOME, "currency_exchange", 0xFF0D9488L, true, 1),
            SeedCat("other_income", "Other Income / Refunds", TransactionType.INCOME, "savings", 0xFF059669L, false, 2)
        )
        return seeds.map { seed ->
            CategoryModel(
                id = "cat_${seed.key}_${UUID.randomUUID().toString().take(6)}",
                workspaceId = wsId,
                name = seed.name,
                type = seed.type.name,
                iconKey = seed.icon,
                colorArgb = seed.color,
                primaryCategory = seed.primary,
                archived = false,
                sortOrder = seed.order,
                memberUids = members,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }
    }

    override suspend fun joinFamilyWorkspace(inviteCode: String): Result<String> {
        val user = currentUserInfo ?: return Result.failure(IllegalStateException("Sign in with Google first."))
        val (fileIdInput, cleanCode) = extractDriveFileIdOrCode(inviteCode)
        if (fileIdInput == null && cleanCode == null) {
            return Result.failure(
                IllegalArgumentException("Enter a 6-character invite code or a Google Drive ledger File ID/link.")
            )
        }

        // 1. Check local cached workspaces first for invite code match
        if (cleanCode != null) {
            val localMatch = envelopeFlow.value.ledgersByWorkspaceId.values.firstOrNull {
                it.workspace.inviteCode.equals(cleanCode, ignoreCase = true)
            }
            if (localMatch != null) {
                if (user.uid !in localMatch.workspace.memberUids) {
                    val now = nextTimestamp(localMatch.updatedAtMillis)
                    val updatedDoc = localMatch.copy(
                        workspace = localMatch.workspace.copy(
                            memberUids = (localMatch.workspace.memberUids + user.uid).distinct(),
                            updatedAtMillis = now
                        ),
                        updatedAtMillis = now
                    )
                    updateCache { env ->
                        env.copy(
                            userIndex = env.userIndex.copy(
                                userProfile = env.userIndex.userProfile.copy(
                                    activeWorkspaceId = localMatch.workspace.id,
                                    updatedAtMillis = now
                                ),
                                updatedAtMillis = now
                            ),
                            ledgersByWorkspaceId = env.ledgersByWorkspaceId + (localMatch.workspace.id to updatedDoc),
                            dirtyWorkspaceIds = env.dirtyWorkspaceIds + localMatch.workspace.id,
                            dirtyProfile = true
                        )
                    }
                    syncWithDrive(forcePull = false)
                } else {
                    openExistingWorkspace(localMatch.workspace.id)
                }
                return Result.success(localMatch.workspace.id)
            }
        }

        // 2. Query Google Drive for the shared/remote JSON ledger
        val token = when (val tokenRes = tokenProvider.getDriveAccessToken()) {
            is DriveTokenResult.Token -> tokenRes.accessToken
            is DriveTokenResult.NeedsConsent -> {
                syncStatusFlow.value = DriveSyncStatus.NeedsAuthorization(tokenRes.pendingIntent)
                return Result.failure(IllegalStateException("Please authorize Google Drive access first to open shared ledgers."))
            }
            is DriveTokenResult.Failure -> {
                return Result.failure(tokenRes.error)
            }
        }

        return try {
            val matchedFileMeta: DriveFileMeta = if (fileIdInput != null) {
                transport.getFileMeta(token, fileIdInput)
                    ?: return Result.failure(IllegalStateException("Drive ledger file was not found or was moved to trash."))
            } else {
                val byProp = transport.listFiles(
                    accessToken = token,
                    query = "appProperties has { key='$PROP_INVITE_CODE' and value='$cleanCode' } and trashed = false",
                    spaces = "drive"
                ).firstOrNull()
                if (byProp != null) {
                    byProp
                } else {
                    val candidates = transport.listFiles(
                        accessToken = token,
                        query = "mimeType = 'application/json' and trashed = false",
                        spaces = "drive"
                    )
                    candidates.firstOrNull { meta ->
                        if (meta.appProperties[PROP_INVITE_CODE].equals(cleanCode, ignoreCase = true)) {
                            true
                        } else {
                            val raw = runCatching { transport.downloadFile(token, meta.id) }.getOrNull()
                            val parsed = raw?.let { DriveJsonCodec.decodeLedgerDocument(it) }
                            parsed?.workspace?.inviteCode.equals(cleanCode, ignoreCase = true)
                        }
                    } ?: return Result.failure(
                        IllegalStateException("No accessible Google Drive ledger matched '$cleanCode'. Share the Drive JSON file from the creator's Settings or paste its Drive File ID.")
                    )
                }
            }

            val rawJson = transport.downloadFile(token, matchedFileMeta.id)
            val doc = DriveJsonCodec.decodeLedgerDocument(rawJson)
                ?: return Result.failure(IllegalStateException("The selected Drive file is not a valid KinLedger JSON file."))

            val now = System.currentTimeMillis()
            val updatedMembers = (doc.workspace.memberUids + user.uid).distinct()
            val joinedDoc = doc.copy(
                workspace = doc.workspace.copy(
                    memberUids = updatedMembers,
                    driveFileId = matchedFileMeta.id,
                    driveFileName = matchedFileMeta.name.ifBlank { doc.workspace.driveFileName },
                    updatedAtMillis = now
                ),
                updatedAtMillis = now
            )
            val ref = DriveWorkspaceRef(
                workspaceId = joinedDoc.workspace.id,
                name = joinedDoc.workspace.name,
                inviteCode = joinedDoc.workspace.inviteCode,
                currencyCode = joinedDoc.workspace.currencyCode,
                driveFileId = matchedFileMeta.id,
                driveFileName = joinedDoc.workspace.driveFileName,
                updatedAtMillis = now
            )

            updateCache { env ->
                env.copy(
                    userIndex = env.userIndex.copy(
                        userProfile = env.userIndex.userProfile.copy(
                            activeWorkspaceId = joinedDoc.workspace.id,
                            updatedAtMillis = now
                        ),
                        workspaceRefs = env.userIndex.workspaceRefs.filterNot { it.workspaceId == joinedDoc.workspace.id } + ref,
                        updatedAtMillis = now
                    ),
                    ledgersByWorkspaceId = env.ledgersByWorkspaceId + (joinedDoc.workspace.id to joinedDoc),
                    dirtyWorkspaceIds = env.dirtyWorkspaceIds + joinedDoc.workspace.id,
                    dirtyProfile = true
                )
            }
            syncWithDrive(forcePull = false)
            Result.success(joinedDoc.workspace.id)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveWorkspace(): Result<Unit> {
        val now = System.currentTimeMillis()
        updateCache { env ->
            env.copy(
                userIndex = env.userIndex.copy(
                    userProfile = env.userIndex.userProfile.copy(
                        activeWorkspaceId = null,
                        updatedAtMillis = now
                    ),
                    updatedAtMillis = now
                ),
                dirtyProfile = true
            )
        }
        triggerBackgroundSync()
        return Result.success(Unit)
    }

    override suspend fun reconcileWorkspaceMembership(workspace: FamilyWorkspaceModel): Result<Unit> =
        Result.success(Unit)

    private suspend fun mutateWorkspaceDocument(
        workspaceId: String,
        mutation: (DriveLedgerDocument, Long) -> DriveLedgerDocument
    ): Result<Unit> {
        if (currentUserInfo == null) return Result.failure(IllegalStateException("Not signed in with Google."))
        var found = false
        updateCache { env ->
            val existing = env.ledgersByWorkspaceId[workspaceId] ?: return@updateCache env
            found = true
            val now = nextTimestamp(existing.updatedAtMillis)
            val updated = mutation(existing, now).copy(updatedAtMillis = now)
            val updatedRef = DriveWorkspaceRef(
                workspaceId = updated.workspace.id,
                name = updated.workspace.name,
                inviteCode = updated.workspace.inviteCode,
                currencyCode = updated.workspace.currencyCode,
                driveFileId = updated.workspace.driveFileId,
                driveFileName = updated.workspace.driveFileName,
                updatedAtMillis = now
            )
            env.copy(
                ledgersByWorkspaceId = env.ledgersByWorkspaceId + (workspaceId to updated),
                dirtyWorkspaceIds = env.dirtyWorkspaceIds + workspaceId,
                userIndex = env.userIndex.copy(
                    workspaceRefs = env.userIndex.workspaceRefs.filterNot { it.workspaceId == workspaceId } + updatedRef,
                    updatedAtMillis = now
                )
            )
        }
        if (!found) return Result.failure(IllegalStateException("Ledger workspace not loaded."))
        syncStatusFlow.value = DriveSyncStatus.PendingLocal("Saving to Google Drive...")
        triggerBackgroundSync()
        return Result.success(Unit)
    }

    override suspend fun updateWorkspaceSettings(
        workspaceId: String,
        name: String?,
        currencyCode: String?
    ): Result<Unit> = mutateWorkspaceDocument(workspaceId) { doc, now ->
        val ws = doc.workspace
        doc.copy(
            workspace = ws.copy(
                name = name?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: ws.name,
                currencyCode = currencyCode?.trim()?.takeIf { it.isNotEmpty() }?.take(6) ?: ws.currencyCode,
                updatedAtMillis = now
            )
        )
    }

    override suspend fun saveAccount(
        workspace: FamilyWorkspaceModel,
        id: String,
        name: String,
        initialBalanceMinor: Long,
        iconKey: String,
        colorArgb: Long
    ): Result<Unit> {
        val cleanName = name.trim().take(50)
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Account name required"))
        return mutateWorkspaceDocument(workspace.id) { doc, now ->
            val nextAccounts = if (id.isBlank()) {
                val created = AccountModel(
                    id = "acct_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = workspace.id,
                    name = cleanName,
                    initialBalanceMinor = initialBalanceMinor,
                    cachedBalanceMinor = initialBalanceMinor,
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    defaultAccount = false,
                    memberUids = doc.workspace.memberUids,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                doc.accounts + created
            } else {
                doc.accounts.map { acct ->
                    if (acct.id == id) {
                        acct.copy(
                            name = cleanName,
                            initialBalanceMinor = initialBalanceMinor,
                            iconKey = iconKey,
                            colorArgb = colorArgb,
                            updatedAtMillis = now
                        )
                    } else acct
                }
            }
            doc.copy(accounts = nextAccounts)
        }
    }

    override suspend fun deleteAccount(
        workspaceId: String,
        account: AccountModel,
        totalAccounts: Int
    ): Result<Unit> {
        if (totalAccounts <= 1) {
            return Result.failure(IllegalStateException("Keep at least one active account."))
        }
        return mutateWorkspaceDocument(workspaceId) { doc, _ ->
            doc.copy(accounts = doc.accounts.filterNot { it.id == account.id })
        }
    }

    override suspend fun saveCategory(
        workspace: FamilyWorkspaceModel,
        id: String,
        name: String,
        type: TransactionType,
        iconKey: String,
        colorArgb: Long
    ): Result<Unit> {
        val cleanName = name.trim().take(50)
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Category name required"))
        val safeType = if (type == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
        return mutateWorkspaceDocument(workspace.id) { doc, now ->
            val nextCats = if (id.isBlank()) {
                val created = CategoryModel(
                    id = "cat_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = workspace.id,
                    name = cleanName,
                    type = safeType.name,
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    primaryCategory = false,
                    archived = false,
                    sortOrder = 50,
                    memberUids = doc.workspace.memberUids,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                doc.categories + created
            } else {
                doc.categories.map { cat ->
                    if (cat.id == id) {
                        cat.copy(
                            name = cleanName,
                            iconKey = iconKey,
                            colorArgb = colorArgb,
                            updatedAtMillis = now
                        )
                    } else cat
                }
            }
            doc.copy(categories = nextCats)
        }
    }

    override suspend fun toggleArchiveCategory(workspaceId: String, category: CategoryModel): Result<Unit> =
        mutateWorkspaceDocument(workspaceId) { doc, now ->
            doc.copy(
                categories = doc.categories.map {
                    if (it.id == category.id) it.copy(archived = !it.archived, updatedAtMillis = now) else it
                }
            )
        }

    override suspend fun deleteCategory(workspaceId: String, category: CategoryModel): Result<Unit> {
        val existingDoc = envelopeFlow.value.ledgersByWorkspaceId[workspaceId]
        if (existingDoc != null && existingDoc.transactions.any { it.categoryId == category.id }) {
            return Result.failure(
                IllegalStateException("This category has transactions. Archive it instead so history stays intact.")
            )
        }
        return mutateWorkspaceDocument(workspaceId) { doc, _ ->
            doc.copy(categories = doc.categories.filterNot { it.id == category.id })
        }
    }

    override suspend fun saveTransaction(
        workspace: FamilyWorkspaceModel,
        id: String,
        previousTransaction: TransactionModel?,
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        destinationAccountId: String?,
        categoryId: String?,
        dateMillis: Long,
        note: String
    ): Result<Unit> {
        if (amountMinor <= 0L) return Result.failure(IllegalArgumentException("Amount must be greater than 0."))
        if (type == TransactionType.TRANSFER) {
            if (destinationAccountId.isNullOrBlank() || destinationAccountId == accountId) {
                return Result.failure(IllegalArgumentException("Select two different accounts for transfer."))
            }
        } else if (categoryId.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Please select a category."))
        }
        val user = currentUserInfo ?: return Result.failure(IllegalStateException("Not signed in."))
        val cleanNote = note.trim().take(200)

        return mutateWorkspaceDocument(workspace.id) { doc, now ->
            val nextTxs = if (id.isBlank()) {
                val created = TransactionModel(
                    id = "tx_${UUID.randomUUID().toString().take(10)}",
                    workspaceId = workspace.id,
                    type = type.name,
                    amountMinor = amountMinor,
                    accountId = accountId,
                    destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
                    categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                    dateMillis = dateMillis,
                    note = cleanNote,
                    createdByUid = user.uid,
                    createdByName = user.displayName.take(80),
                    createdByEmail = user.email.take(160),
                    memberUids = doc.workspace.memberUids,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                doc.transactions + created
            } else {
                doc.transactions.map { tx ->
                    if (tx.id == id) {
                        tx.copy(
                            type = type.name,
                            amountMinor = amountMinor,
                            accountId = accountId,
                            destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
                            categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                            dateMillis = dateMillis,
                            note = cleanNote,
                            updatedAtMillis = now
                        )
                    } else tx
                }
            }
            doc.copy(transactions = nextTxs)
        }
    }

    override suspend fun deleteTransaction(workspaceId: String, transaction: TransactionModel): Result<Unit> =
        mutateWorkspaceDocument(workspaceId) { doc, _ ->
            doc.copy(transactions = doc.transactions.filterNot { it.id == transaction.id })
        }

    override suspend fun saveDebtRecord(
        workspace: FamilyWorkspaceModel,
        id: String,
        previousDebt: DebtRecordModel?,
        type: DebtType,
        personName: String,
        amountMinor: Long,
        sourceAccountId: String,
        dateMillis: Long,
        dueDateMillis: Long?,
        note: String
    ): Result<Unit> {
        val cleanPerson = personName.trim().take(60)
        if (cleanPerson.isEmpty()) return Result.failure(IllegalArgumentException("Enter person name"))
        if (amountMinor <= 0L) return Result.failure(IllegalArgumentException("Amount must be > 0"))
        val alreadySettled = previousDebt?.effectiveSettledAmountMinor ?: 0L
        if (previousDebt != null && amountMinor < alreadySettled) {
            return Result.failure(IllegalArgumentException("Principal cannot be less than already settled amount."))
        }
        val user = currentUserInfo ?: return Result.failure(IllegalStateException("Not signed in."))
        val cleanNote = note.trim().take(200)

        return mutateWorkspaceDocument(workspace.id) { doc, now ->
            val nextDebts = if (id.isBlank()) {
                val created = DebtRecordModel(
                    id = "debt_${UUID.randomUUID().toString().take(10)}",
                    workspaceId = workspace.id,
                    type = type.name,
                    personName = cleanPerson,
                    amountMinor = amountMinor,
                    settledAmountMinor = 0L,
                    remainingAmountMinor = amountMinor,
                    sourceAccountId = sourceAccountId,
                    dateMillis = dateMillis,
                    dueDateMillis = dueDateMillis?.takeIf { it > 0L },
                    note = cleanNote,
                    settled = false,
                    settlements = emptyList(),
                    createdByUid = user.uid,
                    createdByName = user.displayName.take(80),
                    memberUids = doc.workspace.memberUids,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                doc.debts + created
            } else {
                val newRemaining = (amountMinor - alreadySettled).coerceAtLeast(0L)
                val nowSettled = newRemaining == 0L
                doc.debts.map { d ->
                    if (d.id == id) {
                        val lastSettlement = d.settlements.maxByOrNull { it.dateMillis }
                        d.copy(
                            type = type.name,
                            personName = cleanPerson,
                            amountMinor = amountMinor,
                            remainingAmountMinor = newRemaining,
                            settled = nowSettled,
                            sourceAccountId = sourceAccountId,
                            dateMillis = dateMillis,
                            dueDateMillis = dueDateMillis?.takeIf { it > 0L },
                            note = cleanNote,
                            settledDateMillis = if (nowSettled) {
                                d.settledDateMillis ?: lastSettlement?.dateMillis ?: now
                            } else null,
                            settlementAccountId = if (nowSettled) {
                                d.settlementAccountId ?: lastSettlement?.accountId ?: sourceAccountId
                            } else null,
                            updatedAtMillis = now
                        )
                    } else d
                }
            }
            doc.copy(debts = nextDebts)
        }
    }

    override suspend fun recordDebtSettlement(
        workspaceId: String,
        debt: DebtRecordModel,
        amountMinor: Long,
        settlementAccountId: String,
        settledDateMillis: Long,
        note: String
    ): Result<Boolean> {
        val remainingBefore = debt.effectiveRemainingAmountMinor
        if (amountMinor <= 0L) {
            return Result.failure(IllegalArgumentException("Settlement amount must be greater than 0."))
        }
        if (amountMinor > remainingBefore) {
            return Result.failure(IllegalArgumentException("Amount cannot exceed remaining balance."))
        }
        if (settlementAccountId.isBlank()) {
            return Result.failure(IllegalArgumentException("Please select an account."))
        }
        val user = currentUserInfo ?: return Result.failure(IllegalStateException("Not signed in."))

        val newSettledTotal = debt.effectiveSettledAmountMinor + amountMinor
        val newRemaining = (debt.amountMinor - newSettledTotal).coerceAtLeast(0L)
        val isFullySettled = newRemaining == 0L

        val newEntry = DebtSettlementEntry(
            id = "settle_${UUID.randomUUID().toString().take(8)}",
            amountMinor = amountMinor,
            dateMillis = settledDateMillis,
            accountId = settlementAccountId,
            note = note.trim().take(200),
            settledByUid = user.uid,
            settledByName = user.displayName.take(80)
        )

        val result = mutateWorkspaceDocument(workspaceId) { doc, now ->
            doc.copy(
                debts = doc.debts.map { existing ->
                    if (existing.id == debt.id) {
                        existing.copy(
                            settled = isFullySettled,
                            settledAmountMinor = newSettledTotal,
                            remainingAmountMinor = newRemaining,
                            settlements = existing.settlements + newEntry,
                            settledDateMillis = if (isFullySettled) settledDateMillis else existing.settledDateMillis,
                            settlementAccountId = if (isFullySettled) settlementAccountId else existing.settlementAccountId,
                            updatedAtMillis = now
                        )
                    } else existing
                }
            )
        }
        return result.map { isFullySettled }
    }

    override suspend fun reopenDebtRecord(workspaceId: String, debt: DebtRecordModel): Result<Unit> =
        mutateWorkspaceDocument(workspaceId) { doc, now ->
            doc.copy(
                debts = doc.debts.map { existing ->
                    if (existing.id == debt.id) {
                        existing.copy(
                            settled = false,
                            settledAmountMinor = 0L,
                            remainingAmountMinor = existing.amountMinor,
                            settlements = emptyList(),
                            settledDateMillis = null,
                            settlementAccountId = null,
                            updatedAtMillis = now
                        )
                    } else existing
                }
            )
        }

    override suspend fun deleteDebtRecord(workspaceId: String, debt: DebtRecordModel): Result<Unit> =
        mutateWorkspaceDocument(workspaceId) { doc, _ ->
            doc.copy(debts = doc.debts.filterNot { it.id == debt.id })
        }
}
