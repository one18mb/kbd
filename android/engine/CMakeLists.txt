cmake_minimum_required(VERSION 3.22.1)
project(kbd_engine CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(THIRD "${CMAKE_CURRENT_LIST_DIR}/../../third_party")
set(LIBIME_DIR "${THIRD}/libime-1.1.17")
set(FCITX_DIR "${THIRD}/fcitx5-5.1.23")
set(BOOST_DIR "${THIRD}/boost_1_84_0")
set(ZSTD_DIR "${THIRD}/zstd-1.5.6")

if(NOT EXISTS "${LIBIME_DIR}/src/libime/pinyin/pinyinime.cpp")
    message(FATAL_ERROR "缺少 libime 源码：${LIBIME_DIR}")
endif()

# ---------- zstd（静态） ----------
file(GLOB ZSTD_SRCS
    "${ZSTD_DIR}/lib/common/*.c"
    "${ZSTD_DIR}/lib/compress/*.c"
    "${ZSTD_DIR}/lib/decompress/*.c")
add_library(kbd_zstd STATIC ${ZSTD_SRCS})
target_include_directories(kbd_zstd PUBLIC "${ZSTD_DIR}/lib" "${ZSTD_DIR}/lib/common")
target_compile_definitions(kbd_zstd PRIVATE ZSTD_MULTITHREAD=1)

# ---------- fcitx-utils（只取 libime 需要的子集） ----------
set(FU "${FCITX_DIR}/src/lib/fcitx-utils")
# 只保留 libime 实际引用的实现；standardpath / eventloop / semver / key 等不参与。
add_library(kbd_fcitxutils STATIC
    "${FU}/stringutils.cpp"
    "${FU}/cutf8.cpp"
    "${FU}/fs.cpp"
    "${FU}/unixfd.cpp"
    "${FU}/utf8.cpp"
    "${FU}/connectableobject.cpp"
    "${FU}/element.cpp"
    "${FU}/inputbuffer.cpp"
    "${FU}/log.cpp"
    "${FU}/misc.cpp"
    "${FU}/environ.cpp"
    "${FU}/fdstreambuf.cpp")
target_include_directories(kbd_fcitxutils PUBLIC
    "${CMAKE_CURRENT_LIST_DIR}/compat"
    "${CMAKE_CURRENT_LIST_DIR}/include"
    "${FCITX_DIR}/src/lib")
target_compile_definitions(kbd_fcitxutils PRIVATE FCITX_UTILS_BUILD)

# ---------- libime core（含 kenlm） ----------
file(GLOB KENLM_SRCS
    "${LIBIME_DIR}/src/libime/core/kenlm/lm/*.cc"
    "${LIBIME_DIR}/src/libime/core/kenlm/util/*.cc"
    "${LIBIME_DIR}/src/libime/core/kenlm/util/double-conversion/*.cc")
list(FILTER KENLM_SRCS EXCLUDE REGEX "(_test|_main)\\.cc$")

add_library(kbd_imecore STATIC
    ${KENLM_SRCS}
    "${LIBIME_DIR}/src/libime/core/datrie.cpp"
    "${LIBIME_DIR}/src/libime/core/dictionary.cpp"
    "${LIBIME_DIR}/src/libime/core/decoder.cpp"
    "${LIBIME_DIR}/src/libime/core/languagemodel.cpp"
    "${LIBIME_DIR}/src/libime/core/inputbuffer.cpp"
    "${LIBIME_DIR}/src/libime/core/lattice.cpp"
    "${LIBIME_DIR}/src/libime/core/userlanguagemodel.cpp"
    "${LIBIME_DIR}/src/libime/core/historybigram.cpp"
    "${LIBIME_DIR}/src/libime/core/segmentgraph.cpp"
    "${LIBIME_DIR}/src/libime/core/utils.cpp"
    "${LIBIME_DIR}/src/libime/core/prediction.cpp"
    "${LIBIME_DIR}/src/libime/core/triedictionary.cpp")
target_include_directories(kbd_imecore PUBLIC
    "${CMAKE_CURRENT_LIST_DIR}/include"
    "${LIBIME_DIR}/src"
    "${LIBIME_DIR}/src/libime/core/kenlm"
    "${BOOST_DIR}")
target_compile_definitions(kbd_imecore PUBLIC
    KENLM_MAX_ORDER=3
    _LIBCPP_ENABLE_CXX17_REMOVED_UNARY_BINARY_FUNCTION)
target_link_libraries(kbd_imecore PUBLIC kbd_fcitxutils kbd_zstd)

# ---------- libime pinyin ----------
add_library(kbd_imepinyin STATIC
    "${LIBIME_DIR}/src/libime/pinyin/pinyincontext.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/pinyindata.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/pinyindecoder.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/pinyindictionary.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/pinyinencoder.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/pinyinime.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/pinyinmatchstate.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/shuangpinprofile.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/pinyinprediction.cpp"
    "${LIBIME_DIR}/src/libime/pinyin/pinyincorrectionprofile.cpp")
target_include_directories(kbd_imepinyin PUBLIC
    "${CMAKE_CURRENT_LIST_DIR}/include"
    "${LIBIME_DIR}/src"
    "${BOOST_DIR}")
target_link_libraries(kbd_imepinyin PUBLIC kbd_imecore)

# ---------- JNI 封装 ----------
add_library(kbd_engine SHARED
    src/engine.cpp)
target_include_directories(kbd_engine PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/include")
target_link_libraries(kbd_engine PRIVATE kbd_imepinyin kbd_imecore kbd_fcitxutils kbd_zstd log)
