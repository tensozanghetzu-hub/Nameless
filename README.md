# Nameless

An independent Shosetsu-based Android novel reader, renamed **Nameless** with a **burning-book icon**. Modified 2026-10-06.

- Installed app ID: `app.nameless.reader`
- Base: Shosetsu v2.5.3, commit `f1036d2b177afbcb7b6d77d8f163479d327fa131`
- Version: `2.5.3-nameless.5`
- Android 5.1+ (API 22)
- Native Android app; separate installation from the original Shosetsu.

See [NAMELESS.md](NAMELESS.md) for the complete change list, provenance, build instructions, licensing, and testing limitations. The original upstream README is preserved in [UPSTREAM-README.md](UPSTREAM-README.md).

## Build

With JDK 17 and the Android SDK configured:

```bash
chmod +x build-nameless.sh
./build-nameless.sh
```

The output is an unsigned release APK; align and sign it before installation. This delivery's signed APK uses a dedicated private Nameless signing key.

## License

GPL-3.0; original notices and attribution are retained. This is not an official release and is not affiliated with Shosetsu's maintainers. Corresponding source for the GPL Shosetsu core library is included under `third_party/shosetsu-kotlin-lib`.

## Source-migration update

This version implements the previously unfinished Migrate source option. See [MIGRATION.md](MIGRATION.md) for the workflow, preservation rules, 26 passing automated tests, and testing limitations. Install the update over your existing Nameless app; do not uninstall or clear its data.

## Reader status-bar update

This version fixes chapter text drawing behind the status-bar clock by reserving and clipping the native reader viewport. See [STATUSBAR.md](STATUSBAR.md) for details, the 35 passing automated tests, and device-testing limits.

## Bottom toolbar / settings-panel update

The collapsed panel no longer reveals settings under Android navigation buttons. The entire reader scaffold now reserves and clips to the safe window, including the expanded settings panel. See [BOTTOM-PANEL.md](BOTTOM-PANEL.md) for the 43 passing automated tests and device-testing limits.

## GitHub app updates

This build connects to [tensozanghetzu-hub/Nameless](https://github.com/tensozanghetzu-hub/Nameless) stable releases with verified, user-confirmed in-app APK updates. See [GITHUB-SETUP.md](GITHUB-SETUP.md) for one-time source/secret setup and [GITHUB-UPDATES.md](GITHUB-UPDATES.md) for implementation and validation. Native changes still require a new APK; the app handles discovery/download, not hot code injection.

## Workspace Git connection

The source workspace can commit/push to this repository after secure GitHub browser authorization. See [GITHUB-WORKSPACE.md](GITHUB-WORKSPACE.md). The connection excludes private signing/authentication files and does not automatically publish phone updates.

## English-only available sources

Release v56 makes the new-source catalog English-only while retaining installed sources, novel records, downloads and reading progress. See [ENGLISH-CATALOG.md](ENGLISH-CATALOG.md).

## Extension avatars

The v57 loader repairs the retired official GitLab Pages icon URLs and tries same-site favicon fallback for broken image hosts. Installed-source records and all reading data are preserved. See [EXTENSION-ICONS.md](EXTENSION-ICONS.md).
