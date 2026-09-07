package io.geoshift.app.core

import android.content.Context

object ProviderSettings {
    private const val PREFS = "provider_settings"
    private const val KEY_OPENCELLID = "opencellid_api_key"
    private const val KEY_WIGLE_NAME = "wigle_token_name"
    private const val KEY_WIGLE_TOKEN = "wigle_token"

    data class Snapshot(
        val openCellIdApiKey: String = "",
        val wigleTokenName: String = "",
        val wigleToken: String = "",
    )

    fun load(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            openCellIdApiKey = prefs.getString(KEY_OPENCELLID, "").orEmpty(),
            wigleTokenName = prefs.getString(KEY_WIGLE_NAME, "").orEmpty(),
            wigleToken = prefs.getString(KEY_WIGLE_TOKEN, "").orEmpty(),
        )
    }

    fun save(context: Context, snapshot: Snapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OPENCELLID, snapshot.openCellIdApiKey.trim())
            .putString(KEY_WIGLE_NAME, snapshot.wigleTokenName.trim())
            .putString(KEY_WIGLE_TOKEN, snapshot.wigleToken.trim())
            .apply()
    }
}
