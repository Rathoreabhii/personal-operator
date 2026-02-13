package com.evo.operator.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted key-value storage using Android's EncryptedSharedPreferences.
 * All sensitive data (API keys, tokens, server URL) is stored AES-256 encrypted.
 */
class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "operator_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_API_KEY = "api_secret_key"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_PHONE = "user_phone_number"
        private const val KEY_KILL_SWITCH = "kill_switch_active"
        private const val KEY_AUTO_SUGGEST = "auto_suggest_enabled"
        private const val KEY_COMMAND_MODE = "command_mode_enabled"
    }

    // ── API Key ──
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    // ── Server URL ──
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "wss://api.veloe.in/ws") ?: "wss://api.veloe.in/ws"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    // ── User's own phone number (to detect self-messages) ──
    var userPhoneNumber: String
        get() = prefs.getString(KEY_USER_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_PHONE, value).apply()

    // ── Kill Switch ──
    var killSwitchActive: Boolean
        get() = prefs.getBoolean(KEY_KILL_SWITCH, false)
        set(value) = prefs.edit().putBoolean(KEY_KILL_SWITCH, value).apply()

    // ── Auto Suggestion Mode ──
    var autoSuggestEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SUGGEST, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SUGGEST, value).apply()

    // ── Command Mode ──
    var commandModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_COMMAND_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_COMMAND_MODE, value).apply()

    /**
     * Clears all stored data (used during logout / reset).
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Checks if initial setup is complete.
     */
    fun isSetupComplete(): Boolean {
        return apiKey.isNotBlank() && serverUrl.isNotBlank()
    }
}
