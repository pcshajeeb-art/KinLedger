package com.example.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.GoogleAccountUser
import com.example.data.model.LedgerUserInfo
import com.example.data.repository.DemoLedgerRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AppSession {
    data object SignedOut : AppSession

    data class Google(val user: GoogleAccountUser) : AppSession {
        override val uid: String get() = user.uid
        override val sessionKey: String get() = "google_${user.uid}"
        override val isDemoMode: Boolean get() = false
        override val userInfo: LedgerUserInfo
            get() = LedgerUserInfo(
                uid = user.uid,
                displayName = user.displayName.trim().takeIf { it.isNotEmpty() }
                    ?: user.email.trim().takeIf { it.isNotEmpty() }
                    ?: "Family Member",
                email = user.email
            )
    }

    data class LocalDemo(val instanceId: Long) : AppSession {
        override val uid: String get() = DemoLedgerRepository.DEMO_USER_ID
        override val sessionKey: String get() = "demo_$instanceId"
        override val isDemoMode: Boolean get() = true
        override val userInfo: LedgerUserInfo
            get() = LedgerUserInfo(
                uid = DemoLedgerRepository.DEMO_USER_ID,
                displayName = "Guest Preview",
                email = "Local Preview • Not synced"
            )
    }

    val uid: String get() = ""
    val sessionKey: String get() = "signed_out"
    val isDemoMode: Boolean get() = false
    val userInfo: LedgerUserInfo? get() = null
}

sealed interface DriveTokenResult {
    data class Token(val accessToken: String) : DriveTokenResult
    data class NeedsConsent(val pendingIntent: PendingIntent?) : DriveTokenResult
    data class Failure(val error: Throwable) : DriveTokenResult
}

interface DriveTokenProvider {
    suspend fun getDriveAccessToken(forceRefresh: Boolean = false): DriveTokenResult
    fun onConsentGranted(accessToken: String)
    fun invalidateAccessToken()
}

