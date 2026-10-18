package io.geoshift.app.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Pure JVM credential storage protocol. Android Keystore cryptography and
 * SharedPreferences I/O are provided by independent adapters.
 */
data class ProviderCredentials(
    val openCellIdApiKey: String = "",
    val wigleTokenName: String = "",
    val wigleToken: String = "",
) {
    fun isEmpty(): Boolean = openCellIdApiKey.isBlank() &&
        wigleTokenName.isBlank() && wigleToken.isBlank()

    fun normalized(): ProviderCredentials = copy(
        openCellIdApiKey = openCellIdApiKey.trim(),
        wigleTokenName = wigleTokenName.trim(),
        wigleToken = wigleToken.trim(),
    )
}

object ProviderCredentialCodec {
    private const val VERSION = 1
    private const val MAX_FIELD_LENGTH = 4_096

    fun encode(values: ProviderCredentials): ByteArray {
        val normalized = values.normalized()
        require(listOf(normalized.openCellIdApiKey, normalized.wigleTokenName, normalized.wigleToken)
            .all { it.length <= MAX_FIELD_LENGTH }) { "Credential field too long" }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { writer ->
            writer.writeInt(VERSION)
            writer.writeUTF(normalized.openCellIdApiKey)
            writer.writeUTF(normalized.wigleTokenName)
            writer.writeUTF(normalized.wigleToken)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): ProviderCredentials = DataInputStream(ByteArrayInputStream(bytes)).use {
        require(it.readInt() == VERSION) { "Unsupported credential version" }
        val values = ProviderCredentials(it.readUTF(), it.readUTF(), it.readUTF())
        require(values.openCellIdApiKey.length <= MAX_FIELD_LENGTH &&
            values.wigleTokenName.length <= MAX_FIELD_LENGTH &&
            values.wigleToken.length <= MAX_FIELD_LENGTH) { "Invalid credential length" }
        require(it.available() == 0) { "Unexpected credential payload suffix" }
        values
    }
}

interface ProviderCredentialPersistence {
    fun encrypted(): String?
    fun legacy(): ProviderCredentials
    fun hasLegacyKeys(): Boolean
    /** Atomic commit must write encrypted blob AND remove all legacy plaintext fields. */
    fun commitEncryptedAndClearLegacy(blob: String): Boolean
    fun clearAll(): Boolean
}

interface ProviderCredentialCipher {
    fun encrypt(plaintext: ByteArray): String
    fun decrypt(blob: String): ByteArray
}

enum class ProviderCredentialStatus {
    EMPTY, READY, MIGRATION_FAILED, RECOVERY_REQUIRED, CLEANUP_FAILED,
}

data class CredentialRead(
    val credentials: ProviderCredentials = ProviderCredentials(),
    val status: ProviderCredentialStatus,
)

enum class CredentialWrite { SAVED, FAILED, RECOVERY_REQUIRED }

class ProviderCredentialEngine(
    private val storage: ProviderCredentialPersistence,
    private val cipher: ProviderCredentialCipher,
) {
    fun read(): CredentialRead {
        val encrypted = storage.encrypted()
        if (encrypted != null) {
            val decrypted = runCatching {
                val bytes = cipher.decrypt(encrypted)
                try { ProviderCredentialCodec.decode(bytes) } finally { bytes.fill(0) }
            }.getOrNull() ?: return CredentialRead(status = ProviderCredentialStatus.RECOVERY_REQUIRED)
            // If an earlier cleanup failed, never use plaintext; retry removing it.
            if (storage.hasLegacyKeys() && !storage.commitEncryptedAndClearLegacy(encrypted)) {
                return CredentialRead(decrypted, ProviderCredentialStatus.CLEANUP_FAILED)
            }
            return CredentialRead(decrypted, ProviderCredentialStatus.READY)
        }

        val legacy = storage.legacy()
        if (legacy.isEmpty()) {
            // Also delete legacy keys with empty strings if they still exist.
            if (storage.hasLegacyKeys() && !storage.clearAll()) {
                return CredentialRead(status = ProviderCredentialStatus.MIGRATION_FAILED)
            }
            return CredentialRead(status = ProviderCredentialStatus.EMPTY)
        }

        val sealed = runCatching { seal(legacy) }.getOrNull()
            ?: return CredentialRead(status = ProviderCredentialStatus.MIGRATION_FAILED)
        if (!storage.commitEncryptedAndClearLegacy(sealed)) {
            return CredentialRead(status = ProviderCredentialStatus.MIGRATION_FAILED)
        }
        return CredentialRead(legacy.normalized(), ProviderCredentialStatus.READY)
    }

    fun save(credentials: ProviderCredentials): CredentialWrite {
        // Corrupt/undecryptable ciphertext must not be silently replaced.
        if (storage.encrypted() != null &&
            read().status == ProviderCredentialStatus.RECOVERY_REQUIRED
        ) return CredentialWrite.RECOVERY_REQUIRED
        val sealed = runCatching { seal(credentials) }.getOrNull() ?: return CredentialWrite.FAILED
        return if (storage.commitEncryptedAndClearLegacy(sealed)) CredentialWrite.SAVED
            else CredentialWrite.FAILED
    }

    fun reset(): Boolean = storage.clearAll()

    private fun seal(credentials: ProviderCredentials): String {
        val bytes = ProviderCredentialCodec.encode(credentials)
        return try { cipher.encrypt(bytes) } finally { bytes.fill(0) }
    }
}
