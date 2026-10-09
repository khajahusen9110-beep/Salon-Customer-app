#!/usr/bin/env bash
# Installs the release APK on the running emulator, launches it and fails if the app crashes.
set -u

APK=$(ls apk/*.apk | head -1)
AAPT="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)aapt2"
PKG=$("$AAPT" dump packagename "$APK")
echo "package=$PKG apk=$APK"

adb install --no-incremental -t "$APK" || adb install "$APK"
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 20

echo "== visible text on first screen =="
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]\+"' | head -40

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d | grep -A40 "FATAL EXCEPTION"
  echo "Smoke test FAILED: app crashed"
  exit 1
fi
if ! adb shell pidof "$PKG"; then
  echo "Smoke test FAILED: app is not running"
  exit 1
fi
echo "Smoke test passed"
