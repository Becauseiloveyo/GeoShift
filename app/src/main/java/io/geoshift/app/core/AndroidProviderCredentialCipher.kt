package io.geoshift.app.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AndroidKeyStore AES-256-GCM with a fresh platform-generated 96-bit nonce for
 * every encryption and GCM authentication. Ciphertext is never logged/exported.
 */
internal class AndroidProviderCredentialCipher : ProviderCredentialCipher {
    private val alias = "io.geoshift.app.provider_secret_v1"
    private val keyStoreName = "AndroidKeyStore"
    private val aad = "GeoShift/provider-credentials/v1".toByteArray(Charsets.UTF_8)

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(keyStoreName).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreName).apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    override fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad)
        val nonce = cipher.iv
        check(nonce.size == 12) { "Invalid AES-GCM nonce" }
        val encrypted = cipher.doFinal(plaintext)
        return Base64.getEncoder().encodeToString(nonce + encrypted)
    }

    override fun decrypt(blob: String): ByteArray {
        val input = Base64.getDecoder().decode(blob)
        require(input.size in 28..32_768) { "Invalid sealed credential blob" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = keyStore().getKey(alias, null) as? SecretKey
            ?: error("Keystore key unavailable")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, input.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return cipher.doFinal(input.copyOfRange(12, input.size))
    }

    fun clearKey() {
        val store = keyStore()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }
}
