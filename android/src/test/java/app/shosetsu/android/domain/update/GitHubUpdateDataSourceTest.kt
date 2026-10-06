package app.shosetsu.android.domain.update

import app.shosetsu.android.BuildConfig
import app.shosetsu.android.datasource.remote.impl.update.GitAppUpdateDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/* No network/device: exercise the real GitHub datasource with canned HTTPS responses. */
class GitHubUpdateDataSourceTest {
    private val policy = NamelessUpdatePolicy
    private val code = BuildConfig.VERSION_CODE + 1
    private val tag = "v$code"
    private val apkUrl = "${policy.REPOSITORY_URL}/releases/download/$tag/Nameless-$tag.apk"
    private val manifestUrl = "${policy.REPOSITORY_URL}/releases/download/$tag/${policy.MANIFEST_NAME}"
    private val manifest = NamelessReleaseManifest(1, policy.APPLICATION_ID, code, "Test release", 22, apkUrl, "a".repeat(64), 16, policy.CERTIFICATE_SHA256)
    private fun release() = GitHubRelease(tag, assets = listOf(GitHubReleaseAsset(policy.MANIFEST_NAME, manifestUrl, 512), GitHubReleaseAsset("Nameless-$tag.apk", apkUrl, 16)))
    private fun datasource(responses: Map<String, Pair<Int, String>>, requests: MutableList<String> = mutableListOf()): GitAppUpdateDataSource {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request().url.toString())
            val (status, body) = responses[chain.request().url.toString()] ?: error("Unexpected URL: ${chain.request().url}")
            assertNull(chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("Fixture").body(body.toResponseBody()).build()
        }.build()
        return GitAppUpdateDataSource(client)
    }
    @Test fun emptyRepositoryIsNormalAndNotAnUpdateError() = runBlocking<Unit> {
        val source = datasource(mapOf(policy.LATEST_API_URL to (404 to "{}")))
        assertEquals(BuildConfig.VERSION_CODE, source.loadAppUpdate().versionCode)
    }
    @Test fun publishedReleaseWithoutFinishedManifestIsNotOffered() = runBlocking<Unit> {
        val source = datasource(mapOf(policy.LATEST_API_URL to (200 to Json.encodeToString(release().copy(assets = emptyList())))))
        assertEquals(BuildConfig.VERSION_CODE, source.loadAppUpdate().versionCode)
    }
    @Test fun prereleaseDoesNotFetchAssets() = runBlocking<Unit> {
        val calls = mutableListOf<String>()
        val source = datasource(mapOf(policy.LATEST_API_URL to (200 to Json.encodeToString(release().copy(prerelease = true)))), calls)
        assertEquals(BuildConfig.VERSION_CODE, source.loadAppUpdate().versionCode)
        assertEquals(1, calls.size)
    }
    @Test fun validReleaseFetchesOnlyMetadataBeforeUserChoosesDownload() = runBlocking<Unit> {
        val calls = mutableListOf<String>()
        val source = datasource(mapOf(policy.LATEST_API_URL to (200 to Json.encodeToString(release())), manifestUrl to (200 to Json.encodeToString(manifest))), calls)
        assertEquals(code, source.loadAppUpdate().versionCode)
        assertEquals(listOf(policy.LATEST_API_URL, manifestUrl), calls)
        assertFalse(calls.contains(apkUrl))
    }
    @Test fun foreignManifestUrlIsRejectedBeforeItCanBeRequested() = runBlocking<Unit> {
        val bad = release().copy(assets = listOf(GitHubReleaseAsset(policy.MANIFEST_NAME, "https://example.com/nameless-update.json", 512)))
        val source = datasource(mapOf(policy.LATEST_API_URL to (200 to Json.encodeToString(bad))))
        assertThrows(IOException::class.java) { runBlocking { source.loadAppUpdate() } }
    }
    @Test fun apiFailuresAreReportedInsteadOfSilentlyClaimingUpToDate() = runBlocking<Unit> {
        val source = datasource(mapOf(policy.LATEST_API_URL to (403 to "{}")))
        val e = assertThrows(IOException::class.java) { runBlocking { source.loadAppUpdate() } }
        assertTrue(e.message!!.contains("403"))
    }
    @Test fun malformedManifestIsNotOffered() = runBlocking<Unit> {
        val source = datasource(mapOf(policy.LATEST_API_URL to (200 to Json.encodeToString(release())), manifestUrl to (200 to Json.encodeToString(manifest.copy(applicationId = "app.shosetsu.android")))))
        assertThrows(IOException::class.java) { runBlocking { source.loadAppUpdate() } }
    }
    @Test fun downloadCannotExceedItsDeclaredSize() = runBlocking<Unit> {
        val source = datasource(mapOf(apkUrl to (200 to "x".repeat(32))))
        val update = policy.validateManifest(manifest, release())
        assertThrows(IOException::class.java) { runBlocking { source.downloadAppUpdate(update).use { it.readBytes() } } }
    }
}
