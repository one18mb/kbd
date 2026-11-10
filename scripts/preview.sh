#!/bin/sh
# 宿主端验收：
#   1) ParityTests   —— 动作断言（docs/behavior.md）
#   2) LayoutPreview —— 几何/字体断言 + 输出 PNG（docs/preview/）
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC=$ROOT/android/app/src/main/java/com/kbd/ime
OUT=$ROOT/docs/preview
CLASSES=/tmp/kbd-preview-classes
FONT=$ROOT/android/app/src/main/assets/fonts/unifont.otf

mkdir -p "$OUT"
rm -rf "$CLASSES"
mkdir -p "$CLASSES"

env -u LD_LIBRARY_PATH javac -encoding UTF-8 -d "$CLASSES" \
    "$SRC/KbdData.java" \
    "$SRC/KbdLayout.java" \
    "$SRC/KbdRenderer.java" \
    "$SRC/KbdApp.java" \
    "$SRC/KbdGestures.java" \
    "$SRC/EngineBridge.java" \
    "$SRC/LibimeEngine.java" \
    "$ROOT/scripts/preview/LayoutPreview.java" \
    "$ROOT/scripts/parity/ParityTests.java"

echo "==> 动作真值表"
env -u LD_LIBRARY_PATH java -cp "$CLASSES" com.kbd.ime.ParityTests

echo "==> 渲染与几何"
env -u LD_LIBRARY_PATH java -Djava.awt.headless=true \
    -cp "$CLASSES" com.kbd.ime.LayoutPreview "$FONT" "$OUT"
