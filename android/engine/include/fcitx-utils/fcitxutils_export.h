#ifndef FCITXUTILS_EXPORT_H
#define FCITXUTILS_EXPORT_H

/* 为 Android 静态链接伪造的导出宏，等价于默认可见性。
 * 宏名必须与 CMake generate_export_header 的输出一致（无下划线）。 */
#define FCITXUTILS_EXPORT
#define FCITXUTILS_NO_EXPORT
#define FCITXUTILS_DEPRECATED
#define FCITXUTILS_DEPRECATED_EXPORT
#define FCITXUTILS_DEPRECATED_NO_EXPORT

#endif
