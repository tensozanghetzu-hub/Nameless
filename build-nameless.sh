#!/usr/bin/env bash
# Nameless independent fork, modified 2026-10-05. GPL-3.0.
set -euo pipefail
cd "$(dirname "$0")"
: "${JAVA_HOME:?Set JAVA_HOME to your JDK 17 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK installation}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
export CI_MODE=true
chmod +x gradlew
./gradlew :android:assembleStandardRelease --no-daemon --console=plain "$@"
printf '\nUnsigned release APK: android/build/outputs/apk/standard/release/Nameless-standard-release-unsigned.apk\n'
printf 'Align and sign the APK with your own private key before installation.\n'
