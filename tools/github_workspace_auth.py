#!/usr/bin/env python3
"""Official GitHub browser device authorization for the Nameless source workspace.
GPL-3.0, 2026-10-06. No password/token should be supplied in chat or committed.
Credentials and pending device authorization live OUTSIDE the source repository.
"""
import argparse
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

REPOSITORY = "tensozanghetzu-hub/Nameless"
# Public native-client ID from official cli/cli internal/authflow/flow.go.
# Device flow does not require or store a client secret.
CLIENT_ID = "178c6fc778ccc68e1d6a"
AUTH_DIR = Path(os.environ.get("NAMELESS_GITHUB_AUTH_DIR", str(Path.home() / "nameless-signing" / "github-auth")))
DEVICE = AUTH_DIR / "device-flow.json"
TOKEN = AUTH_DIR / "connection.json"


def private_write(path, value):
    AUTH_DIR.mkdir(parents=True, exist_ok=True)
    AUTH_DIR.chmod(0o700)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as out:
        json.dump(value, out)
        out.write("\n")
    path.chmod(0o600)


def request(url, payload=None, token=None):
    if not url.startswith("https://github.com/") and not url.startswith("https://api.github.com/"):
        raise ValueError("Authorization only contacts official GitHub HTTPS endpoints")
    headers = {"Accept": "application/json", "User-Agent": "Nameless-workspace-GitHub-link"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if payload is None else urllib.parse.urlencode(payload).encode()
    req = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"GitHub request failed (HTTP {error.code}); no credentials were printed") from None


def begin():
    value = request("https://github.com/login/device/code", {
        "client_id": CLIENT_ID, "scope": "public_repo workflow",
    })
    if "device_code" not in value:
        raise RuntimeError("GitHub could not start device authorization: " + value.get("error", "unknown response"))
    value["started_at"] = time.time()
    private_write(DEVICE, value)
    # User code is a short-lived browser confirmation code, not the private
    # device_code or access_token. Only these two public UI fields are shown.
    print("Browser URL:", value["verification_uri"])
    print("One-time code:", value["user_code"])
    print("Expires in seconds:", value["expires_in"])
    print("Requested scopes: public_repo, workflow (review permissions on GitHub)")


def complete():
    value = json.loads(DEVICE.read_text())
    if time.time() > value["started_at"] + value["expires_in"]:
        raise RuntimeError("The code expired; start a new browser authorization")
    result = request("https://github.com/login/oauth/access_token", {
        "client_id": CLIENT_ID, "device_code": value["device_code"],
        "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
    })
    if "access_token" not in result:
        raise RuntimeError("GitHub authorization status: " + result.get("error", "unknown response"))
    token = result["access_token"]
    user = request("https://api.github.com/user", token=token)
    repository = request("https://api.github.com/repos/" + REPOSITORY, token=token)
    if not repository.get("permissions", {}).get("push"):
        raise RuntimeError("Authorized account cannot push to the configured Nameless repository")
    private_write(TOKEN, {
        "access_token": token, "scope": result.get("scope", ""),
        "login": user["login"], "user_id": user["id"], "repository": REPOSITORY,
    })
    DEVICE.unlink(missing_ok=True)
    print("Authorized account:", user["login"])
    print("Repository write access verified:", REPOSITORY)
    print("Credentials saved only in the private directory outside the source tree")


def check():
    value = json.loads(TOKEN.read_text())
    repository = request("https://api.github.com/repos/" + REPOSITORY, token=value["access_token"])
    if not repository.get("permissions", {}).get("push"):
        raise RuntimeError("Repository push access is not available")
    print("Authorized account:", value["login"])
    print("Repository:", REPOSITORY)
    print("Push permission: verified")


def credential(action):
    # Used only by Git's credential protocol: never call this to display a token.
    values = dict(line.rstrip("\n").split("=", 1) for line in sys.stdin if "=" in line)
    if action != "get":
        return
    if values.get("protocol") != "https" or values.get("host") != "github.com":
        return
    if values.get("path", "").rstrip("/") not in {REPOSITORY, REPOSITORY + ".git"}:
        return
    if not TOKEN.exists():
        return
    AUTH_DIR.chmod(0o700)
    TOKEN.chmod(0o600)
    value = json.loads(TOKEN.read_text())
    if value.get("repository") != REPOSITORY:
        return
    # Git captures this stdout internally; it is never written to project files.
    sys.stdout.write("username=x-access-token\npassword=" + value["access_token"] + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["begin", "complete", "check", "git-credential"])
    parser.add_argument("action", nargs="?", default="get")
    args = parser.parse_args()
    try:
        if args.command == "git-credential":
            credential(args.action)
        else:
            globals()[args.command]()
    except Exception as error:
        # Do not dump request/response bodies; they may contain OAuth credentials.
        print(str(error), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
