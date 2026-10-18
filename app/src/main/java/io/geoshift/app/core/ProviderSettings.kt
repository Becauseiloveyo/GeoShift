package io.geoshift.app.core

import android.content.Context
import android.content.SharedPreferences

/**
 * At-rest provider credentials: AndroidKeyStore AES-256-GCM only. No plaintext
 * fallback. Credential values never enter Remote Preferences or Profile export.
 */
object ProviderSettings {
    private const val PREFS = "provider_settings"
    private const val KEY_OPENCELLID = "opencellid_api_key"
    private const val KEY_WIGLE_NAME = "wigle_token_name"
    private const val KEY_WIGLE_TOKEN = "wigle_token"
    private const val KEY_ENCRYPTED = "sealed_credentials_v1"

    data class Snapshot(
        val openCellIdApiKey: String = "",
        val wigleTokenName: String = "",
        val wigleToken: String = "",
    )

    data class Result(
        val snapshot: Snapshot = Snapshot(),
        val status: ProviderCredentialStatus,
    )

    private class PreferencesStorage(
        private val prefs: SharedPreferences,
    ) : ProviderCredentialPersistence {
        override fun encrypted(): String? = prefs.getString(KEY_ENCRYPTED, null)

        override fun legacy(): ProviderCredentials = ProviderCredentials(
            openCellIdApiKey = prefs.getString(KEY_OPENCELLID, "").orEmpty(),
            wigleTokenName = prefs.getString(KEY_WIGLE_NAME, "").orEmpty(),
            wigleToken = prefs.getString(KEY_WIGLE_TOKEN, "").orEmpty(),
        )

        override fun hasLegacyKeys(): Boolean =
            prefs.contains(KEY_OPENCELLID) ||
                prefs.contains(KEY_WIGLE_NAME) ||
                prefs.contains(KEY_WIGLE_TOKEN)

        override fun commitEncryptedAndClearLegacy(blob: String): Boolean =
            prefs.edit()
                .putString(KEY_ENCRYPTED, blob)
                .remove(KEY_OPENCELLID)
                .remove(KEY_WIGLE_NAME)
                .remove(KEY_WIGLE_TOKEN)
                .commit()

        override fun clearAll(): Boolean =
            prefs.edit()
                .remove(KEY_ENCRYPTED)
                .remove(KEY_OPENCELLID)
                .remove(KEY_WIGLE_NAME)
                .remove(KEY_WIGLE_TOKEN)
                .commit()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun engine(context: Context) = ProviderCredentialEngine(
        PreferencesStorage(prefs(context)),
        AndroidProviderCredentialCipher(),
    )

    @Synchronized
    fun loadWithStatus(context: Context): Result {
        val read = runCatching { engine(context).read() }.getOrNull()
            ?: return Result(status = ProviderCredentialStatus.MIGRATION_FAILED)
        // No credentials are exposed on a failed migration, cleanup or lost Keystore key.
        if (read.status !in setOf(
                ProviderCredentialStatus.EMPTY, ProviderCredentialStatus.READY,
            )
        ) return Result(status = read.status)
        return Result(
            snapshot = Snapshot(
                openCellIdApiKey = read.credentials.openCellIdApiKey,
                wigleTokenName = read.credentials.wigleTokenName,
                wigleToken = read.credentials.wigleToken,
            ),
            status = read.status,
        )
    }

    fun load(context: Context): Snapshot = loadWithStatus(context).snapshot

    @Synchronized
    fun save(context: Context, snapshot: Snapshot): CredentialWrite =
        runCatching {
            engine(context).save(
                ProviderCredentials(
                    openCellIdApiKey = snapshot.openCellIdApiKey,
                    wigleTokenName = snapshot.wigleTokenName,
                    wigleToken = snapshot.wigleToken,
                ),
            )
        }.getOrDefault(CredentialWrite.FAILED)

    /**
     * Destructive and explicit only: caller must show a confirmation. Clears the
     * encrypted record and any obsolete plaintext before removing the Keystore key.
     */
    @Synchronized
    fun resetAfterUserConfirmation(context: Context): Boolean = runCatching {
        val cipher = AndroidProviderCredentialCipher()
        if (!engine(context).reset()) return@runCatching false
        cipher.clearKey()
        true
    }.getOrDefault(false)
}
