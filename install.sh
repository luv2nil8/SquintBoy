#!/usr/bin/env bash
# Build and install release APKs to watch and phone.
# Usage: ./install.sh [watch_addr] [phone_addr]
#   watch_addr  default: 192.168.86.242:38381
#   phone_addr  default: 192.168.86.162:44657

set -euo pipefail

WATCH="${1:-192.168.86.242:38381}"
PHONE="${2:-192.168.86.162:44657}"
ADB=/home/hunter/Android/Sdk/platform-tools/adb
JAVA_HOME=/snap/android-studio/209/jbr

reconnect() {
    local addr="$1"
    echo "  connecting $addr..."
    $ADB connect "$addr" >/dev/null 2>&1 || true
    sleep 1
    # Verify it's online
    if ! $ADB -s "$addr" get-state 2>/dev/null | grep -q device; then
        echo "  retry $addr..."
        $ADB connect "$addr" >/dev/null 2>&1 || true
        sleep 2
    fi
    $ADB -s "$addr" get-state
}

install_apk() {
    local addr="$1" apk="$2" label="$3"
    echo "Installing $label → $addr"
    reconnect "$addr"
    $ADB -s "$addr" install -r "$apk"
    echo "  ✓ $label installed"
}

echo "=== Building ==="
JAVA_HOME=$JAVA_HOME ./gradlew :wear:assembleRelease :mobile:assembleRelease

WEAR_APK=wear/build/outputs/apk/release/wear-release.apk
MOBILE_APK=mobile/build/outputs/apk/release/mobile-release.apk

echo ""
echo "=== Installing ==="
install_apk "$WATCH" "$WEAR_APK"  "wear"
install_apk "$PHONE" "$MOBILE_APK" "mobile"

echo ""
echo "Done."
