# 开发、构建与发布

## 1. 环境

| 组件 | 版本 / 位置 |
| --- | --- |
| JDK | OpenJDK 17 |
| Android SDK | `/home/fz/opt/android-sdk`，platform 34、build-tools 34.0.0 |
| NDK | 26.1.10909125 |
| SDK CMake | 3.22.1（必须用 SDK 自带版本） |
| Gradle | 8.7（`/home/fz/opt/gradle/gradle-8.7`） |
| libime / fcitx5 / Boost / zstd | `third_party/`（不入库） |

每次构建前：

```sh
. scripts/env.sh
```

`scripts/env.sh` 设置 `ANDROID_SDK_ROOT`、`ANDROID_NDK_HOME`、`GRADLE_HOME` 和 `PATH`，并清空 `LD_LIBRARY_PATH`。不写 shell rc。

第三方源码与数据不提交仓库，按以下步骤准备：

```sh
cd third_party
curl -L https://codeload.github.com/fcitx/libime/tar.gz/refs/tags/1.1.17 | tar xz
curl -L https://codeload.github.com/fcitx/fcitx5/tar.gz/refs/tags/5.1.23 | tar xz
curl -L https://codeload.github.com/boostorg/boost/tar.gz/refs/tags/boost-1.84.0 | tar xz
curl -L https://codeload.github.com/facebook/zstd/tar.gz/refs/tags/v1.5.6 | tar xz
# kenlm 子模块单独取，放入 libime-1.1.17/src/libime/core/kenlm
# 词库 dict-20260907 与 lm_sc.arpa-20260629 放入 third_party/data
sh ../scripts/host-gen-data.sh
```

## 2. 构建

```sh
sh scripts/sync-assets.sh          # sc.dict / sc.lm → app assets
cd android
gradle :app:assembleDebug
gradle :app:assembleRelease
```

产物：

- `android/app/build/outputs/apk/debug/app-debug.apk`
- `android/app/build/outputs/apk/release/app-release.apk`

发布签名读取 `android/keystore.properties`（已 gitignore）。没有该文件时 release 回退到 debug 签名。密钥不在仓库中，需单独备份。

## 3. 测试

```sh
sh scripts/preview.sh          # 134 条动作断言 + 几何断言 + 预览 PNG
sh scripts/webdemo-test.sh     # webdemo 与 Android 几何 fixture 对拍（789 项）
sh scripts/host-selftest.sh    # 宿主机 libime 联调：双拼/A+B/落盘
```

几何 fixture：

```sh
rm -rf /tmp/kbd-fixture && mkdir -p /tmp/kbd-fixture
javac -encoding UTF-8 -d /tmp/kbd-fixture \
  android/app/src/main/java/com/kbd/ime/KbdData.java \
  android/app/src/main/java/com/kbd/ime/KbdLayout.java \
  scripts/fixtures/GeometryFixture.java
java -Dfile.encoding=UTF-8 -cp /tmp/kbd-fixture com.kbd.ime.GeometryFixture \
  > scripts/fixtures/geometry.json
```

`scripts/preview.sh` 输出 `docs/preview/*.png`，README 只使用这些 Android 预览图。

## 4. 在线原型

`webdemo/` 是纯静态原型，由 GitHub Actions 部署到：

**https://one18mb.github.io/kbd/**

工作流：`.github/workflows/pages.yml`，只在 `main` 推送时部署 `webdemo/`。

本地运行：

```sh
cd webdemo
python3 -m http.server 8765
# http://127.0.0.1:8765/
```

URL 参数便于分享固定状态：

```text
?w=1604&h=720&theme=dark&scheme=xiaohe&demo=nihao
```

webdemo 是视觉/交互原型，候选用 mock 数据，不运行 libime。

## 5. 发布

1. 更新 `android/app/build.gradle` 的 `versionCode` / `versionName`，以及 `engine.cpp` 的 `versionString()`。
2. `gradle :app:assembleRelease`。
3. 提交、打 tag、推送。
4. 创建 GitHub Release，附 `app-release.apk`。

签名密钥同一版本线必须保持一致，否则已安装用户无法覆盖升级。

## 6. 仓库结构

```text
android/app     五层前端 + Android 适配 + MainActivity 帮助页
android/engine  libime JNI 封装、A+B、宿主联调
webdemo         GitHub Pages 在线原型
docs            geometry / behavior / engine / development
scripts         构建、测试、数据生成、fixture
```

清理环境：`scripts/clean-env.sh --yes`（会删除 SDK、third_party、构建产物）。
