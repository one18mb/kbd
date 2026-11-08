#!/usr/bin/env sh
# 用法：. scripts/env.sh
# 只影响当前 shell，不修改任何 rc 文件。

export ANDROID_SDK_ROOT=/home/fz/opt/android-sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/26.1.10909125
export GRADLE_HOME=/home/fz/opt/gradle/gradle-8.7
export PATH="$GRADLE_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

# 本机注入的 LD_LIBRARY_PATH 会让系统 cmake/git 报 GLIBCXX 缺失，这里清空。
unset LD_LIBRARY_PATH
