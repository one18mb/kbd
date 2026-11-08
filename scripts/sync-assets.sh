#!/bin/sh
# 把生成的词典与语言模型复制到 app assets，供首次启动解压。
# 前置：scripts/host-gen-data.sh 已产出 third_party/data/sc.dict 与 sc.lm
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DATA=$ROOT/third_party/data
ASSETS=$ROOT/android/app/src/main/assets/engine

for f in sc.dict sc.lm; do
    if [ ! -f "$DATA/$f" ]; then
        echo "缺少 $DATA/$f，请先运行 scripts/host-gen-data.sh" >&2
        exit 1
    fi
done

mkdir -p "$ASSETS"
cp -f "$DATA/sc.dict" "$DATA/sc.lm" "$ASSETS/"
ls -la "$ASSETS"
