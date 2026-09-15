#!/usr/bin/env bash
#
# Builds Shopkeeper Tycoon into a signed, installable APK without Gradle or
# Android Studio. It drives the Android build tools directly:
#
#   javac -> dx -> aapt package -> zipalign -> apksigner
#
# Everything it needs is available from Debian/Ubuntu:
#   sudo apt-get install aapt dalvik-exchange zipalign apksigner \
#                        android-sdk-platform-23 openjdk-17-jdk-headless
#
# Override any tool or path with the matching environment variable.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$ROOT/app/src/main/java"
RES="$ROOT/app/src/main/res"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
OUT="$ROOT/build"
APK_NAME="${APK_NAME:-shopkeeper-tycoon}"

AAPT="${AAPT:-aapt}"
DX="${DX:-dalvik-exchange}"
ZIPALIGN="${ZIPALIGN:-zipalign}"
APKSIGNER="${APKSIGNER:-apksigner}"
JAVAC="${JAVAC:-javac}"
KEYTOOL="${KEYTOOL:-keytool}"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"

KEYSTORE="${KEYSTORE:-$ROOT/keystore/debug.keystore}"
KEY_ALIAS="${KEY_ALIAS:-shopkeeper}"
KEY_PASS="${KEY_PASS:-android}"
STORE_PASS="${STORE_PASS:-android}"

log()  { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mError:\033[0m %s\n' "$*" >&2; exit 1; }

require() {
  command -v "$1" >/dev/null 2>&1 || fail "'$1' not found. $2"
}

log "Checking toolchain"
require "$JAVAC"     "Install a JDK (openjdk-17-jdk-headless)."
require "$AAPT"      "Install the 'aapt' package."
require "$DX"        "Install the 'dalvik-exchange' package (provides dx)."
require "$ZIPALIGN"  "Install the 'zipalign' package."
require "$APKSIGNER" "Install the 'apksigner' package."
[ -f "$ANDROID_JAR" ] || fail "android.jar not found at $ANDROID_JAR. Install 'android-sdk-platform-23' or set ANDROID_JAR."

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

log "Compiling Java sources"
find "$SRC" -name '*.java' > "$OUT/sources.txt"
SOURCE_COUNT=$(wc -l < "$OUT/sources.txt")
[ "$SOURCE_COUNT" -gt 0 ] || fail "No Java sources found under $SRC"
"$JAVAC" \
  -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR" \
  -encoding UTF-8 \
  -nowarn \
  -d "$OUT/classes" \
  @"$OUT/sources.txt" 2>&1 | grep -v 'warning: \[options\]' | grep -v '^Picked up' || true
[ -d "$OUT/classes/com" ] || fail "Compilation produced no classes"
log "Compiled $SOURCE_COUNT source files"

log "Converting bytecode to Dalvik (dex)"
"$DX" --dex --output="$OUT/dex/classes.dex" "$OUT/classes" 2>&1 | grep -v '^Picked up' || true
[ -f "$OUT/dex/classes.dex" ] || fail "dex conversion failed"

log "Packaging resources"
"$AAPT" package -f \
  -M "$MANIFEST" \
  -S "$RES" \
  -I "$ANDROID_JAR" \
  -F "$OUT/$APK_NAME.unaligned.apk"

log "Adding classes.dex"
( cd "$OUT/dex" && "$AAPT" add -f "$OUT/$APK_NAME.unaligned.apk" classes.dex >/dev/null )

log "Aligning"
"$ZIPALIGN" -f -p 4 "$OUT/$APK_NAME.unaligned.apk" "$OUT/$APK_NAME.aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  log "Creating signing key at $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  "$KEYTOOL" -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Shopkeeper Tycoon, OU=Games, O=Poodicraft, L=, ST=, C=" 2>&1 | grep -v '^Picked up' || true
fi

log "Signing"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$STORE_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --ks-key-alias "$KEY_ALIAS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --out "$OUT/$APK_NAME.apk" \
  "$OUT/$APK_NAME.aligned.apk" 2>&1 | grep -v '^Picked up' || true

log "Verifying"
"$APKSIGNER" verify --print-certs "$OUT/$APK_NAME.apk" 2>&1 | grep -v '^Picked up' | grep -v '^WARNING' || true

rm -f "$OUT/$APK_NAME.unaligned.apk" "$OUT/$APK_NAME.aligned.apk" "$OUT/sources.txt"

SIZE=$(du -h "$OUT/$APK_NAME.apk" | cut -f1)
printf '\n\033[1;32mBuilt %s (%s)\033[0m\n' "$OUT/$APK_NAME.apk" "$SIZE"
printf 'Install with:  adb install -r %s\n' "$OUT/$APK_NAME.apk"
