# Nameless: reader bottom toolbar and settings-panel fix

Modified 2026-10-06 in **2.5.3-nameless.4**, Android versionCode **53**. Original GPL-3.0 licensing and upstream attribution are retained.

## What was wrong

The collapsed reader sheet reserved the 56 dp toolbar height **plus** Android's navigation-bar inset. Its actual toolbar was only 56 dp tall, so that extra peek space exposed the start of the next settings row ("Paragraph spacing") underneath the navigation buttons. The expanded sheet also extended into the system bars, placing its toolbar behind the clock and its settings behind the navigation buttons.

## Fix

- The **complete reader scaffold and sheet**, not only the chapter text, now sit inside the existing native safe-area viewport.
- That viewport reserves visible system bars and display cutouts and clips content at its safe bounds. A taller collapsed sheet cannot paint its offscreen settings into the navigation area.
- Collapsed peek height and actual toolbar height use one shared **56 dp** value. Navigation-bar height is no longer added to the sheet peek.
- Expanded controls start below the status bar, and the settings list remains scrollable above the navigation bar.
- Chapter children reserve only the toolbar's inset; outer system insets are not counted a second time. Focus/fullscreen still allow reading without a visible toolbar.
- The previously fixed chapter-text/status-bar behavior and source migration are retained. The burning-book icon, application ID, signing certificate, and Room schema (11) are unchanged.

## Install

Install **Nameless-2.5.3-github-updater.apk** over your existing Nameless app. It has the same package (`app.nameless.reader`) and signing certificate, with the current bootstrap versionCode 54 (higher than the reader/panel releases 52/53). **Do not uninstall or clear app data.** Keep a library backup as a normal update precaution.

Check the collapsed toolbar: only its icon row should be visible above Android's navigation area. Expand it and check that "Paragraph spacing" is fully accessible, the toolbar clears the clock, and the final settings can scroll clear of the navigation buttons.

## Validation and limits

- Release build and vital lint passed.
- **43 automated tests passed**, with zero failures, errors, or skips: 8 new Compose sheet-layout regressions, 9 reader-inset unit tests, 21 chapter-migration planner tests, and 5 generated Room/SQLite transaction tests.
- The new layout tests run the actual production `ReaderSheetScaffold` and `ReaderViewport` under Robolectric with a representative toolbar/settings list and synthetic system insets. They check three-button and gesture navigation, collapsed settings invisibility, expanded top/bottom bounds, scrolling to the last setting and collapsing again, focus/no double padding, landscape cutouts, fullscreen, and system-bar visibility changes.
- APK package/version/release flags, ZIP integrity, 16 KB alignment, and v1/v2/v3 signatures verified. Its signing certificate matches the previous Nameless release: SHA-256 `2ea9c211fa608f85426ce20c71a7f9fb03ef096829deb8b7e1ab851b3ce3aec8`.

The user confirmed that the previous v52 chapter/status-bar fix works on their phone. **This v53 update has not yet been run on a physical phone or full Android emulator.** The targeted Robolectric Compose tests do not constitute a full-app or device visual test.

## Corresponding source and build

Matching GPL source: **Nameless-GitHub-ready-source.zip**. Application/core-library source, licenses, contributor attribution, branding masters, build scripts, and all tests are included; private signing material is excluded.

With JDK 17, Android SDK platform 36, and build tools 37.0.0 configured:

```bash
CI_MODE=true ./gradlew :android:testStandardReleaseUnitTest --no-daemon --console=plain
CI_MODE=true ./gradlew :android:assembleStandardRelease --no-daemon --console=plain
```

Use separate Gradle invocations on memory-limited machines. Robolectric and Compose UI-test dependencies are test-only and do not ship in the APK. This is a non-minified, non-debuggable native Android release. See [NAMELESS.md](NAMELESS.md) for provenance/build/signing instructions and [MIGRATION.md](MIGRATION.md) for migration usage and preservation rules.
