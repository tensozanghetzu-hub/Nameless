# Nameless: working source migration

Implemented 2026-10-05 in `2.5.3-nameless.2` (Android versionCode 51). Original Shosetsu GPL-3.0 licensing and attribution are preserved.

## What was wrong

The upstream migration route called `MigrationView(emptyList())`, losing the selected novel IDs. The view displayed an under-construction message, its search results were always empty, and there was no database transfer implementation. This update implements the complete flow, rather than merely removing the placeholder text.

## Install the update

The migration feature is retained in the current **Nameless-2.5.3-github-updater.apk**. Install it over your existing Nameless app. It uses the same `app.nameless.reader` application ID and signing certificate, and versionCode is now 54 (higher than the original 50, migration release 51, status-bar release 52, and panel release 53). The Room schema stays at version 11. Do not uninstall the app or clear its storage; keep a backup before trying a new feature.

## Use Migrate source

1. Open a novel and choose **Migrate source**, or select novels in the library and choose **Migrate sources**.
2. Choose an installed, enabled destination extension. Use the source filter if necessary.
3. Search for the same novel. The original title is prefilled; you can edit it. Search pagination is supported when the extension supports it.
4. Choose the correct result. Alternatively paste the full novel-page URL from that source and choose **Load novel URL**. This also supports extensions without title search.
5. The app retrieves the destination's actual chapter list and displays a preview. Review the title, source, authors, description, chapter counts, matched reading progress, and bookmarks.
6. Confirm the migration. The original remains in the library by default. You may uncheck **Keep original in library** only when all progress-bearing/bookmarked chapters can be matched.
7. Open the destination novel, or continue with the next selected novel when migrating a library selection.

## Data preservation rules

- The original source does not need to be installed or online. Its saved novel and chapter records are read directly from the database.
- Chapters are matched by a unique chapter number (with volume/part safeguards) or a unique normalized full title.
- Duplicate numbers/titles, missing chapters, fractional chapters, and split parts are treated conservatively. Unmatched progress and bookmarks are reported, not guessed.
- Positional matching is an explicit optional checkbox. Enable it only if both chapter lists have the same ordering. Different prologues, extras, parts, or missing chapters can make position-based matching inaccurate.
- Matched read/in-progress status, reading position, chapter bookmarks, and reading history are merged. Existing destination progress/bookmarks are never downgraded or reset.
- Library categories are merged; pinned status and per-novel reader settings are copied. Sort preferences can carry over to a fresh destination, but source-specific list filters are cleared so new chapters are not hidden.
- Existing destination chapters, history, and downloaded flags are retained, even if an older destination chapter is absent from the current remote list.
- Original downloaded files remain with their original novel/source. They are not copied, moved, or falsely marked as downloaded on a different URL/extension/type. Download the destination's chapters separately for offline use.
- Removing the original from the library only clears its library bookmark. The migration does not delete its novel record, chapter records, history, or downloads. The app's separate cache-purge features can still remove unbookmarked records if you explicitly use them later.
- Network loading occurs before confirmation. All library/progress modifications occur in one Room transaction, so a failed transfer rolls back its target writes.
- The new migration UI supports loading, retry/error messages, back navigation, empty results, no-installed-source messages, and multiple selected novels.

## Tests and limits

**26 automated tests passed, zero failures/errors/skips:**

- 21 chapter-planner regression tests, including 1,000 randomized one-to-one mapping cases.
- 5 database transaction tests using the actual generated Room DAOs and SQLite under Robolectric's Android runtime with synthetic novel fixtures. These cover category/pin/reader-setting/history transfers, repeat-safe migrations, protecting existing destination progress/downloaded flags, failure rollback, and rejecting self-migration.

The release build and vital lint passed. The APK container, alignment, and v1/v2/v3 signatures were verified; its certificate matches the previous Nameless APK.

The interactive screen has not been exercised on a physical Android device or full Android emulator. Third-party website and Lua-extension availability, Cloudflare challenges, login requirements, and differing chapter numbering still require verification with the sources you use. If a destination is blocked, sign in or verify the site in the app's Browse/WebView and try again. This feature does not bypass source access requirements.

## Build and test

With JDK 17 and the Android SDK configured:

```bash
CI_MODE=true ./gradlew :android:testStandardReleaseUnitTest --no-daemon --console=plain
CI_MODE=true ./gradlew :android:assembleStandardRelease --no-daemon --console=plain
```

Run the two commands separately on a memory-limited machine. Android SDK 36 and build tools 37.0.0 are used. Robolectric is a test-only dependency and is not packaged in the APK. The delivered APK is a non-minified, non-debuggable release build, signed with the existing private Nameless key.

Current source archive: **Nameless-GitHub-ready-source.zip**. Functional sources, resources, original licenses, core-library source, branding masters, build scripts, and tests are included. Nonfunctional upstream marketing screenshots are omitted to keep the archive small; original upstream links and attribution are retained.
