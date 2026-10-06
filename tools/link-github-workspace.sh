#!/usr/bin/env bash
# Nameless workspace -> owned GitHub repository. GPL-3.0, 2026-10-06.
# This restores the public remote only. It does not store a token or push files.
set -euo pipefail
cd "$(dirname "$0")/.."
remote='https://github.com/tensozanghetzu-hub/Nameless.git'
if ! git rev-parse --show-toplevel >/dev/null 2>&1; then
  git init --initial-branch=main
fi
root="$(git rev-parse --show-toplevel)"
if [ "$root" != "$PWD" ]; then
  echo 'Refusing to connect a parent workspace: run this only inside nameless-source.' >&2
  exit 1
fi
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$remote"
else
  git remote add origin "$remote"
fi
# Local Git config may be refreshed on a later workspace session. Credential
# values stay outside this source tree and are returned only to Git for THIS repo.
git config --local credential.useHttpPath true
git config --local 'credential.https://github.com/tensozanghetzu-hub/Nameless.git.helper' "!python3 '$PWD/tools/github_workspace_auth.py' git-credential"
printf 'Source repository linked to %s\n' "$remote"
printf 'Authentication and an explicit commit/push are still required. Never add the signing folder.\n'
