# Nameless: reader status-bar overlap fix

Modified 2026-10-06 in **2.5.3-nameless.3**, Android versionCode **52**. Original GPL-3.0 licensing and upstream attribution are retained.

The user confirmed that the v52 fix works on their phone. It is retained and refined in the current v53 bottom-panel update; see [BOTTOM-PANEL.md](BOTTOM-PANEL.md).

## Change

Chapter text could draw behind the Android status-bar clock. The reader uses an edge-to-edge window, but system-bar spacing was previously implemented as HTML body padding. That padding belongs to the scrolling document: it can scroll away and can be overridden by novel/user CSS.

The fix reserves system-bar and display-cutout space around the **native scrolling viewport** and clips chapter content to that viewport. It covers loaded chapters, loading/error screens, and chapter dividers. Native spacing follows visible system bars, including landscape/RTL cutouts. Overlapping reserved padding is combined per edge without double counting. In v53, the complete scaffold reserves system insets once, and chapter children reserve only toolbar padding. Duplicate system padding in the HTML stylesheet is removed; ordinary reader text margins and user CSS remain available. HTML/progress flows are remembered by chapter rather than by system insets.

The app identity, signing certificate, Room schema (11), burning-book icon, and previously implemented source migration are unchanged.

## Install

Install the current **Nameless-2.5.3-github-updater.apk** over your existing Nameless installation. It has the same package (`app.nameless.reader`) and signing certificate, with a higher versionCode. **Do not uninstall or clear app data.** Keep a library backup as a normal precaution when installing an update.

## Original v52 validation

- Release build and vital lint passed.
- **35 automated tests passed, zero failures, errors, or skips:** 9 reader-inset unit tests, 21 chapter-migration planner tests, and 5 real generated Room/SQLite transaction tests under Robolectric.
- Reader-inset tests cover portrait status-bar spacing, navigation/footer overlap, hidden toolbar, fullscreen zero insets, left/right landscape cutouts, RTL edge resolution, visibility changes, and fractional insets.
- APK package/version/release flags, ZIP integrity, 16 KB alignment, and v1/v2/v3 signatures verified. Signing certificate SHA-256: `2ea9c211fa608f85426ce20c71a7f9fb03ef096829deb8b7e1ab851b3ce3aec8`.

No physical-phone or full Android-emulator visual test was performed before the v52 delivery; the user subsequently confirmed it works. Current v53 validation is detailed in BOTTOM-PANEL.md. The padding tests validate inset calculations, not an interactive scrolling screenshot. After installing, reopen the chapter from your screenshot and scroll in both directions; its title and text should remain below the clock when the status bar is visible. Also try landscape and your normal fullscreen/toolbar toggles. Report a screenshot if overlap remains on your device.

## Source and build

Matching GPL source: **Nameless-GitHub-ready-source.zip**. It contains the application, unmodified corresponding Shosetsu Kotlin core-library source, original notices/contributor attribution, branding masters, build instructions, and all test sources. No private signing material is included.

With JDK 17, Android SDK platform 36, and build tools 37.0.0 configured:

```bash
CI_MODE=true ./gradlew :android:testStandardReleaseUnitTest --no-daemon --console=plain
CI_MODE=true ./gradlew :android:assembleStandardRelease --no-daemon --console=plain
```

Use separate Gradle invocations on memory-limited machines. This is a non-minified, non-debuggable native Android release, not a website wrapper. Follow [NAMELESS.md](NAMELESS.md) for provenance and signing instructions, and [MIGRATION.md](MIGRATION.md) for source-migration usage and preservation rules.
