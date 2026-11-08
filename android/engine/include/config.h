#ifndef KBD_ENGINE_CONFIG_H
#define KBD_ENGINE_CONFIG_H

/* ------------------------------------------------------------------
 * libime 构建期配置（原由 libime 的 CMake 生成）
 * ------------------------------------------------------------------ */
#ifndef _LIBIME_CONFIG_H_IN_
#define _LIBIME_CONFIG_H_IN_
/* 运行时数据目录由 Java 侧显式传入，这里仅满足编译期引用。 */
#define LIBIME_INSTALL_PKGDATADIR "/data/local/tmp/kbd/data"
#define LIBIME_INSTALL_LIBDATADIR "/data/local/tmp/kbd/lib"
#endif

/* ------------------------------------------------------------------
 * fcitx5 构建期配置（原由 fcitx5 的 CMake 生成，这里只保留被引用项）
 * ------------------------------------------------------------------ */
#ifndef _FCITX5_CONFIG_H_IN_
#define _FCITX5_CONFIG_H_IN_

#define FCITX_INSTALL_PREFIX "/usr"
#define FCITX_INSTALL_SYSCONFDIR "/etc"
#define FCITX_INSTALL_LIBDIR "/usr/lib"
#define FCITX_INSTALL_LIBDATADIR "/usr/lib"
#define FCITX_INSTALL_LIBEXECDIR "/usr/lib"
#define FCITX_INSTALL_DATADIR "/usr/share"
#define FCITX_INSTALL_PKGDATADIR "/usr/share/fcitx5"
#define FCITX_INSTALL_BINDIR "/usr/bin"
#define FCITX_INSTALL_LOCALEDIR "/usr/share/locale"
#define FCITX_INSTALL_ADDONDIR "/usr/share/fcitx5/addon"
#define FCITX_LIBRARY_SUFFIX ".so"
#define FCITX_VERSION_STRING "5.1.23"

/* Android / bionic 能力 */
#define HAVE_PIPE2 1
#define HAVE_SYS_WAIT_H 1
#define HAVE_SYS_MMAN_H 1
#define HAVE_SYS_UIO_H 1

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#endif /* _FCITX5_CONFIG_H_IN_ */

#endif /* KBD_ENGINE_CONFIG_H */
