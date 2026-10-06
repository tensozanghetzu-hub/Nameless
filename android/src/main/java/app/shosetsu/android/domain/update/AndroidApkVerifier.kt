package app.shosetsu.android.domain.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.shosetsu.android.domain.model.local.AppUpdateEntity
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/* Nameless verified in-place updates, 2026-10-06. GPL-3.0. */
object AndroidApkVerifier {
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, update: AppUpdateEntity) {
        if (!file.isFile || file.length() != update.sizeBytes || file.length() > NamelessUpdatePolicy.MAX_APK_BYTES) {
            throw IOException("Downloaded APK is incomplete or has the wrong size.")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        if (hex(digest.digest()) != update.sha256) throw IOException("Downloaded APK failed SHA-256 verification.")
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("Android cannot read the downloaded APK.")
        val installed = pm.getPackageInfo(context.packageName, flags)
        NamelessUpdatePolicy.validateApkFacts(
            archive.packageName, versionCode(archive), versionCode(installed), update.versionCode,
            if (Build.VERSION.SDK_INT >= 24) archive.applicationInfo?.minSdkVersion ?: update.minSdk else update.minSdk,
            Build.VERSION.SDK_INT, signers(archive), signers(installed),
        )
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return certificates.orEmpty().map {
            hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.toSet()
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
