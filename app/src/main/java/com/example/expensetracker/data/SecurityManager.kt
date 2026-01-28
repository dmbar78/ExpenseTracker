package com.example.expensetracker.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityManager {

    private const val PREFS_NAME = "security_prefs"
    private const val KEY_ENC_DEK_PIN = "enc_dek_pin" // DEK encrypted with PIN-derived Key
    private const val KEY_ENC_DEK_BIO = "enc_dek_bio" // DEK encrypted with Biometric-bound Key
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_IV = "pin_iv"
    private const val KEY_BIO_IV = "bio_iv"
    private const val KEY_IS_BIO_ENABLED = "is_bio_enabled"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIO_KEY_ALIAS = "ExpenseTrackerBioKey"

    // PBKDF2 Constants
    private const val PBKDF2_ITERATIONS = 5000 // Balance between security and UI lag
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    // DEK Constants
    private const val DEK_ALGORITHM = "AES"
    private const val DEK_SIZE = 256

    // In-memory Master Key (DEK) - cleared on app kill
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_TIMESTAMP = "lockout_timestamp"
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 30000L // 30 seconds

    // In-memory Master Key (DEK) - cleared on app kill
    private var masterKey: SecretKey? = null

    // --- State Check ---
    fun isPinSet(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_ENC_DEK_PIN)
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_BIO_ENABLED, false)
    }

    fun isLocked(): Boolean {
        return masterKey == null
    }
    
    fun setLocked() {
        masterKey = null
    }

    fun isLockedOut(context: Context): Boolean {
        val prefs = getPrefs(context)
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0)
        return lockoutTime > System.currentTimeMillis()
    }

    fun getRemainingLockoutTime(context: Context): Long {
        val prefs = getPrefs(context)
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0)
        val remaining = lockoutTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    // --- PIN Operations ---

    // 1. Generate new DEK
    // 2. Derive KEK from PIN
    // 3. Encrypt DEK with KEK
    // 4. Save to Prefs
    fun setPin(context: Context, pin: String) {
        val dek = generateDek()
        val salt = generateSalt()
        val kek = deriveKeyFromPin(pin, salt)

        val (encryptedDek, iv) = encrypt(dek.encoded, kek)

        getPrefs(context).edit()
            .putString(KEY_ENC_DEK_PIN, Base64.encodeToString(encryptedDek, Base64.DEFAULT))
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.DEFAULT))
            .putString(KEY_PIN_IV, Base64.encodeToString(iv, Base64.DEFAULT))
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_TIMESTAMP)
            .apply()

        // Cache the key
        masterKey = dek
        
        // Disable BIO on PIN change (must re-enable to re-bind keys)
        disableBiometric(context)
    }
    
    // Change PIN without generating new DEK (re-wrapping)
    fun changePin(context: Context, oldPin: String, newPin: String): Boolean {
        // If locked out, verifyPin will fail/return false.
        // We rely on verifyPin to enforce checks for oldPin.
        if (!verifyPin(context, oldPin)) return false
        
        val currentDek = masterKey ?: return false
        
        val newSalt = generateSalt()
        val newKek = deriveKeyFromPin(newPin, newSalt)
        val (newEncryptedDek, newIv) = encrypt(currentDek.encoded, newKek)
        
        getPrefs(context).edit()
            .putString(KEY_ENC_DEK_PIN, Base64.encodeToString(newEncryptedDek, Base64.DEFAULT))
            .putString(KEY_PIN_SALT, Base64.encodeToString(newSalt, Base64.DEFAULT))
            .putString(KEY_PIN_IV, Base64.encodeToString(newIv, Base64.DEFAULT))
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_TIMESTAMP)
            .apply()
            
        // Disable BIO on PIN change
        disableBiometric(context)
        return true
    }
    
    fun removePin(context: Context) {
        getPrefs(context).edit().clear().apply()
        masterKey = null
        // Keystore key cleanup
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(BIO_KEY_ALIAS)) {
                keyStore.deleteEntry(BIO_KEY_ALIAS)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        // Check Lockout
        if (isLockedOut(context)) return false

        val prefs = getPrefs(context)
        val saltStr = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val ivStr = prefs.getString(KEY_PIN_IV, null) ?: return false
        val encDekStr = prefs.getString(KEY_ENC_DEK_PIN, null) ?: return false

        val salt = Base64.decode(saltStr, Base64.DEFAULT)
        val iv = Base64.decode(ivStr, Base64.DEFAULT)
        val encDek = Base64.decode(encDekStr, Base64.DEFAULT)

        val kek = deriveKeyFromPin(pin, salt)

        return try {
            val dekBytes = decrypt(encDek, kek, iv)
            masterKey = SecretKeySpec(dekBytes, DEK_ALGORITHM)
            
            // Success: Reset attempts
            prefs.edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_LOCKOUT_TIMESTAMP)
                .apply()
            
            true
        } catch (e: Exception) {
            // Failure: Increment attempts
            val failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val editor = prefs.edit()
            editor.putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            
            if (failedAttempts >= MAX_ATTEMPTS) {
                editor.putLong(KEY_LOCKOUT_TIMESTAMP, System.currentTimeMillis() + LOCKOUT_DURATION_MS)
                editor.putInt(KEY_FAILED_ATTEMPTS, 0) // Reset attempts so next try after lockout is fresh
            }
            editor.commit() // Use commit for synchronous safety in critical path
            false
        }
    }

    // --- Biometric Operations ---

    fun enableBiometric(context: Context) {
        val currentDek = masterKey ?: throw IllegalStateException("Must unlock with PIN first")

        // 1. Generate KEK_bio in Keystore (User Auth Required)
        val bioKek = generateBiometricKey()

        // 2. Encrypt DEK with KEK_bio
        val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_GCM + "/"
                + KeyProperties.ENCRYPTION_PADDING_NONE)
        cipher.init(Cipher.ENCRYPT_MODE, bioKek)
        
        val iv = cipher.iv
        val encDek = cipher.doFinal(currentDek.encoded)

        getPrefs(context).edit()
            .putBoolean(KEY_IS_BIO_ENABLED, true)
            .putString(KEY_ENC_DEK_BIO, Base64.encodeToString(encDek, Base64.DEFAULT))
            .putString(KEY_BIO_IV, Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
    }
    
    fun disableBiometric(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_IS_BIO_ENABLED, false)
            .remove(KEY_ENC_DEK_BIO)
            .remove(KEY_BIO_IV)
            .apply()
    }

    // Returns CryptoObject initialized for Decryption
    fun getBiometricCryptoObject(context: Context): BiometricPrompt.CryptoObject? {
        if (!isBiometricEnabled(context)) return null
        
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val bioKek = keyStore.getKey(BIO_KEY_ALIAS, null) as? SecretKey ?: return null
            
            val prefs = getPrefs(context)
            val ivStr = prefs.getString(KEY_BIO_IV, null) ?: return null
            val iv = Base64.decode(ivStr, Base64.DEFAULT)

            val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_GCM + "/"
                    + KeyProperties.ENCRYPTION_PADDING_NONE)
            cipher.init(Cipher.DECRYPT_MODE, bioKek, GCMParameterSpec(128, iv)) // Use GCMParameterSpec for GCM mode

            return BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Called after successful Bio Auth
    fun processBiometricResult(context: Context, cryptoObject: BiometricPrompt.CryptoObject): Boolean {
        return try {
            val cipher = cryptoObject.cipher ?: return false
            val prefs = getPrefs(context)
            val encDekStr = prefs.getString(KEY_ENC_DEK_BIO, null) ?: return false
            val encDek = Base64.decode(encDekStr, Base64.DEFAULT)

            val dekBytes = cipher.doFinal(encDek)
            masterKey = SecretKeySpec(dekBytes, DEK_ALGORITHM)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Helpers ---

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun generateDek(): SecretKey {
        val keyGen = KeyGenerator.getInstance(DEK_ALGORITHM)
        keyGen.init(DEK_SIZE)
        return keyGen.generateKey()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun deriveKeyFromPin(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(data: ByteArray, key: SecretKey): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return Pair(encrypted, iv)
    }

    private fun decrypt(data: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(data)
    }
    
    private fun generateBiometricKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        
        val builder = KeyGenParameterSpec.Builder(
            BIO_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1) // Require auth for EVERY use
            
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
    
    // Wrapper for GCM Spec (API 19+) for compatibility logic if needed, but we are API 34+
    private class GCMParameterSpec(tLen: Int, iv: ByteArray) : javax.crypto.spec.GCMParameterSpec(tLen, iv)
}
