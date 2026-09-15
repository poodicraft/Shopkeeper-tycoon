#!/usr/bin/env bash
#
# Runs every off-device check against the game:
#
#   GeometryTest  every primitive's triangles face the way their normals claim
#   SceneTest     the whole 3D scene builds, with a sane triangle budget
#   SimTest       navigation, the customer state machine and the economy
#   shaders       the GLSL compiles as OpenGL ES 1.00 (needs glslangValidator)
#
# None of this needs a device or an emulator.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main/java"
OUT="$ROOT/build/tests"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"

log() { printf '\n\033[1;36m==>\033[0m %s\n' "$*"; }

rm -rf "$OUT"
mkdir -p "$OUT"

# Everything except the classes that genuinely need a live Android runtime.
find "$SRC" -name '*.java' \
  ! -name 'SaveManager.java' \
  ! -name 'MainActivity.java' \
  ! -name 'GameView.java' \
  ! -path '*/ui/*' \
  ! -path '*/audio/*' > "$OUT/srcs.txt"

log "Compiling game code and tests"
javac -cp "$ANDROID_JAR" -d "$OUT" -nowarn \
  @"$OUT/srcs.txt" \
  "$ROOT/tools/simtest/GeometryTest.java" \
  "$ROOT/tools/simtest/SceneTest.java" \
  "$ROOT/tools/simtest/SimTest.java" 2>&1 | grep -v '^Picked up' || true

log "Geometry: triangle winding and normals"
java -cp "$OUT:$ANDROID_JAR" GeometryTest 2>&1 | grep -v '^Picked up'

log "Scene: full asset build"
java -cp "$OUT:$ANDROID_JAR" SceneTest 2>&1 | grep -v '^Picked up'

log "Simulation: navigation, customers and the economy"
java -cp "$OUT:$ANDROID_JAR" SimTest 2>&1 | grep -v '^Picked up'

if command -v glslangValidator >/dev/null 2>&1; then
  log "Shaders: compiling as OpenGL ES 1.00"
  python3 "$ROOT/tools/extract_shaders.py" "$OUT/shaders" >/dev/null
  glslangValidator "$OUT/shaders/shader.vert" "$OUT/shaders/shader.frag"
  echo "  ok    both shaders compile"
else
  log "Shaders: skipped (install glslang-tools to enable)"
fi

printf '\n\033[1;32mAll checks passed.\033[0m\n'
