#!/usr/bin/env bash
#
# Every off-device check against the game:
#
#   GeometryTest  every primitive is wound the way its normals claim, with a
#                 valid tangent basis
#   SceneTest     the whole 3D world and the skinned character build correctly,
#                 inside a phone-sized triangle budget
#   CharacterTest the rig and every animation pose, skinned the way the GPU will
#   SimTest       walking, collision, interaction prompts, the shopkeeping loop
#                 and the economy, played by an autopilot
#   shaders       every GLSL variant compiles as OpenGL ES 3.00
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
  ! -name 'LabelPainter.java' \
  ! -name 'MainActivity.java' \
  ! -name 'GameView.java' \
  ! -name 'SceneRenderer.java' \
  ! -path '*/ui/*' \
  ! -path '*/audio/*' > "$OUT/srcs.txt"

log "Compiling game code and tests"
javac -cp "$ANDROID_JAR" -d "$OUT" -nowarn \
  @"$OUT/srcs.txt" \
  "$ROOT/tools/simtest/GeometryTest.java" \
  "$ROOT/tools/simtest/SceneTest.java" \
  "$ROOT/tools/simtest/CharacterTest.java" \
  "$ROOT/tools/simtest/SimTest.java" 2>&1 | grep -v '^Picked up' || true

log "Geometry: winding, normals and tangents"
java -cp "$OUT:$ANDROID_JAR" GeometryTest 2>&1 | grep -v '^Picked up'

log "Scene: the whole world and the character"
java -cp "$OUT:$ANDROID_JAR" SceneTest 2>&1 | grep -v '^Picked up'

log "Character: the rig, skinning and every animation pose"
java -cp "$OUT:$ANDROID_JAR" CharacterTest 2>&1 | grep -v '^Picked up'

log "Simulation: walking the shop and working a shift"
java -cp "$OUT:$ANDROID_JAR" SimTest 2>&1 | grep -v '^Picked up'

if command -v glslangValidator >/dev/null 2>&1; then
  log "Shaders: compiling every variant as OpenGL ES 3.00"
  python3 "$ROOT/tools/extract_shaders.py" "$OUT/shaders" >/dev/null
  glslangValidator "$OUT"/shaders/*.vert "$OUT"/shaders/*.frag
  echo "  ok    all shader variants compile"
else
  log "Shaders: skipped (install glslang-tools to enable)"
fi

printf '\n\033[1;32mAll checks passed.\033[0m\n'
