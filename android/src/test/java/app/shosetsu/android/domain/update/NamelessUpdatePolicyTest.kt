package app.shosetsu.android.domain.update

import app.shosetsu.android.domain.model.remote.AppUpdateDTO
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/* Nameless updater security/compatibility regressions, 2026-10-06. GPL-3.0. */
class NamelessUpdatePolicyTest {
    private val policy = NamelessUpdatePolicy
    private val apkUrl = "${policy.REPOSITORY_URL}/releases/download/v55/Nameless-v55.apk"
    private fun release() = GitHubRelease("v55", assets = listOf(GitHubReleaseAsset("Nameless-v55.apk", apkUrl, 1024)))
    private fun manifest() = NamelessReleaseManifest(1, policy.APPLICATION_ID, 55, "2.5.3-nameless.6", 22, apkUrl, "a".repeat(64), 1024, policy.CERTIFICATE_SHA256, listOf("Fix"))
    private fun candidate() = policy.validateManifest(manifest(), release())
    private fun rejected(block: () -> Unit) { assertThrows(IOException::class.java, block) }
    private fun facts(
        packageName: String = policy.APPLICATION_ID, code: Long = 55,
        current: Long = 54, expected: Int = 55, minSdk: Int = 22, sdk: Int = 28,
        apkSigners: Set<String> = setOf(policy.CERTIFICATE_SHA256),
        installedSigners: Set<String> = setOf(policy.CERTIFICATE_SHA256),
    ) = policy.validateApkFacts(packageName, code, current, expected, minSdk, sdk, apkSigners, installedSigners)

    @Test fun validStableReleaseCanBeOffered() {
        val u = candidate()
        assertEquals(55, u.versionCode)
        assertEquals("v55", u.releaseTag)
        policy.validateCandidate(u, 54, 22)
    }
    @Test fun originalShosetsuPackageCannotReplaceNameless() { rejected { facts(packageName = "app.shosetsu.android") } }
    @Test fun wrongApkCertificateIsRejected() { rejected { facts(apkSigners = setOf("b".repeat(64))) } }
    @Test fun wrongInstalledCertificateIsRejected() { rejected { facts(installedSigners = setOf("b".repeat(64))) } }
    @Test fun multipleApkSignersAreRejected() { rejected { facts(apkSigners = setOf(policy.CERTIFICATE_SHA256, "b".repeat(64))) } }
    @Test fun missingCertificateIsRejected() { rejected { facts(apkSigners = emptySet()) } }
    @Test fun downgradeAndSameVersionAreRejected() {
        rejected { facts(code = 54, current = 54, expected = 54) }
        rejected { facts(code = 53, current = 54, expected = 53) }
    }
    @Test fun apkVersionMustEqualManifestVersion() { rejected { facts(code = 56, expected = 55) } }
    @Test fun apkCannotRequireNewerAndroid() { rejected { facts(minSdk = 29, sdk = 28) } }
    @Test fun genuineNewSameSignerApkPasses() { facts() }
    @Test fun metadataCannotOfferSameOrOlderVersion() {
        rejected { policy.validateCandidate(candidate(), 55, 28) }
        rejected { policy.validateCandidate(candidate(), 56, 28) }
    }
    @Test fun metadataCannotRequireNewerAndroid() { rejected { policy.validateCandidate(candidate().copy(minSdk = 29), 54, 28) } }
    @Test fun draftAndPrereleaseAreRejected() {
        rejected { policy.validateManifest(manifest(), release().copy(draft = true)) }
        rejected { policy.validateManifest(manifest(), release().copy(prerelease = true)) }
    }
    @Test fun manifestCannotAdvertiseDifferentAppOrKey() {
        rejected { policy.validateManifest(manifest().copy(applicationId = "app.shosetsu.android"), release()) }
        rejected { policy.validateManifest(manifest().copy(signingCertificateSha256 = "b".repeat(64)), release()) }
    }
    @Test fun manifestVersionMustMatchReleaseTag() { rejected { policy.validateManifest(manifest().copy(versionCode = 56), release()) } }
    @Test fun malformedTagsCannotSelectArbitraryDownloadPaths() {
        for (tag in listOf("v53", "v055", "v1.2.3", "../v55", "v55/bad", "v55;echo BAD", "v-1", "v99999999999")) {
            rejected { policy.versionCodeFromTag(tag) }
        }
        assertEquals(55, policy.versionCodeFromTag("v55"))
    }
    @Test fun foreignRepositoryOrInsecureUrlIsRejected() {
        for (url in listOf(apkUrl.replace("https:", "http:"), apkUrl.replace("tensozanghetzu-hub", "someone-else"), apkUrl.replace("github.com/", "github.com.evil.example/"), apkUrl.replace("Nameless/releases", "Shosetsu/releases"))) {
            rejected { policy.requireAssetUrl(url, "v55", "Nameless-v55.apk") }
        }
    }
    @Test fun urlCredentialsQueriesFragmentsAndWrongPortAreRejected() {
        for (url in listOf(apkUrl+"?token=secret", apkUrl+"#anything", apkUrl.replace("github.com", "user:pass@github.com"), apkUrl.replace("github.com", "github.com:1234"))) {
            rejected { policy.requireAssetUrl(url, "v55", "Nameless-v55.apk") }
        }
    }
    @Test fun hashAndDownloadSizeMustBeValid() {
        rejected { policy.validateManifest(manifest().copy(sha256 = "bad"), release()) }
        rejected { policy.validateManifest(manifest().copy(sizeBytes = 0), release()) }
        rejected { policy.validateManifest(manifest().copy(sizeBytes = policy.MAX_APK_BYTES + 1), release()) }
    }
    @Test fun missingOrMismatchedApkAssetsAreRejected() {
        rejected { policy.validateManifest(manifest(), release().copy(assets = emptyList())) }
        rejected { policy.validateManifest(manifest(), release().copy(assets = listOf(GitHubReleaseAsset("Nameless-v55.apk", apkUrl, 2048)))) }
        rejected { policy.validateManifest(manifest(), release().copy(assets = listOf(GitHubReleaseAsset("Nameless-v55.apk", apkUrl+"?x", 1024)))) }
    }
    @Test fun staleLegacyCacheMetadataCannotAuthorizeAnUpdate() {
        rejected { policy.validateCandidate(candidate().copy(sha256 = "", releaseTag = ""), 54, 28) }
    }
    @Test fun cachedUpdateRoundTripRetainsVerificationMetadata() {
        val original = candidate()
        val dto = AppUpdateDTO.fromEntity(original)
        val saved = Json.encodeToString(dto)
        assertEquals(original, Json.decodeFromString<AppUpdateDTO>(saved).convertTo())
    }
    @Test fun legacyCacheDeserializesButCannotBecomeTrusted() {
        val old = """{"latestVersion":"old","versionCode":99,"url":"https://cdn.shosetsu.app/app.apk","releaseNotes":[]}"""
        val value = Json.decodeFromString<AppUpdateDTO>(old).convertTo()
        rejected { policy.validateCandidate(value, 54, 28) }
    }
}
