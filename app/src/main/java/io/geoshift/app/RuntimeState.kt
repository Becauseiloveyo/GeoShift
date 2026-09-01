package io.geoshift.app

import android.content.Context

object RuntimeState {
    private const val PREFS = "geoshift_runtime"
    private const val KEY_FOLLOW_ENABLED = "follow_enabled"

    fun setFollowEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FOLLOW_ENABLED, enabled)
            .apply()
    }

    fun shouldFollow(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FOLLOW_ENABLED, false)
}
