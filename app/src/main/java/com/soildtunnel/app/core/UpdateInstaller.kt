package com.soildtunnel.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.soildtunnel.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads an update APK, proves it is signed by the key that signed the
 * app already installed, and hands it to the system installer.
 *
 * SECURITY: this is the app's only runtime code-delivery path, and the
 * signature check is what makes it safe to have one. The bytes arrive over
 * TLS from a GitHub Release, but TLS only protects the hop — a tampered
 * release, a poisoned cache or a swapped file would otherwise be installed
 * without a second thought. Comparing the file's signer against the signer
 * of the installed app means none of them can reach the installer: Android
 * would refuse a mismatched update anyway, but discarding it here means the
 * user is never even shown a dialog for a file we already know is wrong.
 */
object UpdateInstaller {

    sealed class Outcome {
        /** Downloaded and signature-checked; safe to hand to the installer. */
        data class Ready(val apk: File) : Outcome()

        /** Anything that stopped the update, including a signer mismatch. */
        data class Failed(val reason: String) : Outcome()
    }

    /** Staged under the cache path that res/xml/file_paths.xml exposes. */
    private fun stagingDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    /**
     * Streams [url] into the update cache and verifies the result.
     * [onProgress] receives 0..100 on the main thread.
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        val target = File(stagingDir(context), "update.apk")
        try {
            target.delete()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "SoildTunnel/${BuildConfig.VERSION_NAME}")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    return@withContext Outcome.Failed("HTTP $code")
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != last) {
                                    last = percent
                                    withContext(Dispatchers.Main) { onProgress(percent) }
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (!signerMatchesInstalled(context, target)) {
                target.delete()
                return@withContext Outcome.Failed("signature mismatch")
            }
            Outcome.Ready(target)
        } catch (e: Exception) {
            target.delete()
            Outcome.Failed(e.message ?: "download failed")
        }
    }

    /**
     * True when [apk] is signed by the key that already signed the installed
     * app. Fails closed: an unreadable signer on either side means no match.
     */
    private fun signerMatchesInstalled(context: Context, apk: File): Boolean {
        val installed = installedSigners(context)
        if (installed.isEmpty()) return false
        val archive = archiveSigners(context, apk)
        if (archive.isEmpty()) return false
        return installed.all { it in archive }
    }

    private fun installedSigners(context: Context): Set<String> {
        val info = try {
            context.packageManager.getPackageInfo(context.packageName, signingFlags())
        } catch (_: Exception) {
            return emptySet()
        }
        return signerStrings(info)
    }

    private fun archiveSigners(context: Context, apk: File): Set<String> {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, signingFlags())
            ?: return emptySet()
        return signerStrings(info)
    }

    /** The flag that actually reads a package's signing certificate. */
    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signerStrings(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures?.map { it.toCharsString() }?.toSet() ?: emptySet()
    }

    /** True once the user has allowed this app to install packages. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen that grants that allowance. */
    fun requestInstallPermission(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the verified APK to the system installer. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
