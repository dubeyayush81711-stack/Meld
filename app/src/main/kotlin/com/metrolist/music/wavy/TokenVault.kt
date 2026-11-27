package com.metrolist.music.wavy

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the saved login tokens with a key that lives in the Android Keystore, so the file on disk is not readable text. */
object TokenVault {
    private const val ALIAS = "wavy_session_key"
    private fun key(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    } catch (e: Throwable) { null }

    fun enc(s: String): String {
        if (s.isEmpty()) return s
        val k = key() ?: return s
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, k)
            "enc:" + Base64.encodeToString(c.iv + c.doFinal(s.toByteArray()), Base64.NO_WRAP)
        } catch (e: Throwable) { s }
    }

    /** Plain (old) values pass through unchanged. If decryption fails the user is simply signed out. */
    fun dec(s: String): String {
        if (!s.startsWith("enc:")) return s
        val k = key() ?: return ""
        return try {
            val raw = Base64.decode(s.removePrefix("enc:"), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (e: Throwable) { "" }
    }
}
