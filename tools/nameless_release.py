#!/usr/bin/env python3
"""Nameless release/source packaging. GPL-3.0, modified 2026-10-06.
No signing secret is accepted by this script; signing happens in a separate CI step.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

REPOSITORY = "tensozanghetzu-hub/Nameless"
APPLICATION_ID = "app.nameless.reader"
CERTIFICATE_SHA256 = "2ea9c211fa608f85426ce20c71a7f9fb03ef096829deb8b7e1ab851b3ce3aec8"
EXCLUDED_DIRS = {".git", ".gradle", ".idea", ".arena", ".cache", ".local", ".mypy_cache", ".next", ".nox", ".npm", ".nuxt", ".output", ".parcel-cache", ".pytest_cache", ".ruff_cache", ".svelte-kit", ".tox", ".turbo", ".venv", ".vite", "__pycache__", "build", "coverage", "dist", "node_modules", "out", "target", "release-assets", "nameless-signing", "private-signing"}
EXCLUDED_SUFFIXES = {".apk", ".aab", ".p12", ".jks", ".keystore", ".key", ".pem", ".hprof"}


def release_version(tag):
    match = re.fullmatch(r"v([1-9][0-9]{1,8})", tag)
    if not match or int(match[1]) < 54:
        raise ValueError("Use a release tag such as v55, v56, v57; minimum code is 54.")
    code = int(match[1])
    return code, f"2.5.3-nameless.{code - 49}"


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def source_archive(root, destination):
    root = Path(root).resolve()
    destination = Path(destination).resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(root.rglob("*")):
            relative = path.relative_to(root)
            if path.is_symlink() or not path.is_file() or path.resolve() == destination:
                continue
            if any(part in EXCLUDED_DIRS for part in relative.parts):
                continue
            if path.suffix.lower() in EXCLUDED_SUFFIXES or path.name in {"local.properties", "password.txt", ".DS_Store", "NAMELESS_KEYSTORE_BASE64.txt", "NAMELESS_KEYSTORE_PASSWORD.txt", "github-actions-keystore-base64.txt"}:
                continue
            archive.write(path, "Nameless-source/" + relative.as_posix())
    with zipfile.ZipFile(destination) as archive:
        if archive.testzip() is not None:
            raise ValueError("Source archive integrity check failed")
        required = {"Nameless-source/LICENSE", "Nameless-source/android/build.gradle.kts", "Nameless-source/gradle/wrapper/gradle-wrapper.jar", "Nameless-source/gradle/libs.versions.toml", "Nameless-source/third_party/shosetsu-kotlin-lib/LICENSE"}
        if not required.issubset(set(archive.namelist())):
            raise ValueError("Corresponding source is missing licenses, wrapper, library source or app build config")
    return destination


def validate_signed_apk(apk, tools, code, name):
    badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
    expected = f"package: name='{APPLICATION_ID}' versionCode='{code}' versionName='{name}'"
    if expected not in badging or "application-debuggable" in badging:
        raise ValueError("APK app/version/release flags do not match the requested release")
    if "android.permission.REQUEST_INSTALL_PACKAGES" not in badging:
        raise ValueError("Standard updater APK is missing its user-consented installation permission")
    verification = subprocess.check_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
    if CERTIFICATE_SHA256 not in verification:
        raise ValueError("Wrong signing key: this APK cannot update the existing Nameless app")
    for scheme in ["v1 scheme (JAR signing)", "v2 scheme (APK Signature Scheme v2)", "v3 scheme (APK Signature Scheme v3)"]:
        if f"Verified using {scheme}: true" not in verification:
            raise ValueError("Missing APK signature scheme: " + scheme)
    subprocess.run([str(tools / "zipalign"), "-P", "16", "-c", "4", str(apk)], check=True)
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError("APK ZIP integrity failed")
    return verification


def package_release(root, apk, tag, output, tools, event=None):
    code, name = release_version(tag)
    root, apk, output, tools = map(Path, (root, apk, output, tools))
    validate_signed_apk(apk, tools, code, name)
    output.mkdir(parents=True, exist_ok=True)
    final_apk = output / f"Nameless-{tag}.apk"
    if apk.resolve() != final_apk.resolve():
        shutil.copy2(apk, final_apk)
    if not 0 < final_apk.stat().st_size <= 128 * 1024 * 1024:
        raise ValueError("APK is outside the updater's permitted size range")
    source = source_archive(root, output / f"Nameless-{tag}-source.zip")
    notes = []
    if event:
        payload = json.loads(Path(event).read_text())
        body = payload.get("release", {}).get("body") or ""
        notes = body[:20000].splitlines()
    if not notes:
        notes = [f"Signed Nameless release {tag}. See the GitHub release page for changes."]
    manifest = {
        "schema": 1, "applicationId": APPLICATION_ID, "versionCode": code,
        "latestVersion": name, "minSdk": 22,
        "url": f"https://github.com/{REPOSITORY}/releases/download/{tag}/{final_apk.name}",
        "sha256": sha256(final_apk), "sizeBytes": final_apk.stat().st_size,
        "signingCertificateSha256": CERTIFICATE_SHA256, "releaseNotes": notes,
        "sourceUrl": f"https://github.com/{REPOSITORY}/releases/download/{tag}/{source.name}",
    }
    update = output / "nameless-update.json"
    update.write_text(json.dumps(manifest, indent=2) + "\n")
    checksum_files = [final_apk, source, update]
    reports = sorted((root / "android/build/test-results/testStandardReleaseUnitTest").glob("TEST-*.xml"))
    if reports:
        suites = []
        for report in reports:
            suite = ET.parse(report).getroot().attrib
            if any(int(suite[key]) for key in ["failures", "errors", "skipped"]):
                raise ValueError("Release test suite did not pass completely: " + suite["name"])
            suites.append({key: suite[key] for key in ["name", "tests", "failures", "errors", "skipped", "time"]})
        test_zip = output / f"Nameless-{tag}-test-results.zip"
        with zipfile.ZipFile(test_zip, "w", zipfile.ZIP_DEFLATED) as archive:
            for report in reports:
                archive.write(report, report.name)
            archive.writestr("summary.json", json.dumps({"tests": sum(int(s["tests"]) for s in suites), "suites": suites}, indent=2) + "\n")
        checksum_files.append(test_zip)
    (output / "SHA256SUMS.txt").write_text("".join(f"{sha256(p)}  {p.name}\n" for p in checksum_files))
    print(f"Validated and packaged {tag}: original certificate, signatures, alignment, APK identity, corresponding GPL source.")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    version = commands.add_parser("version")
    version.add_argument("tag")
    version.add_argument("--github-output")
    package = commands.add_parser("package")
    for key in ["root", "apk", "tag", "output", "tools"]:
        package.add_argument("--" + key, required=True)
    package.add_argument("--event")
    args = parser.parse_args()
    if args.command == "version":
        code, name = release_version(args.tag)
        result = f"code={code}\nname={name}\ntag={args.tag}\n"
        if args.github_output:
            with open(args.github_output, "a") as output:
                output.write(result)
        else:
            print(result, end="")
    else:
        package_release(args.root, args.apk, args.tag, args.output, args.tools, args.event)


if __name__ == "__main__":
    main()
