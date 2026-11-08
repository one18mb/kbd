#!/bin/sh
# 宿主机（x86_64）编译并运行 libime 联调自检：input -> candidates -> select。
# 前置：先执行 scripts/host-gen-data.sh 生成 third_party/data/sc.dict 与 sc.lm。
# 用法：sh scripts/host-selftest.sh
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
TP=$ROOT/third_party
L=$TP/libime-1.1.17/src
F=$TP/fcitx5-5.1.23/src/lib
Z=$TP/zstd-1.5.6
K=$L/libime/core/kenlm
DATA=$TP/data
BUILD=${BUILD:-/tmp/kbd-host-build}
OUT=$BUILD/pipeline

mkdir -p "$BUILD"

INC="-I$ROOT/android/engine/include -I$L -I$TP/boost_1_84_0 -I$F -I$Z/lib -I$Z/lib/common -I$K"

CORE="$L/libime/core/datrie.cpp $L/libime/core/dictionary.cpp $L/libime/core/decoder.cpp \
$L/libime/core/languagemodel.cpp $L/libime/core/inputbuffer.cpp $L/libime/core/lattice.cpp \
$L/libime/core/userlanguagemodel.cpp $L/libime/core/historybigram.cpp \
$L/libime/core/segmentgraph.cpp $L/libime/core/utils.cpp $L/libime/core/prediction.cpp \
$L/libime/core/triedictionary.cpp"

PINYIN="$L/libime/pinyin/pinyincontext.cpp $L/libime/pinyin/pinyindata.cpp \
$L/libime/pinyin/pinyindecoder.cpp $L/libime/pinyin/pinyindictionary.cpp \
$L/libime/pinyin/pinyinencoder.cpp $L/libime/pinyin/pinyinime.cpp \
$L/libime/pinyin/pinyinmatchstate.cpp $L/libime/pinyin/shuangpinprofile.cpp \
$L/libime/pinyin/pinyinprediction.cpp $L/libime/pinyin/pinyincorrectionprofile.cpp"

KENLM=$(find "$K/lm" "$K/util" "$K/util/double-conversion" -maxdepth 1 -name '*.cc' \
    ! -name '*_test.cc' ! -name '*_main.cc')

UTILS="$F/fcitx-utils/stringutils.cpp $F/fcitx-utils/utf8.cpp $F/fcitx-utils/cutf8.cpp \
$F/fcitx-utils/element.cpp $F/fcitx-utils/connectableobject.cpp $F/fcitx-utils/log.cpp \
$F/fcitx-utils/misc.cpp $F/fcitx-utils/fs.cpp $F/fcitx-utils/unixfd.cpp \
$F/fcitx-utils/environ.cpp $F/fcitx-utils/inputbuffer.cpp $F/fcitx-utils/fdstreambuf.cpp"

CC=${CC:-g++}
ENV="env -u LD_LIBRARY_PATH"

if [ ! -f "$DATA/sc.dict" ] || [ ! -f "$DATA/sc.lm" ]; then
    echo "缺少 $DATA/sc.dict 或 sc.lm，请先运行 scripts/host-gen-data.sh" >&2
    exit 1
fi

echo "==> 构建 pipeline"
$ENV $CC -std=c++20 -O2 -DKENLM_MAX_ORDER=3 $INC \
    "$ROOT/android/engine/test/pipeline.cpp" $CORE $PINYIN $KENLM $UTILS \
    "$Z/lib/libzstd.a" -o "$OUT"

echo "==> 运行"
cd "$DATA"
HOME=${HOME:-/tmp} $ENV "$OUT" sc.dict sc.lm
