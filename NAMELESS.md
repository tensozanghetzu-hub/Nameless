# Nameless — independent Shosetsu-based Android build

Modified on 2026-10-06. This is an independent personal fork, not an official Shosetsu release and not affiliated with its maintainers.

## Requested changes

- App name: **Nameless** in the launcher and app-branding text.
- Icon: an open book with amber/orange flames on a dark background. Includes legacy, round, adaptive, themed monochrome, and splash-screen assets.
- Unique Android application ID: `app.nameless.reader`, so the supplied APK installs alongside the original Shosetsu rather than replacing it.

The original novel reader, library, downloads, extension repositories, backups, theming, and text-to-speech code remain in the fork. No paid feature unlocking, additional permissions, or new third-party service has been added.

## Necessary fork-specific adjustments

- Shosetsu's official APK endpoint remains disabled. Standard Nameless now uses verified GitHub releases from `tensozanghetzu-hub/Nameless`, with user-confirmed installation. See GITHUB-SETUP.md; do not install official Shosetsu APKs as updates to Nameless.
- Upstream ACRA crash reporting is not initialized. The corresponding intro page and settings toggle were removed, so this independent fork does not send crash reports to the original developer's server.
- The standard build requests `REQUEST_INSTALL_PACKAGES` for the newly requested, consent-based Nameless GitHub updater. APK download/installation needs a user tap and Android confirmation; no silent installation is implemented.
- WorkManager's automatic initializer is removed from the merged manifest, as required when the Application implements `Configuration.Provider`. Existing background novel/extension update features remain.
- Original contributors are frozen into a checked-in `ContributorsRepositoryImpl.kt`, with the Git-derived attribution snapshot in `NAMELESS-UPSTREAM-CONTRIBUTORS.json`. This preserves upstream credit without requiring a full Git history to build the source archive.
- The Kotlin/Java namespace remains `app.shosetsu.android` for internal compatibility; the installed application ID is different.

## Exact upstream provenance

- Application: https://gitlab.com/shosetsuorg/shosetsu
- Tag: `v2.5.3`
- Commit: `f1036d2b177afbcb7b6d77d8f163479d327fa131`
- Shosetsu Kotlin core library: https://gitlab.com/shosetsuorg/kotlin-lib
- Library tag: `v1.4.1`
- Library commit: `893b0d9c079d3dfbe1f1544df334685dd4357809`
- Unmodified GPL core-library source is included under `third_party/shosetsu-kotlin-lib`.

## APK configuration

- Current release version name: `2.5.3-nameless.9`
- Current release version code: `58`
- Minimum Android API: `22` (Android 5.1)
- Target and compile Android API: `36`
- Variant: `standardRelease`
- This delivery is a non-minified, non-debuggable release build, signed with a dedicated local Nameless key. Resource/code size optimization was disabled because R8 exceeded the build machine's heap. The APK remains native Android, not a website wrapper or a mockup.

## Build from this archive

This is the already-modified source. Do not apply the branding patch again.

Requirements:

1. JDK 17.
2. Android SDK platform 36 and build tools 37.0.0, with accepted SDK licenses.
3. Network access for Gradle 8.13 and the declared dependencies.
4. At least 4 GB RAM is recommended. A smaller machine can use swap and one build worker.

Set `JAVA_HOME` to your JDK 17 installation and `ANDROID_HOME` to your Android SDK. Alternatively configure the SDK in Android Studio or an untracked `local.properties` file. Then run:

```bash
chmod +x gradlew
CI_MODE=true ./gradlew :android:assembleStandardRelease --no-daemon --console=plain
```

`CI_MODE=true` disables only code/resource shrinking in the upstream build configuration. It does NOT make this release debuggable.

Output:

```
android/build/outputs/apk/standard/release/Nameless-standard-release-unsigned.apk
```

Align and sign it with your own signing key using the SDK's `zipalign` and `apksigner`, or use the dedicated private Nameless key retained separately in the workspace. The private signing key and password are intentionally excluded from the public source archive. Future in-place updates must use the same application ID and signing certificate, and a higher version code.

## Installation and testing limits

Download the APK onto an Android device, open it, and authorize that file-opening app to install unknown apps if Android asks. This is a privately signed APK, not a Play Store or F-Droid-distributed release.

Building and static APK/signature checks do not establish that every reader function or extension works on every phone. This version has not been run on a physical Android phone or full Android emulator. Targeted Compose layout tests and Room/SQLite tests run under Robolectric; see BOTTOM-PANEL.md for their scope. The original app's extension and website limitations still apply.

A separate package means separate app data. Do not uninstall or clear your original Shosetsu app while experimenting. Keep backups of any library/progress you care about.

## Licensing and attribution

Original Shosetsu copyright notices, GPL license text, contributor credit, and upstream links are retained. See `LICENSE`, original file headers, `UPSTREAM-README.md`, and the third-party core-library license. Nameless modifications and the included branding assets are supplied under GPL-3.0, consistently with this distribution. If redistributing the APK, also provide its corresponding source and license notices.

Website and extension content remains the property of its respective rightsholders. App licensing is not permission to redistribute novels or bypass a source's access requirements.

## Source-migration update

This version implements the previously unfinished Migrate source option. See [MIGRATION.md](MIGRATION.md) for the workflow, preservation rules, 26 passing automated tests, and testing limitations. Install the update over your existing Nameless app; do not uninstall or clear its data.

## Reader status-bar update

The reader viewport now reserves visible system-bar/display-cutout space natively and clips scrolling content so text cannot paint over the clock. Overlapping footer/navigation insets are not double-counted. See [STATUSBAR.md](STATUSBAR.md) for the implementation, 35-test validation, and visual-testing limitations.

## Reader bottom-panel update

The entire scaffold now uses the safe, clipped native viewport. Collapsed peek and toolbar height share a 56 dp value, so settings cannot peek into the navigation area. Expanded controls clear both system bars. Children receive toolbar spacing only, avoiding duplicate system insets. See [BOTTOM-PANEL.md](BOTTOM-PANEL.md) for 43-test validation and limits.

## GitHub updater

Bootstrap versionCode 54 added a repository-specific, verified updater and GitHub Actions workflows. See [GITHUB-SETUP.md](GITHUB-SETUP.md) for setup details and [GITHUB-UPDATES.md](GITHUB-UPDATES.md) for release validation and limitations. The signing material and GitHub workspace credentials are excluded from commits and release source bundles.

## Extension avatars

The v58 extension-avatar loader includes the v57 retired GitLab Pages repairs and adds the checked NovelRare icon for the moved Sky-mtl logo. Installed-source display prefers refreshed repository metadata. The official English catalog and its extension scripts were health-checked; website-side blocks, outages, and upstream metadata defects are documented rather than bypassed. Existing installed sources and all reading data remain intact. See [EXTENSION-ICONS.md](EXTENSION-ICONS.md) and [EXTENSION-CATALOG-TESTS.md](EXTENSION-CATALOG-TESTS.md).
