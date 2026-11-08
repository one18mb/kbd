#!/usr/bin/env sh
# 清理开发环境。默认只打印将要删除的内容；加 --yes 真正删除。
set -eu

YES=0
[ "${1:-}" = "--yes" ] && YES=1

TARGETS="
/home/fz/opt/android-sdk
/home/fz/opt/gradle
/home/fz/opt/downloads
$HOME/.gradle
$HOME/.android
$HOME/ime/android/.gradle
$HOME/ime/android/build
$HOME/ime/android/app/build
$HOME/ime/android/engine/build
$HOME/ime/third_party/libime-1.1.17
$HOME/ime/third_party/fcitx5-5.1.23
$HOME/ime/third_party/boost_1_84_0
$HOME/ime/third_party/zstd-1.5.6
$HOME/ime/third_party/data
/tmp/kbd-engine-build
/tmp/kbd-host-build
/tmp/kbd-host-selftest
"

echo "将清理以下目录："
for p in $TARGETS; do
  if [ -e "$p" ]; then
    size=$(du -sh "$p" 2>/dev/null | cut -f1)
    echo "  $p ($size)"
  fi
done

if [ "$YES" -ne 1 ]; then
  echo
  echo "以上为预览。确认清理请执行：sh scripts/clean-env.sh --yes"
  exit 0
fi

for p in $TARGETS; do
  [ -e "$p" ] && rm -rf "$p" && echo "removed $p"
done
echo "完成。环境变量未写入任何 rc 文件，关闭当前终端即可。"
