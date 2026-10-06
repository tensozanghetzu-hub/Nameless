# Connect Nameless to your GitHub releases

Repository: **https://github.com/tensozanghetzu-hub/Nameless**  
Prepared 2026-10-06. The repository was initially public and empty, then its owner imported the full source. The workspace-to-GitHub source connection is described in GITHUB-WORKSPACE.md. Signing secrets and release publication are separate setup steps.

## What this provides

- A one-time bootstrap APK: **Nameless-2.5.3-github-updater.apk**, versionCode **54**, version name `2.5.3-nameless.5`.
- Nameless checks THIS repository's stable GitHub releases on launch (when the existing startup-update setting is enabled) and on its existing periodic schedule. It downloads only small update metadata during checks.
- **More → About → Check for app update** opens a visible updater screen. APK downloads require a tap. After download, **Install verified update** opens Android's normal consent-based installer.
- SHA-256, package ID, higher versionCode, Android compatibility, and the original signing certificate are checked before an APK is offered for installation. Draft/prerelease, wrong-source, wrong-key, malformed or older updates are rejected.
- GitHub Actions tests/builds source pushes. Only publishing a stable release triggers a signed update. Each release includes its matching GPL source archive.

This is not hot code injection or silent installation. Native changes still require a newly built APK, but the app handles discovering/downloading it. Keep a library backup as a normal update precaution.

## 1. Install the bootstrap once

Install `Nameless-2.5.3-github-updater.apk` over your existing Nameless. **Do not uninstall or clear app data.** The package and signing certificate are unchanged; the previous migration and both reader fixes remain.

Until GitHub has a completed signed release, the app correctly says that no newer ready stable release is available. Merely linking an empty repository does not create an update.

## 2. Put the repository-ready SOURCE on GitHub

**Already completed for this repository:** its owner imported the source, and this workspace is connected to that existing history. The instructions below remain useful for a fresh clone/import. See GITHUB-WORKSPACE.md for future workspace pushes.

Use **Nameless-GitHub-ready-source.zip**, not the APK and not the private signing folder. Extract it; its `Nameless-source` directory contains the project.

### Easiest on a computer: GitHub Desktop

1. Sign in to GitHub Desktop and clone `tensozanghetzu-hub/Nameless`.
2. Copy the **contents** of the extracted `Nameless-source` folder into the cloned repository. Include hidden `.github` and `.gitignore` files. Do not copy an extra outer `Nameless-source` folder.
3. Commit with a message such as `Import Nameless with GitHub updater`, then **Push origin**.
4. Check GitHub: `settings.gradle.kts`, `android/`, `gradle/`, `tools/` and `.github/workflows/` must be at the repository root. The **Actions** tab should show `Nameless checks`.

### Browser-only alternative: GitHub Codespaces

An empty repository first needs a commit: create a temporary `README.md` through GitHub's web interface and commit it to `main`. Then **Code → Codespaces → Create codespace on main**. GitHub account availability/quotas apply.

Upload `Nameless-GitHub-ready-source.zip` into the Codespace's repository folder using its file explorer. In that Codespace's terminal, run:

```bash
unzip Nameless-GitHub-ready-source.zip
cp -a Nameless-source/. .
rm -rf Nameless-source
rm Nameless-GitHub-ready-source.zip
chmod +x gradlew build-nameless.sh
git add .
git commit -m "Import Nameless with GitHub updater"
git push
```

The ZIP excludes Git state and secrets, so it does not overwrite your repository's `.git`. Never upload the private signing folder to a Codespace/repository as project source. If GitHub refuses workflow-file pushes due to authentication permissions, use GitHub's own login/authorization flow or GitHub Desktop; do not paste personal access tokens into chat.

GitHub's web uploader has file-count limits, so uploading the whole source tree directly through that interface is not recommended. Uploading only the ZIP to the repository does NOT install the workflows; the source must be extracted at the repository root.

## 3. Add two PRIVATE Actions secrets

