# 第三方组件与许可

KBD 以 GPL-3.0-or-later 发布。以下组件随源码或构建产物使用，许可证与上游一致。

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| libime | 1.1.17 | LGPL-2.1-or-later | 拼音引擎、双拼 profile、语言模型 |
| fcitx5 | 5.1.23 | LGPL-2.1-or-later | libime 依赖的 fcitx-utils |
| zstd | 1.5.6 | BSD-3-Clause | libime 词典压缩 |
| Boost | 1.84.0 | Boost Software License 1.0 | libime 头文件依赖 |
| GNU Unifont | unifont.otf | GPL-2.0-or-later，含字体嵌入例外 | 键盘与候选字体 |
| Android SDK / NDK | compileSdk 34 / NDK 26.1 | Apache-2.0 | 构建与运行环境 |

## 数据

`sc.dict`、`sc.lm` 由 libime 的拼音数据生成，按 libime 的 LGPL-2.1-or-later 使用；重新分发时请保留本文件与对应许可证文本。

## 说明

- libime / fcitx5 以静态库形式链接进 `libkbd_engine.so`。项目整体按 GPL-3.0-or-later 发布，满足 LGPL-2.1-or-later 的源码与重链接要求。
- GNU Unifont 的字体嵌入例外允许把字体嵌入文档/应用；本项目仍按 GPL-3.0-or-later 整体发布。
- 第三方源码与生成的词典/模型不提交到仓库，按 `docs/development.md` 下载与生成。
