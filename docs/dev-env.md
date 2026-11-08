# 开发环境与清理清单

> 配套文档：[kbd-backend.md](kbd-backend.md)
>
> 原则：**不 sudo、不 apt、不写 shell rc**。所有工具装在 `/home/fz/opt`，项目构建产物留在仓库内；卸载 = 删除下列目录。

## 1. 版本清单

| 组件 | 版本 | 安装位置 |
| --- | --- | --- |
| JDK | OpenJDK 17.0.20（系统已有） | `/usr/lib/jvm` |
| Android 命令行工具 | 12.0（commandlinetools 11076708） | `/home/fz/opt/android-sdk/cmdline-tools/latest` |
| Android SDK Platform | android-34 | `/home/fz/opt/android-sdk/platforms/android-34` |
| Android Build-Tools | 34.0.0 | `/home/fz/opt/android-sdk/build-tools/34.0.0` |
| Android NDK | 26.1.10909125（r26b） | `/home/fz/opt/android-sdk/ndk/26.1.10909125` |
| SDK CMake | 3.22.1（含 ninja） | `/home/fz/opt/android-sdk/cmake/3.22.1` |
| Platform-Tools（adb） | 随 SDK 安装 | `/home/fz/opt/android-sdk/platform-tools` |
| Gradle | 8.7 | `/home/fz/opt/gradle/gradle-8.7` |
| AGP | 8.5.2（Gradle 解析，缓存在 `~/.gradle`） | `~/.gradle/caches` |
| libime | 1.1.17（源码） | `third_party/libime-1.1.17` |
| kenlm | master（libime 子模块，需单独拉取） | `third_party/libime-1.1.17/src/libime/core/kenlm` |
| fcitx5 | 5.1.23（只取 fcitx-utils 子集） | `third_party/fcitx5-5.1.23` |
| Boost | 1.84.0（header-only） | `third_party/boost_1_84_0` |
| zstd | 1.5.6（静态编译） | `third_party/zstd-1.5.6` |
| 拼音词库 | dict-20260907（`dict_sc.txt`） | `third_party/data` |
| 语言模型 | lm_sc.arpa-20260629 | `third_party/data` |

系统包：**没有安装任何 apt 包**。Gradle 依赖只在首次构建时下载到 `~/.gradle`。

## 2. 环境变量

不写入 `~/.bashrc`。每次开发前：

```sh
. scripts/env.sh
```

脚本内容见 `scripts/env.sh`，包括 `ANDROID_SDK_ROOT`、`ANDROID_NDK_HOME`、`GRADLE_HOME`、`PATH`，并清空 `LD_LIBRARY_PATH`。

## 3. 安装记录（可复现）

```sh
mkdir -p /home/fz/opt/android-sdk/cmdline-tools /home/fz/opt/downloads /home/fz/opt/gradle
cd /home/fz/opt/downloads
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip -d /home/fz/opt/android-sdk/cmdline-tools
mv /home/fz/opt/android-sdk/cmdline-tools/cmdline-tools /home/fz/opt/android-sdk/cmdline-tools/latest

yes | /home/fz/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/home/fz/opt/android-sdk --licenses
/home/fz/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/home/fz/opt/android-sdk \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
  "ndk;26.1.10909125" "cmake;3.22.1"

curl -L -o gradle-8.7-bin.zip https://services.gradle.org/distributions/gradle-8.7-bin.zip
unzip -q gradle-8.7-bin.zip -d /home/fz/opt/gradle
```

第三方源码与数据（版本与 libime 1.1.17 的 `data/CMakeLists.txt` 一致）：

```sh
cd third_party
# 源码（固定版本）
curl -L https://codeload.github.com/fcitx/libime/tar.gz/refs/tags/1.1.17 | tar xz
curl -L https://codeload.github.com/fcitx/fcitx5/tar.gz/refs/tags/5.1.23 | tar xz
curl -L https://codeload.github.com/boostorg/boost/tar.gz/refs/tags/boost-1.84.0 | tar xz
curl -L https://codeload.github.com/facebook/zstd/tar.gz/refs/tags/v1.5.6 | tar xz
# kenlm 是 libime 的子模块，需单独取
curl -L -o /tmp/kenlm.tar.gz https://codeload.github.com/kpu/kenlm/tar.gz/refs/heads/master
mkdir -p libime-1.1.17/src/libime/core/kenlm
tar xzf /tmp/kenlm.tar.gz -C /tmp && cp -a /tmp/kenlm-master/. libime-1.1.17/src/libime/core/kenlm/

# 数据（原始文本，之后由脚本转成二进制）
make -C zstd-1.5.6 -j 8 zstd   # 系统无 zstd 命令时，先编一个解压器
mkdir -p data && cd data
curl -LO https://download.fcitx-im.org/data/dict-20260907.tar.zst
curl -LO https://download.fcitx-im.org/data/lm_sc.arpa-20260629.tar.zst
../../third_party/zstd-1.5.6/programs/zstd -d dict-20260907.tar.zst -o dict.tar && tar xf dict.tar
../../third_party/zstd-1.5.6/programs/zstd -d lm_sc.arpa-20260629.tar.zst -o lm.tar && tar xf lm.tar

# 生成 sc.dict / sc.lm（宿主机编译两个转换工具，不污染 Android 构建）
sh ../../scripts/host-gen-data.sh
```