Open **repository → Settings → Secrets and variables → Actions → New repository secret**.

| Secret NAME | Secret VALUE |
|---|---|
| `NAMELESS_KEYSTORE_BASE64` | Base64 of the EXISTING `nameless.p12` signing keystore. |
| `NAMELESS_KEYSTORE_PASSWORD` | Its existing keystore password. |

The existing workspace has these ready-to-copy value files under **`nameless-signing/`**:

- `NAMELESS_KEYSTORE_BASE64.txt`
- `NAMELESS_KEYSTORE_PASSWORD.txt`

Copy each value into its matching encrypted Actions secret. **Never commit these text files, `nameless.p12`, `password.txt`, or the signing folder. Do not post them in issues, release assets, screenshots or chat.** They are not in the public source ZIP. Do not generate a different key: Android would reject updates over the installed app. The fixed alias is `nameless`.

Expected signing certificate SHA-256:

```
2ea9c211fa608f85426ce20c71a7f9fb03ef096829deb8b7e1ab851b3ce3aec8
```

Protect your GitHub account with two-factor authentication. Limit repository/workflow write access; someone able to alter a workflow that uses signing secrets may be able to steal them. Consider protected branches/tags and a release environment requiring your approval. Keep an offline backup of the original keystore/password.

## 4. Publish your first ready update

After source is uploaded, the checks pass and both secrets are configured:

1. Open **Releases → Draft a new release**.
2. Create tag **`v55`** targeting **main**. Title can be `Nameless v55`; add your change notes.
3. Leave **pre-release unchecked**, then **Publish release**.
4. Open **Actions → Publish signed Nameless update** and wait for success.
5. The workflow attaches `Nameless-v55.apk`, `Nameless-v55-source.zip`, `nameless-update.json` and `SHA256SUMS.txt` to the release. The APK uses versionCode **55**, which updates the bootstrap's **54**.
6. On your phone, use **More → About → Check for app update → Download update → Install verified update**. If Android asks, allow **Nameless** to install unknown apps, return, and tap Install again. Android still asks you to confirm the update.

A release appears on GitHub before its build finishes. Nameless waits for the completed manifest/assets rather than offering a half-built release. Missing secrets, test failures, wrong signing keys or invalid versions stop publication of an installable update. A failed job can leave the release page without assets: fix the cause and rerun the job, or use the workflow's **Run workflow** with that existing tag.

## Later changes

Push edited source to `main`; CI checks it without publishing an APK. When ready, publish **v56**, then **v57**, etc. Use a higher numerical tag every time. Do not edit/reuse an old release tag or assume a commit changes installed phone code.

Tags directly define Android versionCode. Version names are generated as `2.5.3-nameless.(versionCode - 49)`: v55 → `.6`, v56 → `.7`. The release workflow passes version properties to Gradle; you do not need to bump the hardcoded default for each GitHub release. The workflow rejects publishing below an already newer stable release.

## Testing limits and troubleshooting

The local build/test results are supplied with the delivery. Public repository discovery was checked; the release API currently has no published update. GitHub-hosted workflows and a real phone self-update have NOT been exercised from this session because this connection step does not run a signed release or an Android installation. The source import is already present; verify signing-secret setup and publish a ready release next.

- **No newer ready release:** make sure the release is stable, has the vNN tag and all assets, and has a higher code than installed.
- **Build fails:** inspect Actions logs; configure the two secret NAMES exactly, keep the original key, and include the root `gradle/` directory/workflows.
- **Wrong key:** use the retained original Nameless signing material, not a new keystore.
- **Installer blocked:** allow Nameless as an installation source; enterprise/device policy may prevent sideloading.
- **Offline/API rate limit:** retry later. No GitHub access token is embedded in the app.

Original Shosetsu GPL licensing, contributors and core-library source are retained. App-update requests now go to GitHub; source/extension access still follows each website's own requirements. Upstream Shosetsu's self-updater and crash-report uploads are not re-enabled.
