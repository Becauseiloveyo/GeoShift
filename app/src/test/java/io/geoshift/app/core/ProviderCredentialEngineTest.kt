package io.geoshift.app.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ProviderCredentialEngineTest {
    private class FakeStorage(
        var blob: String? = null,
        var old: ProviderCredentials = ProviderCredentials(),
        var legacyKeys: Boolean = false,
        var permitWrites: Boolean = true,
    ) : ProviderCredentialPersistence {
        override fun encrypted() = blob
        override fun legacy() = old
        override fun hasLegacyKeys() = legacyKeys
        override fun commitEncryptedAndClearLegacy(blob: String): Boolean {
            if (!permitWrites) return false
            this.blob = blob
            legacyKeys = false
            old = ProviderCredentials()
            return true
        }
        override fun clearAll(): Boolean {
            if (!permitWrites) return false
            blob = null
            legacyKeys = false
            old = ProviderCredentials()
            return true
        }
    }
    private class FakeCipher : ProviderCredentialCipher {
        var unavailable = false
        override fun encrypt(plaintext: ByteArray): String {
            if (unavailable) error("unavailable")
            return Base64.getEncoder().encodeToString(plaintext)
        }
        override fun decrypt(blob: String): ByteArray {
            if (unavailable) error("key invalid")
            return Base64.getDecoder().decode(blob)
        }
    }

    private val old = ProviderCredentials("key-123", "username", "token-456")

    @Test fun codecRoundTripAndRejectsTrailingGarbage() {
        val bytes = ProviderCredentialCodec.encode(old)
        assertEquals(old, ProviderCredentialCodec.decode(bytes))
        assertThrows(IllegalArgumentException::class.java) {
            ProviderCredentialCodec.decode(bytes + byteArrayOf(0x2a))
        }
    }

    @Test fun migrateOnlyAfterDurableEncryptedCommit() {
        val storage = FakeStorage(old = old, legacyKeys = true)
        val engine = ProviderCredentialEngine(storage, FakeCipher())
        assertEquals(ProviderCredentialStatus.READY, engine.read().status)
        assertEquals(old, engine.read().credentials)
        assertFalse(storage.hasLegacyKeys())
        assertNotNull(storage.blob)
    }

    @Test fun failedMigrationDoesNotLoseOldCredentialsOrClaimReady() {
        val storage = FakeStorage(old = old, legacyKeys = true, permitWrites = false)
        val result = ProviderCredentialEngine(storage, FakeCipher()).read()
        assertEquals(ProviderCredentialStatus.MIGRATION_FAILED, result.status)
        assertTrue(result.credentials.isEmpty())
        assertEquals(old, storage.old)
        assertNull(storage.blob)
    }

    @Test fun corruptedCiphertextBlocksPlaintextFallbackAndOverwrites() {
        val storage = FakeStorage(blob = "invalid$", old = old, legacyKeys = true)
        val engine = ProviderCredentialEngine(storage, FakeCipher())
        assertEquals(ProviderCredentialStatus.RECOVERY_REQUIRED, engine.read().status)
        assertTrue(engine.read().credentials.isEmpty())
        assertEquals(CredentialWrite.RECOVERY_REQUIRED, engine.save(old))
        assertEquals("invalid$", storage.blob)
    }

    @Test fun invalidatedKeyRequiresExplicitReset() {
        val crypto = FakeCipher()
        val storage = FakeStorage()
        val engine = ProviderCredentialEngine(storage, crypto)
        assertEquals(CredentialWrite.SAVED, engine.save(old))
        crypto.unavailable = true
        assertEquals(ProviderCredentialStatus.RECOVERY_REQUIRED, engine.read().status)
        assertEquals(CredentialWrite.RECOVERY_REQUIRED, engine.save(ProviderCredentials()))
        assertTrue(engine.reset())
        assertEquals(ProviderCredentialStatus.EMPTY, engine.read().status)
    }

    @Test fun failedCleanupDoesNotExposeOldPlaintextInstead() {
        val crypto = FakeCipher()
        val storage = FakeStorage(old = old, legacyKeys = true)
        val sealed = crypto.encrypt(ProviderCredentialCodec.encode(old))
        storage.blob = sealed
        storage.permitWrites = false
        val read = ProviderCredentialEngine(storage, crypto).read()
        assertEquals(ProviderCredentialStatus.CLEANUP_FAILED, read.status)
        assertTrue(read.credentials.isEmpty())
        assertTrue(storage.legacyKeys)
    }

    @Test fun saveClearsAnyResidualLegacyCredentialsAtomically() {
        val storage = FakeStorage(old = old, legacyKeys = true)
        assertEquals(CredentialWrite.SAVED, ProviderCredentialEngine(storage, FakeCipher()).save(
            ProviderCredentials("new-key", "", ""),
        ))
        assertFalse(storage.legacyKeys)
        assertEquals("new-key", ProviderCredentialEngine(storage, FakeCipher()).read().credentials.openCellIdApiKey)
    }
}