`scripts/host-gen-data.sh` 会在 `/tmp/kbd-host-build` 编译 `pinyindict`、`build_binary`，输出
`third_party/data/sc.dict`（约 4.4 MB）与 `sc.lm`（约 34 MB）。

联调自检：

```sh
sh scripts/host-selftest.sh
# 期望：输入 nihc -> 首选「你好」-> 选 0 后已上屏「你好」
```

## 4. 验证

```sh
. scripts/env.sh
java -version
sdkmanager --list_installed | sed -n '1,20p'
$ANDROID_NDK_HOME/ndk-build --version
ls $ANDROID_SDK_ROOT/cmake/3.22.1/bin/{cmake,ninja}
gradle --version | sed -n '1,8p'
```

构建引擎（不依赖 Gradle）与 APK：

```sh
sh scripts/sync-assets.sh           # 把 sc.dict / sc.lm 复制进 app assets
sh scripts/preview.sh               # 宿主端渲染自检：几何断言 + 输出 docs/preview/*.png
sh scripts/build-engine.sh          # 产出 /tmp/kbd-engine-build/libkbd_engine.so
cd android && gradle :app:assembleDebug
# 产物：app-debug.apk 约 51 MB（含 5.3 MB Unifont、完整词典与联想模型）
```

真机安装（APK 已内置词典与模型，首次启动自动解压到 `filesDir/engine`）：

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
# 或在手机上直接安装该 APK
# 启用：设置 → 系统和更新 → 语言和输入法 → 启用「KBD 输入法」
# 打开 KBD app，应显示：输入 nihc → 首选「你好」→ 选0上屏「你好」
```

模拟器限制（WSL2）：

- 本机 WSL2 内核 **没有 `/dev/kvm`**，官方 Android Emulator 的 x86_64 镜像强制要求硬件加速，会直接拒绝启动；
  ARM 镜像在 x86 宿主上不受官方支持。
- `.wslconfig` 目前为 `guiApplications=false`，且开启嵌套虚拟化需要改 Windows 侧配置并 `wsl --shutdown`（会中断当前会话）。
- 因此模拟器不在此环境部署；真机（鸿蒙 4.0 / Android 12 兼容）手动安装 APK 为当前验证路径。

## 5. 磁盘占用（实测）

| 路径 | 用途 | 实际占用 |
| --- | --- | --- |
| `/home/fz/opt/android-sdk` | SDK + NDK + CMake | 2.6 GB |
| `/home/fz/opt/gradle` | Gradle 发行包 | 143 MB |
| `/home/fz/opt/downloads` | 安装包与数据缓存 | 403 MB（可删） |
| `~/.gradle` | AGP 依赖与构建缓存 | 324 MB |
| `~/.android` | adb/AVD 配置 | 视使用情况 |
| `third_party/libime-1.1.17` | libime + kenlm 源码 | 4 MB |
| `third_party/fcitx5-5.1.23` | fcitx-utils 源码 | 17 MB |
| `third_party/boost_1_84_0` | Boost 头文件 | 917 MB |
| `third_party/zstd-1.5.6` | zstd 源码与静态库 | 10 MB |
| `third_party/data` | 词库/模型原始文件与产物 | 约 480 MB（含 arpa 196 MB） |
| `/tmp/kbd-engine-build` | Android 引擎构建目录 | 约 300 MB（可删） |
| `/tmp/kbd-host-build` | 宿主机转换工具 | 约 60 MB（可删） |
| `android/app/build` | Gradle 构建产物（含 APK） | 约 99 MB（可删） |

## 6. 清理清单

按顺序执行（或直接运行 `scripts/clean-env.sh --yes`）：

```sh
# 1. 工具链
rm -rf /home/fz/opt/android-sdk
rm -rf /home/fz/opt/gradle
rm -rf /home/fz/opt/downloads

# 2. 构建缓存与设备配置
rm -rf ~/.gradle
rm -rf ~/.android

# 3. 项目内构建产物与第三方源码
rm -rf android/.gradle android/build android/app/build android/engine/build
rm -rf third_party/libime-1.1.17 third_party/fcitx5-5.1.23 \
       third_party/boost_1_84_0 third_party/zstd-1.5.6 third_party/data
rm -rf /tmp/kbd-engine-build /tmp/kbd-host-selftest

# 4. 环境变量：只需关闭当前终端；未写入任何 rc 文件
```

清理后如需恢复：重跑第 3 节命令 + `git submodule update --init`（或项目内拉取脚本）。

## 7. 注意事项

- 本机 `cmake`（4.2.3）过新，libime/fcitx5 的老 `cmake_minimum_required` 可能不兼容；一律使用 SDK 自带的 **CMake 3.22.1**。
- 本机环境会注入 `LD_LIBRARY_PATH`，导致 `cmake`/`git` 等系统命令报 GLIBCXX 缺失；`scripts/env.sh` 会清空该变量。
- 不把 SDK/NDK 路径写进仓库文件；统一由 `scripts/env.sh` 提供。
