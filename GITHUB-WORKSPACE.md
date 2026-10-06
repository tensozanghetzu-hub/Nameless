# Nameless workspace ↔ GitHub

This source folder is connected to:

**https://github.com/tensozanghetzu-hub/Nameless**

Only `nameless-source/` is a Git repository. The surrounding workspace, APK deliveries, uploads and private `nameless-signing/` directory are NOT part of it. Original GitHub source history is preserved; pushes are ordinary fast-forward updates, not forced replacements.

## Working from this workspace

Local remote/credential settings can be reset between workspace sessions. Restore the public connection with:

```bash
bash tools/link-github-workspace.sh
```

Then inspect changes and push a deliberate commit:

```bash
git status
git diff
# Run the relevant tests before committing changed application code.
git add <specific-source-files>
git commit -m "Describe the change"
git push origin main
```

The Git credential helper uses private authorization saved OUTSIDE the source tree and returns it only for `tensozanghetzu-hub/Nameless` over GitHub HTTPS. Do not print or commit the authorization file. If GitHub revokes/expires the authorization, reconnect through the official device browser flow using `tools/github_workspace_auth.py begin`, approve the displayed code at https://github.com/login/device, then run `complete`. Do not paste account passwords or tokens into chat.

This is Git synchronization, **not automatic file mirroring**: workspace edits do not reach GitHub until committed/pushed, and GitHub edits need a fetch/pull before working on them. This workspace connection does not create an Arena-wide GitHub account integration.

## App updates are still release-based

- A source push runs the `Nameless checks` workflow; it does not publish a phone update.
- Publishing a stable tag `v55`, `v56`, etc. runs the signed-release workflow.
- The signed-release workflow reads the retained signing material only from encrypted repository Actions secrets. Local credential/key files remain outside the source tree and source archive.
- Publishing a release is separate from a source push; only the stable release workflow uploads a validated, signed APK. The owner still needs to install/check the resulting release on a physical device.

## Keep private material private

The retained signing key/password and GitHub authorization are under the PRIVATE `nameless-signing/` workspace directory, outside Git. Never upload that directory, authorization files, passwords or prepared signing-secret values as source files/release assets. Public source ZIP generation and Git ignore rules exclude signing material and generated builds.

GitHub authorization requested public-repository/workflow access. The local helper is restricted to this one repository, but GitHub's OAuth permission scope can cover other public repositories; protect your account and authorization. To revoke access, use **GitHub → Settings → Applications → Authorized OAuth Apps → GitHub CLI**. Removing local private connection credentials also stops this workspace from authenticating.

The original Shosetsu licensing, contributors, core-library source, migration and reader fixes are unchanged. No native app code was changed merely to connect GitHub.
