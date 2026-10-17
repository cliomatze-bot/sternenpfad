package com.ipa.analyzer.data

import android.content.Context
import com.ipa.analyzer.core.api.BusinessDiscovery

/** Lokale Einstellungen (privater App-Speicher, vom System-Backup ausgeschlossen). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun credentials() = ApiCredentials(
        accessToken = prefs.getString(KEY_TOKEN, "") ?: "",
        igUserId = prefs.getString(KEY_IG_ID, "") ?: "",
        apiVersion = prefs.getString(KEY_VERSION, BusinessDiscovery.DEFAULT_API_VERSION)
            ?: BusinessDiscovery.DEFAULT_API_VERSION,
    )

    fun saveCredentials(c: ApiCredentials) {
        prefs.edit()
            .putString(KEY_TOKEN, c.accessToken.trim())
            .putString(KEY_IG_ID, c.igUserId.trim())
            .putString(KEY_VERSION, c.apiVersion.trim().ifEmpty { BusinessDiscovery.DEFAULT_API_VERSION })
            .apply()
    }

    var activeSnapshotId: String?
        get() = prefs.getString(KEY_ACTIVE, null)
        set(value) { prefs.edit().putString(KEY_ACTIVE, value).apply() }

    fun clearAll() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TOKEN = "graph_token"
        const val KEY_IG_ID = "graph_ig_user_id"
        const val KEY_VERSION = "graph_api_version"
        const val KEY_ACTIVE = "active_snapshot"
    }
}
