package app.shosetsu.android.datasource.remote.impl.update

import app.shosetsu.android.BuildConfig
import app.shosetsu.android.datasource.remote.base.IRemoteAppUpdateDataSource
import app.shosetsu.android.domain.model.local.AppUpdateEntity
import app.shosetsu.android.domain.update.GitHubRelease
import app.shosetsu.android.domain.update.NamelessReleaseManifest
import app.shosetsu.android.domain.update.NamelessUpdatePolicy
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/* Original Shosetsu update datasource, GPL-3.0, Doomsdayrs, 2020.
 * Nameless, modified 2026-10-06: public GitHub Releases only; no tokens or
 * upstream Shosetsu APKs. Stable release manifest/asset validation is mandatory.
 */
class GitAppUpdateDataSource(
    okHttpClient: OkHttpClient,
    private val apiUrl: String = NamelessUpdatePolicy.LATEST_API_URL,
) : IRemoteAppUpdateDataSource.Downloadable {
    private val client = okHttpClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun request(url: String) = Request.Builder().url(url)
        .header("User-Agent", "Nameless/${BuildConfig.VERSION_NAME}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Cache-Control", "no-cache")
        .build()

    private fun noUpdate() = AppUpdateEntity(
        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        url = "", notes = listOf("No newer, ready stable Nameless release is published yet."),
    )

    override suspend fun loadAppUpdate(): AppUpdateEntity {
        val release = client.newCall(request(apiUrl)).execute().use { response ->
            // A new/empty public repository has no release yet. This is normal.
            if (response.code == 404) return noUpdate()
            if (!response.isSuccessful) throw IOException("GitHub update check failed (HTTP ${response.code}). Try again later.")
            val body = response.body ?: throw IOException("GitHub returned an empty release.")
            json.decodeFromString<GitHubRelease>(readJson(body.byteStream()))
        }
        if (release.draft || release.prerelease) return noUpdate()
        val code = NamelessUpdatePolicy.versionCodeFromTag(release.tag_name)
        if (code <= BuildConfig.VERSION_CODE) return noUpdate()
        // A published release triggers CI; until manifest upload finishes, do not
        // show a half-built update or ask the user to download a missing APK.
        val asset = release.assets.singleOrNull { it.name == NamelessUpdatePolicy.MANIFEST_NAME }
            ?: return noUpdate()
        NamelessUpdatePolicy.requireAssetUrl(asset.browser_download_url, release.tag_name, asset.name)
        if (asset.size !in 1..NamelessUpdatePolicy.MAX_JSON_BYTES) throw IOException("Update manifest is too large or empty.")
        val manifest = client.newCall(request(asset.browser_download_url)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not retrieve the Nameless manifest (HTTP ${response.code}).")
            if (!response.request.url.isHttps) throw IOException("Update manifests require HTTPS.")
            val body = response.body ?: throw IOException("Empty Nameless update manifest.")
            json.decodeFromString<NamelessReleaseManifest>(readJson(body.byteStream()))
        }
        return NamelessUpdatePolicy.validateManifest(manifest, release)
    }

    override suspend fun downloadAppUpdate(update: AppUpdateEntity): InputStream {
        NamelessUpdatePolicy.requireAssetUrl(update.url, update.releaseTag, "Nameless-${update.releaseTag}.apk")
        val response = client.newCall(request(update.url)).execute()
        if (!response.isSuccessful || !response.request.url.isHttps) {
            response.close()
            throw IOException("Cannot download the signed Nameless APK (HTTP ${response.code}).")
        }
        val body = response.body ?: run { response.close(); throw IOException("Empty APK download.") }
        if (body.contentLength() > NamelessUpdatePolicy.MAX_APK_BYTES) {
            response.close()
            throw IOException("APK exceeds the allowed download size.")
        }
        return object : FilterInputStream(body.byteStream()) {
            private var count = 0L
            private fun counted(n: Int): Int {
                if (n > 0) count += n
                if (count > update.sizeBytes || count > NamelessUpdatePolicy.MAX_APK_BYTES) {
                    throw IOException("APK download exceeded its declared size.")
                }
                return n
            }
            override fun read(): Int {
                val value = `in`.read()
                counted(if (value < 0) 0 else 1)
                return value
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = counted(`in`.read(buffer, offset, length))
            override fun close() { response.close() }
        }
    }

    private fun readJson(input: InputStream): String = input.use {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            if (output.size() + read > NamelessUpdatePolicy.MAX_JSON_BYTES) throw IOException("GitHub metadata exceeded the size limit.")
            output.write(buffer, 0, read)
        }
        output.toString("UTF-8")
    }
}
