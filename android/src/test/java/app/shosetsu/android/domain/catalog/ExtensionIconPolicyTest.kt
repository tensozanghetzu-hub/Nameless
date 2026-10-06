package app.shosetsu.android.domain.catalog

import org.junit.Assert.*
import org.junit.Test

class ExtensionIconPolicyTest {
    private val policy = ExtensionIconPolicy

    @Test fun rewritesRetiredOfficialGitlabPagesIconToTestedRawGitlabAsset() {
        val old = "https://shosetsuorg.gitlab.io/extensions/icons/NovelFull.png"
        assertEquals(
            listOf(old, "${ExtensionIconPolicy.CURRENT_EXTENSION_ICON_PREFIX}NovelFull.png"),
            policy.candidates(old),
        )
    }

    @Test fun rewritesTheRetiredSkyMtlLogoToTheVerifiedNovelRareBrandIcon() {
        val old = "https://sky-mtl.com/wp-content/uploads/2017/10/10.png"
        assertEquals(listOf(old, ExtensionIconPolicy.CURRENT_NOVELRARE_ICON), policy.candidates(old))
    }

    @Test fun rewritesAllPublishedFilenameCharactersUsedByOfficialIcons() {
        for (name in listOf("NovLove.png", "NovelFullNET.png", "SpaceBattles-2.png", "icon_1.webp")) {
            val url = "https://shosetsuorg.gitlab.io/extensions/icons/$name"
            assertEquals("${ExtensionIconPolicy.CURRENT_EXTENSION_ICON_PREFIX}$name", policy.candidates(url)[1])
        }
    }

    @Test fun matchesRetiredPagesHostCaseInsensitivelyButNotOtherHosts() {
        val fixed = policy.candidates("https://SHOSETSUORG.GITLAB.IO/extensions/icons/Test.png")
        assertEquals("${ExtensionIconPolicy.CURRENT_EXTENSION_ICON_PREFIX}Test.png", fixed[1])
        assertFalse(policy.candidates("https://evil.example.com/extensions/icons/Test.png").any { it.startsWith(ExtensionIconPolicy.CURRENT_EXTENSION_ICON_PREFIX) })
    }

    @Test fun supportsFormerGithubRawExtensionIconsToo() {
        val url = "https://raw.githubusercontent.com/shosetsuorg/extensions/dev/icons/NovelFull.png"
        assertEquals(url, policy.candidates(url)[0])
        assertEquals("${ExtensionIconPolicy.CURRENT_EXTENSION_ICON_PREFIX}NovelFull.png", policy.candidates(url)[1])
    }

    @Test fun retainsCurrentWorkingOfficialImageAsFirstCandidate() {
        val current = "https://gitlab.com/shosetsuorg/extensions/-/raw/dev/icons/Foxaholic.png"
        assertEquals(current, policy.candidates(current).first())
        assertEquals(1, policy.candidates(current).count { it == current })
    }

    @Test fun genericBrokenSiteImagesCanFallBackToSameOriginFavicon() {
        val url = "https://novel.example.com/assets/images/logo.png?version=7"
        assertEquals(
            listOf(url, "https://novel.example.com/favicon.ico", "https://novel.example.com/apple-touch-icon.png"),
            policy.candidates(url),
        )
    }

    @Test fun cleartextAdvertisedImagesGetSecureSiteIconFallback() {
        val url = "http://novel.example.com/logo.png"
        assertEquals(url, policy.candidates(url).first())
        assertTrue(policy.candidates(url).contains("https://novel.example.com/favicon.ico"))
        assertEquals("https://novel.example.com/", policy.refererFor(url))
    }

    @Test fun gitlabPagesLoginFallbackIsNotMistakenForTheSourceAvatar() {
        val urls = policy.candidates("https://shosetsuorg.gitlab.io/extensions/icons/ReadFromNet.png")
        assertFalse(urls.any { it == "https://shosetsuorg.gitlab.io/favicon.ico" })
        assertFalse(urls.any { it == "https://shosetsuorg.gitlab.io/apple-touch-icon.png" })
        assertTrue(urls.any { it == "${ExtensionIconPolicy.CURRENT_EXTENSION_ICON_PREFIX}ReadFromNet.png" })
    }

    @Test fun emptyMalformedRelativeAndUnsafeSchemesHaveNoNetworkCandidates() {
        for (url in listOf("", "   ", "icons/NovelFull.png", "javascript:alert(1)", "file:///etc/passwd", "data:image/png;base64,AA==", "https://user:password@novel.example/logo.png", "https:///missing-host/logo.png")) {
            assertTrue("$url -> ${policy.candidates(url)}", policy.candidates(url).isEmpty())
        }
    }

    @Test fun freshInstalledRepositoryIconWinsOverStaleInstalledSnapshot() {
        assertEquals(
            "https://cdn.example/new-logo.png",
            policy.preferredImageUrl(
                installedRepositoryImageUrl = " https://cdn.example/new-logo.png ",
                installedSavedImageUrl = "https://old.example/logo.png",
                availableRepositoryImageUrls = listOf("https://other.example/logo.png"),
            ),
        )
    }

    @Test fun oldInstalledIconSurvivesAnEmptyCurrentRepositoryIcon() {
        assertEquals(
            "https://old.example/logo.png",
            policy.preferredImageUrl(" ", " https://old.example/logo.png ", listOf("https://other.example/icon.png")),
        )
    }

    @Test fun catalogIconFillsAnOlderEmptyInstalledIcon() {
        assertEquals(
            "https://cdn.example/logo.png",
            policy.preferredImageUrl(null, "", listOf(" ", "https://cdn.example/logo.png")),
        )
    }

    @Test fun noIconMetadataRemainsEmptyInsteadOfInventingSiteFromName() {
        assertEquals("", policy.preferredImageUrl(null, null, listOf("", " ")))
        assertTrue(policy.candidates("").isEmpty())
    }

    @Test fun fallbackDoesNotLeakAnAdvertisedUrlQuery() {
        val urls = policy.candidates("https://novel.example.com/image.png?key=secret")
        assertEquals(listOf("https://novel.example.com/image.png?key=secret", "https://novel.example.com/favicon.ico", "https://novel.example.com/apple-touch-icon.png"), urls)
        assertFalse(urls.drop(1).any { it.contains("secret") })
    }

    @Test fun hostedSourceFaviconDoesNotReplaceAnExtensionSpecificIcon() {
        val url = "https://gitlab.com/shosetsuorg/extensions/-/raw/dev/icons/Test.png"
        assertFalse(policy.candidates(url).contains("https://gitlab.com/favicon.ico"))
    }
    @Test fun faviconFallbackDoesNotExpandToLocalOrPrivateHosts() {
        for (url in listOf("https://localhost/logo.png", "https://printer.local/logo.png", "https://app.internal/logo.png", "https://nas.lan/logo.png", "https://router.home.arpa/logo.png", "https://192.168.0.10/logo.png", "https://127.0.0.1/logo.png", "https://[::1]/logo.png", "https://preview.example/logo.png")) {
            assertEquals(listOf(url), policy.candidates(url))
        }
    }

}
