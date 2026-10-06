package app.shosetsu.android.domain.catalog

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class EnglishExtensionCatalogPolicyTest {
    private val policy = EnglishExtensionCatalogPolicy

    @Test fun recognizesEnglishLanguageCodesAndName() {
        for (value in listOf("en", "eng", "English", "EN", " ENG ")) assertTrue(value, policy.isEnglish(value))
    }
    @Test fun recognizesEnglishRegionalTags() {
        for (value in listOf("en-US", "en-GB", "en_AU", "EN-ca", "eng-US", "en-Latn-US")) assertTrue(value, policy.isEnglish(value))
    }
    @Test fun rejectsNonEnglishLanguages() {
        for (value in listOf("ru", "es", "pt-BR", "zh-CN", "ja", "ko", "id", "fr", "de", "ar", "ro")) assertFalse(value, policy.isEnglish(value))
    }
    @Test fun unknownOrUnspecifiedLanguageIsNotInventedAsEnglish() {
        for (value in listOf("", " ", "all", "mul", "multi", "unknown", "und")) assertFalse(value, policy.isEnglish(value))
    }
    @Test fun substringOrMalformedTagsAreNotEnglish() {
        for (value in listOf("french", "englishish", "fr-en", "en/fr", "en,fr", "en-", "en--US", "en-123456789")) assertFalse(value, policy.isEnglish(value))
    }
    @Test fun installedForeignAndUnknownSourcesRemainVisible() {
        for (value in listOf("es", "ru", "ja", "", "mul")) assertTrue(policy.isVisible(value, true))
    }
    @Test fun newForeignAndUnknownSourcesAreNotVisible() {
        for (value in listOf("es", "ru", "ja", "", "mul")) assertFalse(policy.isVisible(value, false))
        assertTrue(policy.isVisible("en", false))
    }
    @Test fun newEnglishSourcesDoNotStayHiddenByOldPreference() {
        assertFalse(policy.isHiddenByLanguageChoice("en", false, setOf("en", "es")))
    }
    @Test fun existingInstalledLanguagePreferencesRemainIntact() {
        assertTrue(policy.isHiddenByLanguageChoice("en", true, setOf("en")))
        assertTrue(policy.isHiddenByLanguageChoice("es", true, setOf("es")))
        assertFalse(policy.isHiddenByLanguageChoice("es", true, setOf("en")))
    }
    @Test fun normalizationDoesNotDependOnPhonesLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertTrue(policy.isEnglish("EN"))
            assertTrue(policy.isEnglish("ENGLISH"))
        } finally { Locale.setDefault(original) }
    }
}
