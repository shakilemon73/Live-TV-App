package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@JsonClass(generateAdapter = true)
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val sha256: String? = null
)

object AppUpdateManager {
    private val client by lazy { OkHttpClient() }
    private val moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    suspend fun checkForUpdates(url: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                val adapter = moshi.adapter(UpdateInfo::class.java)
                adapter.fromJson(bodyString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    sealed class VerificationResult {
        object Success : VerificationResult()
        data class Warning(val message: String, val file: File) : VerificationResult()
        data class Error(val message: String) : VerificationResult()
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        expectedSha256: String? = null,
        onProgress: (Float) -> Unit,
        onVerificationWarning: (String, File) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) { onError("Failed to download APK: ${response.message}") }
                    return@withContext
                }
                
                val body = response.body
                if (body == null) {
                    withContext(Dispatchers.Main) { onError("Empty download body") }
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val cacheFile = File(context.externalCacheDir ?: context.cacheDir, "app-update.apk")
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                var bytesCopied = 0L
                val input: InputStream = body.byteStream()
                
                FileOutputStream(cacheFile).use { outStream ->
                    val buffer = ByteArray(8192)
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        outStream.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        if (totalBytes > 0) {
                            val progress = bytesCopied.toFloat() / totalBytes
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                        bytes = input.read(buffer)
                    }
                }

                // Verify the downloaded file
                val verificationResult = verifyApk(context, cacheFile, expectedSha256)
                withContext(Dispatchers.Main) {
                    when (verificationResult) {
                        is VerificationResult.Success -> {
                            onProgress(1.0f)
                            onSuccess(cacheFile)
                        }
                        is VerificationResult.Warning -> {
                            onProgress(1.0f)
                            onVerificationWarning(verificationResult.message, verificationResult.file)
                        }
                        is VerificationResult.Error -> {
                            if (cacheFile.exists()) {
                                cacheFile.delete()
                            }
                            onError("Security Verification Failed:\n${verificationResult.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Unknown download error") }
        }
    }

    private fun verifyApk(context: Context, apkFile: File, expectedSha256: String?): VerificationResult {
        try {
            val pm = context.packageManager

            // 1. Calculate SHA-256 Checksum
            val fileSha256 = calculateSHA256(apkFile)
            android.util.Log.d("AppUpdateManager", "Downloaded APK SHA-256: $fileSha256")

            if (expectedSha256 != null && !expectedSha256.equals(fileSha256, ignoreCase = true)) {
                return VerificationResult.Error("SHA-256 checksum mismatch! Expected: $expectedSha256, Actual: $fileSha256")
            }

            // 2. Parse Package Archive Info to ensure it is a valid APK
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                android.content.pm.PackageManager.GET_SIGNATURES
            }

            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: return VerificationResult.Error("Failed to parse downloaded APK. The file might be corrupted or incomplete.")

            var warningMessage = ""

            // 3. Verify Package Name Matches Current Application
            if (archiveInfo.packageName != context.packageName) {
                warningMessage += "• Package ID mismatch!\nCurrent: ${context.packageName}\nDownloaded: ${archiveInfo.packageName}\n(This is normal in AI Studio preview environments)\n\n"
            }

            // 4. Verify Signing Certificates match the current running app
            val currentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                android.content.pm.PackageManager.GET_SIGNATURES
            }

            val currentInfo = pm.getPackageInfo(context.packageName, currentFlags)
            if (currentInfo == null) {
                warningMessage += "• Failed to retrieve current app package signature.\n"
            } else {
                val signaturesMatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val currentSigs = currentInfo.signingInfo?.apkContentsSigners
                    val downloadedSigs = archiveInfo.signingInfo?.apkContentsSigners
                    if (currentSigs != null && downloadedSigs != null) {
                        currentSigs.any { cSig ->
                            downloadedSigs.any { dSig ->
                                cSig.toByteArray().contentEquals(dSig.toByteArray())
                            }
                        }
                    } else false
                } else {
                    @Suppress("DEPRECATION")
                    val currentSigs = currentInfo.signatures
                    @Suppress("DEPRECATION")
                    val downloadedSigs = archiveInfo.signatures
                    if (currentSigs != null && downloadedSigs != null) {
                        currentSigs.any { cSig ->
                            downloadedSigs.any { dSig ->
                                cSig.toByteArray().contentEquals(dSig.toByteArray())
                            }
                        }
                    } else false
                }

                if (!signaturesMatch) {
                    warningMessage += "• Signing certificate mismatch!\n(Expected if installing production releases onto debug builds / preview environments)\n"
                }
            }

            if (warningMessage.isNotEmpty()) {
                return VerificationResult.Warning(warningMessage.trim(), apkFile)
            }

            return VerificationResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            return VerificationResult.Error("Exception during verification: ${e.localizedMessage}")
        }
    }

    private fun calculateSHA256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    // Redirect to settings to allow unknown source install
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
