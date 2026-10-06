package app.shosetsu.android.domain.update

import app.shosetsu.android.domain.model.local.AppUpdateEntity
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.URI

/* Nameless GitHub updates, 2026-10-06. GPL-3.0; original attribution retained. */
object NamelessUpdatePolicy {
    const val REPOSITORY = "tensozanghetzu-hub/Nameless"
    const val APPLICATION_ID = "app.nameless.reader"
    const val REPOSITORY_URL = "https://github.com/$REPOSITORY"
    const val RELEASES_URL = "$REPOSITORY_URL/releases"
    const val LATEST_API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val CERTIFICATE_SHA256 = "2ea9c211fa608f85426ce20c71a7f9fb03ef096829deb8b7e1ab851b3ce3aec8"
    const val MANIFEST_NAME = "nameless-update.json"
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    const val MAX_JSON_BYTES = 1024L * 1024
    private val sha256 = Regex("[a-fA-F0-9]{64}")

    fun versionCodeFromTag(tag: String): Int {
        val match = Regex("v([1-9][0-9]{1,8})").matchEntire(tag)
            ?: throw IOException("Unsupported release tag. Use v55, v56, and so on.")
        return match.groupValues[1].toInt().also {
            if (it < 54) throw IOException("Release versionCode must be at least 54.")
        }
    }

    fun requireAssetUrl(url: String, tag: String, filename: String) {
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host != "github.com" ||
            uri.port !in listOf(-1, 443) || uri.userInfo != null || uri.query != null ||
            uri.fragment != null || uri.rawPath != "/$REPOSITORY/releases/download/$tag/$filename") {
            throw IOException("Update asset is not from the configured Nameless GitHub repository.")
        }
    }

    fun validateManifest(manifest: NamelessReleaseManifest, release: GitHubRelease): AppUpdateEntity {
        if (release.draft || release.prerelease) throw IOException("Only stable, published releases are supported.")
        val code = versionCodeFromTag(release.tag_name)
        if (manifest.schema != 1 || manifest.applicationId != APPLICATION_ID || manifest.versionCode != code ||
            manifest.latestVersion.isBlank() || manifest.minSdk < 22 ||
            manifest.sizeBytes !in 1..MAX_APK_BYTES || !sha256.matches(manifest.sha256) ||
            manifest.signingCertificateSha256.lowercase() != CERTIFICATE_SHA256) {
            throw IOException("The release manifest is invalid or has the wrong Nameless signing identity.")
        }
        val filename = "Nameless-${release.tag_name}.apk"
        requireAssetUrl(manifest.url, release.tag_name, filename)
        val apk = release.assets.singleOrNull { it.name == filename }
            ?: throw IOException("The signed Nameless APK is missing from this release.")
        if (apk.browser_download_url != manifest.url || apk.size != manifest.sizeBytes) {
            throw IOException("Release APK size/URL does not match its manifest.")
        }
        return AppUpdateEntity(
            version = manifest.latestVersion,
            versionCode = code,
            url = manifest.url,
            notes = manifest.releaseNotes,
            sha256 = manifest.sha256.lowercase(),
            sizeBytes = manifest.sizeBytes,
            minSdk = manifest.minSdk,
            signingCertificateSha256 = manifest.signingCertificateSha256.lowercase(),
            releaseTag = release.tag_name,
        )
    }

    fun validateCandidate(update: AppUpdateEntity, installedCode: Int, deviceSdk: Int) {
        if (update.versionCode <= installedCode) throw IOException("This update is not newer than your installed Nameless.")
        if (update.minSdk > deviceSdk) throw IOException("This release requires a newer Android version.")
        if (versionCodeFromTag(update.releaseTag) != update.versionCode ||
            update.sizeBytes !in 1..MAX_APK_BYTES || !sha256.matches(update.sha256) ||
            update.signingCertificateSha256 != CERTIFICATE_SHA256) {
            throw IOException("Update metadata is missing or invalid. Check for updates again.")
        }
        requireAssetUrl(update.url, update.releaseTag, "Nameless-${update.releaseTag}.apk")
    }

    /** Platform-parsed facts, checked BEFORE the APK is offered to Android's installer. */
    fun validateApkFacts(
        packageName: String, apkCode: Long, installedCode: Long, expectedCode: Int,
        apkMinimumSdk: Int, deviceSdk: Int, apkSigners: Set<String>, installedSigners: Set<String>,
    ) {
        if (packageName != APPLICATION_ID || apkCode != expectedCode.toLong() || apkCode <= installedCode ||
            apkMinimumSdk > deviceSdk || apkSigners != setOf(CERTIFICATE_SHA256) ||
            installedSigners != setOf(CERTIFICATE_SHA256)) {
            throw IOException("APK rejected: wrong app, version, Android requirement or signing certificate.")
        }
    }
}

@Serializable
data class NamelessReleaseManifest(
    val schema: Int,
    val applicationId: String,
    val versionCode: Int,
    val latestVersion: String,
    val minSdk: Int,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val signingCertificateSha256: String,
    val releaseNotes: List<String> = emptyList(),
)

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long,
)
