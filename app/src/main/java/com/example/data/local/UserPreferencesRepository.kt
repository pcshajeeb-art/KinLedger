package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CurrencyOption
import com.example.data.model.GoogleAccountUser
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kinledger_prefs")

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val LEDGER_NAME = stringPreferencesKey("ledger_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GOOGLE_UID = stringPreferencesKey("google_uid")
        val GOOGLE_DISPLAY_NAME = stringPreferencesKey("google_display_name")
        val GOOGLE_EMAIL = stringPreferencesKey("google_email")
        val GOOGLE_AVATAR_URL = stringPreferencesKey("google_avatar_url")
    }

    val currencyFlow: Flow<CurrencyOption> = context.dataStore.data.map { prefs ->
        CurrencyOption.fromCode(prefs[Keys.CURRENCY_CODE] ?: "INR")
    }

    val ledgerNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LEDGER_NAME] ?: "Family Ledger"
    }

    val themePreferenceFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemePreference.DARK.name
        runCatching { ThemePreference.valueOf(raw) }.getOrDefault(ThemePreference.DARK)
    }

    val signedInGoogleUserFlow: Flow<GoogleAccountUser?> = context.dataStore.data.map { prefs ->
        val uid = prefs[Keys.GOOGLE_UID]?.takeIf { it.isNotBlank() } ?: return@map null
        GoogleAccountUser(
            uid = uid,
            displayName = prefs[Keys.GOOGLE_DISPLAY_NAME].orEmpty(),
            email = prefs[Keys.GOOGLE_EMAIL].orEmpty(),
            avatarUrl = prefs[Keys.GOOGLE_AVATAR_URL]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun getSignedInGoogleUser(): GoogleAccountUser? =
        signedInGoogleUserFlow.first()

    suspend fun saveSignedInGoogleUser(user: GoogleAccountUser) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GOOGLE_UID] = user.uid
            prefs[Keys.GOOGLE_DISPLAY_NAME] = user.displayName
            prefs[Keys.GOOGLE_EMAIL] = user.email
            if (!user.avatarUrl.isNullOrBlank()) {
                prefs[Keys.GOOGLE_AVATAR_URL] = user.avatarUrl
            } else {
                prefs.remove(Keys.GOOGLE_AVATAR_URL)
            }
        }
    }

    suspend fun clearSignedInGoogleUser() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.GOOGLE_UID)
            prefs.remove(Keys.GOOGLE_DISPLAY_NAME)
            prefs.remove(Keys.GOOGLE_EMAIL)
            prefs.remove(Keys.GOOGLE_AVATAR_URL)
        }
    }

    suspend fun setCurrency(code: String) {
        context.dataStore.edit { it[Keys.CURRENCY_CODE] = code }
    }

    suspend fun setLedgerName(name: String) {
        val trimmed = name.trim().ifEmpty { "Family Ledger" }
        context.dataStore.edit { it[Keys.LEDGER_NAME] = trimmed }
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_MODE] = preference.name }
    }
}
