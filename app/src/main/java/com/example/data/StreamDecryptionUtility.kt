package com.example.data

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

object StreamDecryptionUtility {
    private const val TAG = "StreamDecryption"
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val PREFS_NAME = "decryption_secure_prefs"
    private const val KEY_PREF_KEY = "rotated_decryption_key"

    @Volatile
    private var activeKeyString: String? = null

    private val ivBytes = byteArrayOf(
        0xA5.toByte(), 0x5A.toByte(), 0x12.toByte(), 0x34.toByte(),
        0x56.toByte(), 0x78.toByte(), 0x90.toByte(), 0xAB.toByte(),
        0xCD.toByte(), 0xEF.toByte(), 0xFE.toByte(), 0xDC.toByte(),
        0xBA.toByte(), 0x09.toByte(), 0x87.toByte(), 0x65.toByte()
    )

    /**
     * Initializes the key from persistent SharedPreferences or BuildConfig fallback.
     */
    private fun loadKeyString(context: Context?): String {
        activeKeyString?.let { return it }

        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedKey = prefs.getString(KEY_PREF_KEY, null)
            if (!savedKey.isNullOrEmpty()) {
                activeKeyString = savedKey
                return savedKey
            }
        }

        val configKey = try {
            BuildConfig.STREAM_DECRYPTION_KEY
        } catch (e: Exception) {
            null
        } ?: "Secr3tM3u8Str3am"

        activeKeyString = configKey
        return configKey
    }

    /**
     * Updates and saves a new rotated key securely.
     */
    fun updateKey(context: Context, newKey: String) {
        if (newKey.isBlank()) return
        activeKeyString = newKey
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PREF_KEY, newKey).apply()
        android.util.Log.i(TAG, "Decryption key successfully rotated and saved locally.")
    }

    /**
     * Gets 16-byte key bytes of the active key for AES-128.
     */
    private fun getKeyBytes(context: Context? = null): ByteArray {
        val keyStr = loadKeyString(context)
        val bytes = keyStr.toByteArray(StandardCharsets.UTF_8)
        return if (bytes.size >= 16) {
            bytes.copyOf(16)
        } else {
            val padded = ByteArray(16)
            System.arraycopy(bytes, 0, padded, 0, bytes.size)
            padded
        }
    }

    /**
     * Encrypts plain text using the currently active AES key.
     */
    fun encrypt(plainText: String, context: Context? = null): String {
        if (plainText.isBlank()) return plainText
        return try {
            val keySpec = SecretKeySpec(getKeyBytes(context), "AES")
            val ivSpec = IvParameterSpec(ivBytes)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val base64 = Base64.encodeToString(encrypted, Base64.NO_WRAP or Base64.URL_SAFE)
            "encrypted://$base64"
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Encryption error", e)
            plainText
        }
    }

    /**
     * Decrypts an encrypted token using the currently active AES key.
     */
    fun decrypt(token: String, context: Context? = null): String {
        if (!token.startsWith("encrypted://")) return token
        val cipherText = token.removePrefix("encrypted://")
        return try {
            val keySpec = SecretKeySpec(getKeyBytes(context), "AES")
            val ivSpec = IvParameterSpec(ivBytes)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.decode(cipherText, Base64.NO_WRAP or Base64.URL_SAFE)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Decryption error", e)
            token
        }
    }

    /**
     * Fetches a new AES key from a secure backend endpoint.
     * Supports both plain text and JSON response structures.
     */
    fun fetchAndRotateKey(
        context: Context,
        endpointUrl: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val client = CachedHttpClient.getBaseClient()
        val request = try {
            Request.Builder()
                .url(endpointUrl)
                .header("Cache-Control", "no-cache") // Ensure fresh key
                .build()
        } catch (e: Exception) {
            onComplete(false, "Invalid endpoint URL: ${e.message}")
            return
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e(TAG, "Failed to connect to key endpoint: ${e.message}")
                onComplete(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        onComplete(false, "Server returned error code: ${resp.code}")
                        return
                    }
                    val bodyString = resp.body?.string()?.trim()
                    if (bodyString.isNullOrEmpty()) {
                        onComplete(false, "Empty key body returned from server")
                        return
                    }

                    try {
                        // 1. Try to parse as JSON first (e.g. {"key": "new_rotated_key"})
                        val json = JSONObject(bodyString)
                        val extractedKey = if (json.has("key")) {
                            json.getString("key")
                        } else if (json.has("decryptionKey")) {
                            json.getString("decryptionKey")
                        } else {
                            null
                        }

                        if (!extractedKey.isNullOrBlank()) {
                            updateKey(context, extractedKey)
                            onComplete(true, extractedKey)
                            return
                        }
                    } catch (jsonEx: Exception) {
                        // Not a JSON object or parsing failed; proceed to treat as raw text
                    }

                    // 2. Fallback to raw text key
                    if (bodyString.length >= 8) {
                        updateKey(context, bodyString)
                        onComplete(true, bodyString)
                    } else {
                        onComplete(false, "Key is too short (must be at least 8 chars)")
                    }
                }
            }
        })
    }

    /**
     * Generates a dynamic, high-entropy secure AES-128 key locally.
     * Perfect for offline operation, fallbacks, or instant rotation testing.
     */
    fun rotateKeyLocally(context: Context): String {
        return try {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(128)
            val secretKey = keyGen.generateKey()
            val base64Key = Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP or Base64.URL_SAFE)
            updateKey(context, base64Key)
            base64Key
        } catch (e: Exception) {
            val fallbackKey = "local_" + System.currentTimeMillis()
            updateKey(context, fallbackKey)
            fallbackKey
        }
    }
}
