#!/bin/sh
# 独立编译 Android 引擎（arm64-v8a），不依赖 Gradle。
# 产物：build/engine/libkbd_engine.so
# 用法：sh scripts/build-engine.sh [build-dir]
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/env.sh"

BUILD=${1:-/tmp/kbd-engine-build}
CMAKE=$ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake
NINJA=$ANDROID_SDK_ROOT/cmake/3.22.1/bin/ninja

if [ ! -x "$CMAKE" ]; then
    echo "缺少 CMake 3.22.1：$CMAKE" >&2
    echo "先执行 docs/dev-env.md 第 3 节的 sdkmanager 安装命令。" >&2
    exit 1
fi

env -u LD_LIBRARY_PATH "$CMAKE" -S "$ROOT/android/engine" -B "$BUILD" -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release

env -u LD_LIBRARY_PATH "$NINJA" -C "$BUILD" -j 8

ls -la "$BUILD/libkbd_engine.so"