class AuthService(
    private val appContext: Context,
    private val preferences: UserPreferencesRepository
) : DriveTokenProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val googleUserFlow = MutableStateFlow<GoogleAccountUser?>(null)
    private val demoInstanceIdFlow = MutableStateFlow<Long?>(null)
    private val sessionInitializedFlow = MutableStateFlow(false)

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedTokenTimestampMillis: Long = 0L

    init {
        scope.launch {
            val savedUser = runCatching { preferences.getSignedInGoogleUser() }.getOrNull()
            if (savedUser != null && demoInstanceIdFlow.value == null) {
                googleUserFlow.value = savedUser
            }
            sessionInitializedFlow.value = true
        }
    }

    val currentUser: GoogleAccountUser?
        get() = googleUserFlow.value

    val sessionFlow: Flow<AppSession> = combine(
        googleUserFlow,
        demoInstanceIdFlow
    ) { user, demoId ->
        when {
            user != null -> AppSession.Google(user)
            demoId != null -> AppSession.LocalDemo(demoId)
            else -> AppSession.SignedOut
        }
    }

    val currentSession: AppSession
        get() = googleUserFlow.value?.let { AppSession.Google(it) }
            ?: demoInstanceIdFlow.value?.let { AppSession.LocalDemo(it) }
            ?: AppSession.SignedOut

    fun enterDemoMode() {
        cachedAccessToken = null
        cachedTokenTimestampMillis = 0L
        demoInstanceIdFlow.value = System.currentTimeMillis()
    }

    /**
     * Restores persisted Google user or runs silent Google auto sign-in via Credential Manager.
     */
    suspend fun attemptAutoSignIn(context: Context): Result<GoogleAccountUser?> {
        // Wait briefly if persisted session restore is still finishing
        googleUserFlow.value?.let {
            demoInstanceIdFlow.value = null
            return Result.success(it)
        }
        val persisted = runCatching { preferences.getSignedInGoogleUser() }.getOrNull()
        if (persisted != null && demoInstanceIdFlow.value == null) {
            googleUserFlow.value = persisted
            return Result.success(persisted)
        }

        val clientId = runCatching { context.getString(R.string.default_web_client_id) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success(null)

        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(context, request)
            if (demoInstanceIdFlow.value != null) return Result.success(null)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idTokenCred = GoogleIdTokenCredential.createFrom(cred.data)
                val user = mapCredentialToUser(idTokenCred)
                preferences.saveSignedInGoogleUser(user)
                demoInstanceIdFlow.value = null
                googleUserFlow.value = user
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (_: Exception) {
            Result.success(null)
        }
    }

    /**
     * Interactive Sign in with Google via Jetpack Credential Manager.
     */
    suspend fun signInWithGoogle(activity: Activity): Result<GoogleAccountUser> {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return Result.failure(
                IllegalStateException("Google Sign-In configuration missing: default_web_client_id not found")
            )
        }

        return try {
            val credentialManager = CredentialManager.create(activity)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val response = credentialManager.getCredential(activity, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idTokenCred = GoogleIdTokenCredential.createFrom(cred.data)
                val user = mapCredentialToUser(idTokenCred)
                preferences.saveSignedInGoogleUser(user)
                cachedAccessToken = null
                cachedTokenTimestampMillis = 0L
                demoInstanceIdFlow.value = null
                googleUserFlow.value = user
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type returned"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            Result.failure(SignInCancelledException(e.message))
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    /**
     * Requests a Google Drive OAuth access token via AuthorizationClient.
     * If user consent is still required, returns DriveTokenResult.NeedsConsent(pendingIntent).
     */
    override suspend fun getDriveAccessToken(forceRefresh: Boolean): DriveTokenResult {
        val now = System.currentTimeMillis()
        val existing = cachedAccessToken
        if (!forceRefresh && !existing.isNullOrBlank() && (now - cachedTokenTimestampMillis) < TOKEN_CACHE_TTL_MILLIS) {
            return DriveTokenResult.Token(existing)
        }
        return authorizeWithContext(appContext)
    }

    suspend fun requestDriveAccessToken(context: Context, forceRefresh: Boolean = false): DriveTokenResult {
        val now = System.currentTimeMillis()
        val existing = cachedAccessToken
        if (!forceRefresh && !existing.isNullOrBlank() && (now - cachedTokenTimestampMillis) < TOKEN_CACHE_TTL_MILLIS) {
            return DriveTokenResult.Token(existing)
        }
        return authorizeWithContext(context)
    }

    private suspend fun authorizeWithContext(context: Context): DriveTokenResult {
        return try {
            val authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(DRIVE_SCOPES)
                .build()
            val result: AuthorizationResult = Identity.getAuthorizationClient(context)
                .authorize(authorizationRequest)
                .awaitResult()

            if (result.hasResolution()) {
                DriveTokenResult.NeedsConsent(result.pendingIntent)
            } else {
                val token = result.accessToken?.takeIf { it.isNotBlank() }
                if (token != null) {
                    onConsentGranted(token)
                    DriveTokenResult.Token(token)
                } else {
                    DriveTokenResult.Failure(IllegalStateException("Google Drive access token was empty."))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Drive authorization check failed: ${e.message}", e)
            DriveTokenResult.Failure(e)
        }
    }

    fun handleDriveConsentResult(context: Context, data: Intent?): DriveTokenResult {
        if (data == null) {
            return DriveTokenResult.Failure(SignInCancelledException("Google Drive permission was not granted."))
        }
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken?.takeIf { it.isNotBlank() }
            if (token != null) {
                onConsentGranted(token)
                DriveTokenResult.Token(token)
            } else {
                DriveTokenResult.Failure(IllegalStateException("Google Drive access token missing after consent."))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Drive authorization result", e)
            DriveTokenResult.Failure(e)
        }
    }

    override fun onConsentGranted(accessToken: String) {
        cachedAccessToken = accessToken
        cachedTokenTimestampMillis = System.currentTimeMillis()
    }

    override fun invalidateAccessToken() {
        cachedAccessToken = null
        cachedTokenTimestampMillis = 0L
    }

    /**
     * Clears persisted Google session, Credential Manager state, cached tokens, and exits demo mode.
     */
    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            val wasDemoOnly = googleUserFlow.value == null && demoInstanceIdFlow.value != null
            demoInstanceIdFlow.value = null
            cachedAccessToken = null
            cachedTokenTimestampMillis = 0L
            googleUserFlow.value = null
            preferences.clearSignedInGoogleUser()
            if (!wasDemoOnly) {
                try {
                    val credentialManager = CredentialManager.create(context)
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                }
            }
        }
    }

    private fun mapCredentialToUser(credential: GoogleIdTokenCredential): GoogleAccountUser {
        val claims = decodeJwtClaims(credential.idToken)
        val email = claims?.optString("email")?.takeIf { it.isNotBlank() }
            ?: credential.id
        val sub = claims?.optString("sub")?.takeIf { it.isNotBlank() }
            ?: email
        val displayName = credential.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: claims?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: email.substringBefore("@").ifBlank { "Family Member" }
        return GoogleAccountUser(
            uid = sub,
            displayName = displayName,
            email = email,
            avatarUrl = credential.profilePictureUri?.toString()
        )
    }

    private fun decodeJwtClaims(jwt: String): JSONObject? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(decoded, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "AuthService"
        private const val TOKEN_CACHE_TTL_MILLIS = 40 * 60 * 1000L
        val DRIVE_SCOPES: List<Scope> = listOf(
            Scope("https://www.googleapis.com/auth/drive.appdata"),
            Scope("https://www.googleapis.com/auth/drive.file")
        )
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}

class SignInCancelledException(message: String?) : Exception(message)
