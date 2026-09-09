package com.example.receiptapp.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erzeugt/lädt die SQLCipher-Passphrase für die Room-DB.
 *
 * Prinzip: Ein zufälliger 256-Bit-Schlüssel wird einmalig generiert und in
 * EncryptedSharedPreferences abgelegt, deren Master-Key wiederum im
 * Android Keystore liegt (hardwaregestützt, falls verfügbar). So landet die
 * DB-Passphrase nie im Klartext im Code oder unverschlüsselt auf der Platte.
 */
@Singleton
class DbPassphraseProvider @Inject constructor(
    private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "receiptapp_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrCreatePassphrase(): ByteArray {
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) return Base64.getDecoder().decode(existing)

        val newKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_PASSPHRASE, Base64.getEncoder().encodeToString(newKey)).apply()
        return newKey
    }

    companion object {
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
