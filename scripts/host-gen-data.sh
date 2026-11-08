#!/bin/sh
# 在宿主机生成 Android 需要的 libime 数据：
#   sc.dict  <- dict_sc.txt（PinyinDictionary 二进制格式）
#   sc.lm    <- lm_sc.arpa（kenlm trie 二进制）
#
# 依赖：third_party 下已解压 libime / fcitx5 / boost / zstd，且 third_party/data 已解压原始数据。
# 用法：sh scripts/host-gen-data.sh
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
TP=$ROOT/third_party
L=$TP/libime-1.1.17/src
T=$TP/libime-1.1.17/tools
F=$TP/fcitx5-5.1.23/src/lib
Z=$TP/zstd-1.5.6
K=$L/libime/core/kenlm
DATA=$TP/data
BUILD=${BUILD:-/tmp/kbd-host-build}

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
CFLAGS="-std=c++20 -O2 -DKENLM_MAX_ORDER=3"
ENV="env -u LD_LIBRARY_PATH"

# zstd 静态库（若未构建）
if [ ! -f "$Z/lib/libzstd.a" ]; then
    $ENV make -C "$Z" -j 8 lib-release >/dev/null
fi

echo "==> 构建 libime_pinyindict"
$ENV $CC $CFLAGS $INC "$T/libime_pinyindict.cpp" $CORE $PINYIN $KENLM $UTILS \
    "$Z/lib/libzstd.a" -o "$BUILD/pinyindict"

echo "==> 构建 kenlm build_binary"
$ENV $CC $CFLAGS -DNDEBUG -I"$K" -I"$Z/lib" -I"$Z/lib/common" \
    "$K/lm/build_binary_main.cc" $KENLM "$Z/lib/libzstd.a" -o "$BUILD/build_binary"

echo "==> 生成 sc.dict"
$ENV "$BUILD/pinyindict" "$DATA/dict_sc.txt" "$DATA/sc.dict"

echo "==> 生成 sc.lm"
$ENV "$BUILD/build_binary" -s -a 22 -q 4 trie "$DATA/lm_sc.arpa" "$DATA/sc.lm"

ls -la "$DATA/sc.dict" "$DATA/sc.lm"
