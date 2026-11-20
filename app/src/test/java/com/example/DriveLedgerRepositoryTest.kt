package com.example

import android.app.Application
import com.example.auth.DriveTokenProvider
import com.example.auth.DriveTokenResult
import com.example.data.model.DateFormatters
import com.example.data.model.DebtType
import com.example.data.model.DriveSyncStatus
import com.example.data.model.LedgerUserInfo
import com.example.data.model.TransactionType
import com.example.data.repository.DriveFileMeta
import com.example.data.repository.DriveJsonCodec
import com.example.data.repository.DriveLedgerRepository
import com.example.data.repository.DriveTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class DriveLedgerRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeTokenProvider(
        private val token: String = "fake_drive_access_token"
    ) : DriveTokenProvider {
        override suspend fun getDriveAccessToken(forceRefresh: Boolean): DriveTokenResult =
            DriveTokenResult.Token(token)

        override fun onConsentGranted(accessToken: String) = Unit
        override fun invalidateAccessToken() = Unit
    }

    private class FakeDriveTransport : DriveTransport {
        data class StoredFile(
            val meta: DriveFileMeta,
            val parentId: String,
            val isFolder: Boolean,
            val content: String
        )

        val files = java.util.concurrent.ConcurrentHashMap<String, StoredFile>()

        override suspend fun listFiles(accessToken: String, query: String, spaces: String): List<DriveFileMeta> {
            return files.values.filter { stored ->
                val spaceOk = if (spaces == "appDataFolder") {
                    stored.parentId == "appDataFolder"
                } else {
                    stored.parentId != "appDataFolder"
                }
                if (!spaceOk) return@filter false
                when {
                    query.contains("mimeType = 'application/vnd.google-apps.folder'") ->
                        stored.isFolder && query.contains("'${stored.meta.name}'")
                    query.contains("name = '") -> {
                        val name = query.substringAfter("name = '").substringBefore("'")
                        stored.meta.name == name
                    }
                    query.contains("in parents") -> {
                        val parent = query.substringAfter("'").substringBefore("' in parents")
                        stored.parentId == parent
                    }
                    query.contains("appProperties has") -> {
                        val value = query.substringAfter("value='").substringBefore("'")
                        stored.meta.appProperties.values.any { it.equals(value, ignoreCase = true) }
                    }
                    else -> !stored.isFolder
                }
            }.map { it.meta }
        }

        override suspend fun getFileMeta(accessToken: String, fileId: String): DriveFileMeta? =
            files[fileId]?.meta

        override suspend fun downloadFile(accessToken: String, fileId: String): String =
            files[fileId]?.content ?: error("File not found: $fileId")

        override suspend fun createFolder(accessToken: String, name: String): DriveFileMeta {
            val id = "folder_${UUID.randomUUID().toString().take(8)}"
            val meta = DriveFileMeta(id = id, name = name)
            files[id] = StoredFile(meta = meta, parentId = "root", isFolder = true, content = "")
            return meta
        }

        override suspend fun createJsonFile(
            accessToken: String,
            name: String,
            parentId: String,
            jsonContent: String,
            appProperties: Map<String, String>
        ): DriveFileMeta {
            val id = "file_${UUID.randomUUID().toString().take(8)}"
            val meta = DriveFileMeta(id = id, name = name, appProperties = appProperties)
            files[id] = StoredFile(meta = meta, parentId = parentId, isFolder = false, content = jsonContent)
            return meta
        }

        override suspend fun updateJsonFile(
            accessToken: String,
            fileId: String,
            jsonContent: String
        ): DriveFileMeta {
            val existing = files[fileId] ?: error("Cannot update missing file $fileId")
            files[fileId] = existing.copy(content = jsonContent)
            return existing.meta
        }
    }

    private fun newRepo(
        cacheDir: File,
        transport: FakeDriveTransport
    ): DriveLedgerRepository = DriveLedgerRepository(
        context = Application(),
        tokenProvider = FakeTokenProvider(),
        transport = transport,
        cacheDirProvider = { cacheDir }
    )

    @Test
    fun `creating workspace and saving transaction and partial debt sync JSON to Google Drive`() = runBlocking {
        val transport = FakeDriveTransport()
        val device1Cache = tempFolder.newFolder("device1")
        val repo1 = newRepo(device1Cache, transport)
        val user = LedgerUserInfo(uid = "google_sub_123", displayName = "Asha", email = "asha@example.com")

        assertTrue(repo1.ensureUserProfile(user).isSuccess)
        val wsId = repo1.createFamilyWorkspace("Patel Family", "INR").getOrThrow()

        // Explicit sync pushes workspace JSON to KinLedger Drive folder and profile to appDataFolder
        assertTrue(repo1.syncWithDrive(forcePull = false).isSuccess)
        assertTrue(repo1.observeSyncStatus().first() is DriveSyncStatus.Synced)

        val ws = repo1.observeWorkspace(wsId).first()!!
        val cashAcct = repo1.observeAccountsWithBalance(wsId, user.uid).first().first { it.account.name == "Cash" }.account
        val groceryCat = repo1.observeCategories(wsId, user.uid).first().first { it.name.contains("Grocery") }

        assertTrue(
            repo1.saveTransaction(
                workspace = ws,
                type = TransactionType.EXPENSE,
                amountMinor = 150_000L, // ₹1,500
                accountId = cashAcct.id,
                destinationAccountId = null,
                categoryId = groceryCat.id,
                dateMillis = DateFormatters.startOfTodayMillis(),
                note = "Vegetables"
            ).isSuccess
        )

        assertTrue(
            repo1.saveDebtRecord(
                workspace = ws,
                type = DebtType.PAYABLE,
                personName = "Ramesh",
                amountMinor = 500_000L, // borrowed ₹5,000 into Cash
                sourceAccountId = cashAcct.id,
                dateMillis = DateFormatters.startOfTodayMillis(),
                dueDateMillis = null,
                note = "Plumber advance"
            ).isSuccess
        )

        val debt = repo1.observeDebtRecords(wsId, user.uid).first().first().debt
        assertTrue(
            repo1.recordDebtSettlement(
                workspaceId = wsId,
                debt = debt,
                amountMinor = 200_000L, // repaid ₹2,000 from Cash
                settlementAccountId = cashAcct.id,
                settledDateMillis = DateFormatters.startOfTodayMillis(),
                note = "Part payment"
            ).isSuccess
        )

        assertTrue(repo1.syncWithDrive(forcePull = false).isSuccess)

        // Verify Drive contains KinLedger folder, profile JSON in appDataFolder, and ledger JSON
        val profileFile = transport.files.values.firstOrNull { it.meta.name == DriveLedgerRepository.PROFILE_FILE_NAME }
        assertNotNull(profileFile)
        assertEquals("appDataFolder", profileFile!!.parentId)

        val folder = transport.files.values.firstOrNull { it.isFolder && it.meta.name == DriveLedgerRepository.DRIVE_FOLDER_NAME }
        assertNotNull(folder)

        val ledgerFile = transport.files.values.firstOrNull { !it.isFolder && it.parentId == folder!!.meta.id }
        assertNotNull(ledgerFile)
        val decodedDoc = DriveJsonCodec.decodeLedgerDocument(ledgerFile!!.content)!!
        assertEquals("Patel Family", decodedDoc.workspace.name)
        assertEquals(1, decodedDoc.transactions.size)
        assertEquals(300_000L, decodedDoc.debts.first().effectiveRemainingAmountMinor)

        // Verify fresh Device 2 restores workspace + balances automatically from Google Drive JSON
        val device2Cache = tempFolder.newFolder("device2")
        val repo2 = newRepo(device2Cache, transport)
        assertTrue(repo2.ensureUserProfile(user).isSuccess)

        val restoredProfile = repo2.observeUserProfile(user.uid).first()!!
        assertEquals(wsId, restoredProfile.activeWorkspaceId)

        val restoredCash = repo2.observeAccountsWithBalance(wsId, user.uid).first().first { it.account.name == "Cash" }
        // Cash: -1,500 (expense) + 5,000 (borrowed) - 2,000 (partial repayment) = +1,500 (150,000 minor)
        assertEquals(150_000L, restoredCash.currentBalanceMinor)
    }

    @Test
    fun `joining shared family workspace via invite code or Drive file URL loads shared ledger`() = runBlocking {
        val transport = FakeDriveTransport()
        val ownerCache = tempFolder.newFolder("owner")
        val memberCache = tempFolder.newFolder("member")

        val ownerRepo = newRepo(ownerCache, transport)
        val owner = LedgerUserInfo(uid = "owner_uid", displayName = "Owner", email = "owner@example.com")
        ownerRepo.ensureUserProfile(owner)
        val wsId = ownerRepo.createFamilyWorkspace("Shared Home", "USD").getOrThrow()
        assertTrue(ownerRepo.syncWithDrive(forcePull = false).isSuccess)

        val ws = ownerRepo.observeWorkspace(wsId).first()!!
        val inviteCode = ws.inviteCode
        val fileId = ws.driveFileId
        assertTrue(fileId.isNotBlank())

        // Family member joins using invite code
        val memberRepo = newRepo(memberCache, transport)
        val member = LedgerUserInfo(uid = "member_uid", displayName = "Spouse", email = "spouse@example.com")
        memberRepo.ensureUserProfile(member)

        val joinedWsId = memberRepo.joinFamilyWorkspace(inviteCode).getOrThrow()
        assertEquals(wsId, joinedWsId)
        val joinedWs = memberRepo.observeWorkspace(wsId).first()!!
        assertTrue("member_uid" in joinedWs.memberUids)

        // Also test URL extraction for Drive share links
        val (parsedId, parsedCode) = DriveLedgerRepository.extractDriveFileIdOrCode(
            "https://drive.google.com/file/d/$fileId/view?usp=sharing"
        )
        assertEquals(fileId, parsedId)
        assertEquals(null, parsedCode)
    }
}
