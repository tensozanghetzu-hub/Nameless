package app.shosetsu.android.domain.catalog

import java.util.Locale

/* Nameless English-only available catalog, 2026-10-06. GPL-3.0.
 * This is display/install eligibility, NOT deletion or repository pruning.
 * Existing installed sources of every language remain usable/updatable.
 */
object EnglishExtensionCatalogPolicy {
    private val englishTag = Regex("(?:en|eng)(?:-[a-z0-9]{1,8})*")

    fun isEnglish(language: String): Boolean {
        val normalized = language.trim().replace('_', '-').lowercase(Locale.ROOT)
        return normalized == "english" || englishTag.matches(normalized)
    }

    fun isVisible(language: String, isInstalled: Boolean): Boolean =
        isInstalled || isEnglish(language)

    // New English catalog entries must not disappear because an older install
    // had English unchecked. Existing installed-source language choices remain.
    fun isHiddenByLanguageChoice(language: String, isInstalled: Boolean, hiddenLanguages: Set<String>): Boolean =
        isInstalled && language in hiddenLanguages
}
