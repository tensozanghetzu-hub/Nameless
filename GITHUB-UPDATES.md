# Nameless GitHub updater — 2.5.3-nameless.5

Modified 2026-10-06. Bootstrap versionCode **54**. Target repository: **tensozanghetzu-hub/Nameless**. Original GPL-3.0 licensing, attribution and corresponding core-library source are retained.

## Implementation

- Standard release updater reads stable GitHub Releases from this repository only. The original Shosetsu update endpoint is no longer used for standard Nameless updates.
- GitHub release tags have the strict format `v55`, `v56`, etc., with the number becoming Android versionCode. Ready release assets include `Nameless-vNN.apk` and `nameless-update.json`.
- Empty repositories (release API 404), prereleases, same/older versions and releases still being built are not offered as newer ready updates. API/network failures are reported rather than claimed as successful checks.
- Existing automatic startup/periodic check settings remain in use. Checks fetch metadata only. The dedicated **More → About → Check for app update** screen shows status, release notes, a user-initiated download button and an installation button, even without update-notification permission.
- Mandatory checks cover trusted asset URL, manifest/tag consistency, download size/SHA-256, APK package, newer version, device requirement and the installed/original signing certificate. SHA-256 and APK facts are rechecked just before handing the cached file to Android. Downloads are bounded; partial or failed files are removed.
- APKs are kept in app-private cache and passed through the existing FileProvider to Android's installer. The standard build now requests `REQUEST_INSTALL_PACKAGES` solely for this user-confirmed update flow. There is no silent-install mechanism, private repository token, dynamic code loading or remote command execution.
- Original ACRA crash uploads remain disabled. Room schema 11, library data, migration, burning-book icon and both reader fixes are unchanged.

## GitHub automation

- `.github/workflows/ci.yml`: test tooling/Android sources and build unsigned releases for main pushes/pull requests.
- `.github/workflows/release.yml`: on a stable published release, test/build the exact tag, use the retained keystore from two encrypted Actions secrets, check signer/package/version/signatures/alignment, and upload the APK plus corresponding GPL source, checksums and manifest. A workflow-dispatch option retries an existing tag.
- Third-party Actions are pinned to full commit SHAs. The signing key is decoded into a private runner-temporary file, never source/release assets, and removed after signing. No signing secrets are passed to the ordinary CI workflow.
- Release asset generation and public source filtering live in `tools/nameless_release.py`; version properties avoid manual Gradle edits for each release. New stable version tags must increase.

Follow [GITHUB-SETUP.md](GITHUB-SETUP.md). The source has been imported by its owner, and this workspace now has a repository-scoped Git credential helper after official GitHub browser authorization. See GITHUB-WORKSPACE.md. This connection step does not configure signing secrets, create tags or publish a release; the owner still controls release setup/publication.

## Validation scope

- **74 Android automated tests passed**, zero failures/errors/skips: existing 43 migration/reader regressions, 23 updater policy/cache/security tests, and 8 real datasource tests with canned HTTPS responses/no live downloads.
- **8 Python release/source-packaging tests passed**, including version validation, secret exclusion, symlink exclusion and required GPL/source files.
- GitHub workflow YAML structure and actionlint validation passed locally.
- Release/signing/package/source-archive checks are recorded with the delivered build.

The current public repository metadata and absent-release state were checked live. The release workflow has not run on GitHub from this session, and end-to-end phone installation of a GitHub-hosted update has not been tested. The previous status-bar fix was confirmed by the user; that does not constitute updater/device validation. Android/package-installer and repository-provider behavior must be checked after the owner completes setup.
