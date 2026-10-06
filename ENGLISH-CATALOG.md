# English-only available extension catalog

Modified 2026-10-06 for Nameless release **v56** / `2.5.3-nameless.7`.

## Selected behavior

The user chose **English-only catalog**, not removal of already-installed sources.

- New/uninstalled sources shown in Browse must declare an English language tag: `en`, `eng`, `English`, or an English regional tag such as `en-US`/`en_GB`.
- Non-English and unknown/unclassified new sources are hidden. A vague `all`/`mul` tag is not assumed to guarantee English.
- Already-installed sources of EVERY language remain in the Browse data, with their original versions/repository/update status. Existing installed-source language preferences still apply.
- Old language preferences cannot accidentally keep the newly requested English available catalog hidden. The filter menu explains that language choices below apply to installed sources.
- New non-English installation requests from a stale Browse UI are rejected; existing non-English updates are still allowed. Backup/internal install-by-ID paths are not repurposed as a data purge.
- Catalog filtering is applied to the presentation use case, NOT to stored repository indexes. Repository updates, installed-source updates, migration, novels, history, bookmarks and downloaded chapters are not deleted or reset. Room schema remains 11.
- If filtering leaves no available items, the flow emits an empty list rather than remaining in the loading state.

## Tests

18 regressions were added: 10 policy tests and 8 production catalog-use-case flow tests. Coverage includes English/region/ISO codes, foreign and unknown tags, phone-locale independence, installed-source preservation, old preferences, empty catalogs, changing installed state, download status and unmodified underlying repository data. The fake repositories reject mutation calls so the tests cannot silently uninstall/delete sources.

Existing reader/migration/updater tests remain. GitHub CI runs the entire test suite before the signed release is published. Test/build results for the actual release are supplied with its assets/logs; no physical-phone visual verification is claimed.

## Delivery

This release is published through the configured GitHub updater at **https://github.com/tensozanghetzu-hub/Nameless/releases**. v55 was an earlier unfinished release with no signed assets; v56 contains this change and uses a higher Android versionCode. Users with the GitHub-updater bootstrap can use **More → About → Check for app update**, then Download/Install with Android's confirmation.

The retained key/password are stored only as encrypted repository Actions secrets (with the owner's approval), never public source/release files. Original Shosetsu GPL licensing, attribution, burning-book icon and previous fixes are retained.
