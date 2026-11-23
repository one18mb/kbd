# 开发、构建与发布

面向维护者：从干净环境构建 APK、跑测试、更新在线原型和发布版本。设计细节见 [geometry.md](geometry.md)、[behavior.md](behavior.md)、[engine.md](engine.md)。

## 1. 环境

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK | platform 34、build-tools 34.0.0 |
| NDK | 26.1.10909125 |
| SDK CMake | 3.22.1（必须用 SDK 自带版本） |
| Gradle | 8.7 |
| libime / fcitx5 / Boost / zstd | 1.1.17 / 5.1.23 / 1.84.0 / 1.5.6 |

`scripts/env.sh` 设置 `ANDROID_SDK_ROOT`、`ANDROID_NDK_HOME`、`GRADLE_HOME` 与 `PATH`，并清空 `LD_LIBRARY_PATH`。`scripts/env.sh`、`scripts/clean-env.sh` 里的 SDK/Gradle 路径是维护者本机值，换机器时按实际安装位置修改，不写 shell rc。

每次构建前：

```sh
. scripts/env.sh
```

## 2. 第三方源码与数据

第三方源码与生成数据不提交仓库，放在 `third_party/`：

```sh
cd third_party
curl -L https://codeload.github.com/fcitx/libime/tar.gz/refs/tags/1.1.17 | tar xz
curl -L https://codeload.github.com/fcitx/fcitx5/tar.gz/refs/tags/5.1.23 | tar xz
curl -L https://codeload.github.com/boostorg/boost/tar.gz/refs/tags/boost-1.84.0 | tar xz
curl -L https://codeload.github.com/facebook/zstd/tar.gz/refs/tags/v1.5.6 | tar xz
# kenlm 子模块单独取，放入 libime-1.1.17/src/libime/core/kenlm
# 原始数据放入 third_party/data：dict_sc.txt、lm_sc.arpa
sh ../scripts/host-gen-data.sh
```

`host-gen-data.sh` 产出 `third_party/data/sc.dict` 与 `sc.lm`；它只依赖宿主机 `g++` / `make`，不依赖 Android 工具链。

## 3. 构建

```sh
sh scripts/sync-assets.sh          # sc.dict / sc.lm → app assets
cd android
gradle :app:assembleDebug
gradle :app:assembleRelease
```

产物：

- `android/app/build/outputs/apk/debug/app-debug.apk`
- `android/app/build/outputs/apk/release/app-release.apk`

发布签名读取 `android/keystore.properties`（已 gitignore），密钥文件需单独备份。没有该文件时 release 回退到 debug 签名；同一版本线必须使用同一密钥，否则已安装用户无法覆盖升级。

## 4. 测试

```sh
sh scripts/preview.sh          # 134 条动作断言 + 几何断言 + 预览 PNG
sh scripts/webdemo-test.sh     # webdemo 与 Android 几何 fixture 对拍（789 项）
sh scripts/host-selftest.sh    # 宿主机 libime 联调：双拼 / A+B / 落盘
```

修改几何后重生成 fixture：

```sh
rm -rf /tmp/kbd-fixture && mkdir -p /tmp/kbd-fixture
javac -encoding UTF-8 -d /tmp/kbd-fixture \
  android/app/src/main/java/com/kbd/ime/KbdData.java \
  android/app/src/main/java/com/kbd/ime/KbdLayout.java \
  scripts/fixtures/GeometryFixture.java
java -Dfile.encoding=UTF-8 -cp /tmp/kbd-fixture com.kbd.ime.GeometryFixture \
  > scripts/fixtures/geometry.json
```

`scripts/preview.sh` 同时输出 `docs/preview/*.png`；README 只使用这些 Android 预览图。

## 5. 在线原型

`webdemo/` 是纯静态原型，部署在：

**https://one18mb.github.io/kbd/**

工作流 `.github/workflows/pages.yml` 在以下情况部署 `webdemo/`：

- `main` 推送且改动 `webdemo/**` 或工作流文件；
- 手动 `workflow_dispatch`。

本地运行：

```sh
cd webdemo
python3 -m http.server 8765
# http://127.0.0.1:8765/
```

URL 参数可固定状态：

```text
?w=1604&h=720&r=0.75&b=32&theme=dark&scheme=xiaohe&mode=landscape&demo=nihao
```

`mode` 可取 `auto` / `portrait` / `landscape`。webdemo 只做视觉/交互与 mock 候选，不运行 libime。

## 6. 发布

1. 更新 `android/app/build.gradle` 的 `versionCode` / `versionName`。
2. 更新 `android/engine/src/engine.cpp` 的 `versionString()`。
3. `gradle :app:assembleRelease`。
4. 核对 APK：

```sh
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump badging \
  android/app/build/outputs/apk/release/app-release.apk | head
```

5. 提交版本变更，打 annotated tag（如 `v1.2`）并推送。
6. 重命名资产为 `KBD-<版本>-arm64-v8a.apk`，创建 GitHub Release：

```sh
gh release create v1.2 /tmp/KBD-1.2-arm64-v8a.apk \
  --title "KBD 1.2" --notes-file /tmp/release-notes.md
```

发布说明至少包含：版本号、用户可见变更、安装要求（Android 8.0+、arm64-v8a）、签名兼容性。

## 7. 仓库结构

```text
android/app      五层前端 + Android 适配 + MainActivity 手册页
android/engine   libime JNI 封装、A+B、宿主联调
webdemo          GitHub Pages 在线原型
docs             geometry / behavior / engine / development
scripts          构建、测试、数据生成、fixture
```

- `android/app/src/main/res/drawable-nodpi/grants_qr.png`：应用内手册的赞赏码裁切图。
- `android/app/src/main/assets/fonts/unifont.otf`：键盘与手册统一字体。
- 清理环境：`scripts/clean-env.sh --yes`（会删除 SDK、third_party、构建产物）。
