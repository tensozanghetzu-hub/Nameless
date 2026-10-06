package app.shosetsu.android.domain.catalog

import java.net.URI
import java.util.LinkedHashSet

/* Nameless extension-avatar recovery, 2026-10-06. GPL-3.0.
 * Network candidates are intentionally restricted to HTTPS / HTTP image URLs.
 * No third-party image proxy is used and no images are bundled/copied.
 */
object ExtensionIconPolicy {
    const val LEGACY_GITLAB_PAGES_HOST = "shosetsuorg.gitlab.io"
    const val CURRENT_EXTENSION_ICON_PREFIX = "https://gitlab.com/shosetsuorg/extensions/-/raw/dev/icons/"

    /**
     * Prefer the icon from the same repository as an installed source. This lets
     * a refreshed repository index repair an old cached installed-icon URL.
     */
    fun preferredImageUrl(
        installedRepositoryImageUrl: String?,
        installedSavedImageUrl: String?,
        availableRepositoryImageUrls: List<String>,
    ): String = sequenceOf(installedRepositoryImageUrl, installedSavedImageUrl)
        .plus(availableRepositoryImageUrls.asSequence())
        .mapNotNull(::normaliseImageUrlOrNull)
        .firstOrNull()
        .orEmpty()

    /**
     * Candidate order: the advertised URL, a known official-repository move for
     * the retired GitLab Pages icon host, then same-origin favicons. The legacy
     * Pages host itself is omitted as a favicon fallback because it now redirects
     * to GitLab's sign-in page instead of serving the old public icons.
     */
    fun candidates(advertisedUrl: String): List<String> {
        val normalized = normaliseImageUrlOrNull(advertisedUrl) ?: return emptyList()
        val source = URI(normalized)
        val result = LinkedHashSet<String>()
        result += normalized
        officialRepositoryMove(source)?.let(result::add)

        if (isPublicHostname(source.host) && !isRetiredGitLabPagesHost(source.host) && !isHostedMetadataHost(source.host)) {
            val secureOrigin = origin(source, preferHttps = true)
            if (secureOrigin != null) {
                result += "$secureOrigin/favicon.ico"
                result += "$secureOrigin/apple-touch-icon.png"
            }
        }
        return result.toList()
    }

    /** Add a minimal same-origin referrer for hosts which reject image hotlinks. */
    fun refererFor(candidateUrl: String): String? =
        runCatching { URI(candidateUrl) }.getOrNull()?.let { origin(it, preferHttps = true)?.plus("/") }

    private fun normaliseImageUrlOrNull(value: String?): String? {
        val input = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        if (!uri.isAbsolute || uri.scheme?.lowercase() !in setOf("https", "http") ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null || (uri.port != -1 && uri.port !in 1..65535) ||
            uri.rawPath.isNullOrBlank()) return null
        return uri.toASCIIString()
    }

    private fun officialRepositoryMove(source: URI): String? {
        val path = source.rawPath ?: return null
        val filename = path.substringAfterLast('/')
        if (filename.isBlank() || !filename.matches(Regex("[A-Za-z0-9._~-]+"))) return null
        return when {
            source.scheme.equals("https", ignoreCase = true) && isRetiredGitLabPagesHost(source.host) &&
                path.startsWith("/extensions/icons/") -> CURRENT_EXTENSION_ICON_PREFIX + filename

            source.scheme.equals("https", ignoreCase = true) &&
                source.host.equals("raw.githubusercontent.com", ignoreCase = true) &&
                path.startsWith("/shosetsuorg/extensions/dev/icons/") -> CURRENT_EXTENSION_ICON_PREFIX + filename

            else -> null
        }
    }

    private fun isRetiredGitLabPagesHost(host: String?): Boolean =
        host.equals(LEGACY_GITLAB_PAGES_HOST, ignoreCase = true)

    private fun isHostedMetadataHost(host: String?): Boolean =
        host.equals("gitlab.com", ignoreCase = true) ||
            host.equals("raw.githubusercontent.com", ignoreCase = true) ||
            host.equals("github.com", ignoreCase = true)

    // Favicon retries only make sense for public site hostnames. Do not expand a
    // repository-provided icon URL into localhost/private-network favicon probes.
    private fun isPublicHostname(host: String?): Boolean {
        val value = host?.lowercase()?.trimEnd('.') ?: return false
        if (value.length > 253 || !value.contains('.') || value.contains(':') ||
            Regex("[0-9.]+").matches(value)) return false
        val nonPublicSuffixes = listOf(
            ".localhost", ".local", ".localdomain", ".internal", ".test", ".invalid",
            ".example", ".lan", ".home", ".home.arpa", ".onion", ".arpa", ".corp", ".private",
        )
        if (nonPublicSuffixes.any(value::endsWith)) return false
        return value.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 && label.firstOrNull()?.isLetterOrDigit() == true &&
                label.lastOrNull()?.isLetterOrDigit() == true && label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    private fun origin(uri: URI, preferHttps: Boolean): String? {
        val scheme = if (preferHttps) "https" else uri.scheme?.lowercase()
        val host = uri.host ?: return null
        if (host.contains("/") || host.contains("\\") || host.startsWith(".")) return null
        val port = if (preferHttps && uri.scheme.equals("http", ignoreCase = true) && uri.port == 80) -1 else uri.port
        return buildString {
            append(scheme).append("://").append(host.lowercase())
            if (port != -1 && !(scheme == "https" && port == 443) && !(scheme == "http" && port == 80)) {
                append(':').append(port)
            }
        }
    }
}
