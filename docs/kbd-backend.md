# KBD 输入法后端设计（v1.1）

> 状态：M2.0 构建 spike 已通过（宿主机 + 鸿蒙 4.0 真机）；四项性能指标待测
>
> 相关文档：[kbd-design.md](kbd-design.md)（前端与交互设计）、[dev-env.md](dev-env.md)（开发环境与清理清单）
>
> 前端几何见 [kbd-adaptive.md](kbd-adaptive.md)，行为见 [kbd-parity.md](kbd-parity.md)；`webdemo/` 是自适应原型。

---

## 1. 范围

本稿只描述输入法引擎侧（候选、拼音、双拼、上屏），不重复前端行网格与手势设计。

目标：

- 中文拼音输入 + 全拼/26 键双拼，候选质量与响应速度满足日常聊天与笔记。
- 720p / arm64-v8a 设备上冷启动、内存、包体可控。
- Android 系统输入法（`InputMethodService`），键盘视图自绘，不使用系统或第三方键盘 UI。

非目标：

- 五笔、仓颉、方言、多语言输入法。
- 云候选、联想词推送、账号体系。
- 桌面端复用（引擎封装保持可移植，但不作为验收项）。

## 2. 决策

| 项 | 结论 | 理由 |
| --- | --- | --- |
| 引擎 | **libime** | 二进制 trie 词典 + bigram 模型，运行时开销低；只需拼音/双拼，不需要 RIME 的方案与插件体系 |
| 集成 | **独立薄 JNI 封装** | libime 只依赖 Fcitx5Utils（工具库）+ Boost，不依赖 fcitx5 的 addon、事件循环与前端 |
| 不采用 | fork fcitx5-android、改 Trime | 两者前端架构与我们的自绘键盘不匹配；fcitx5-android 应用层为 GPL-3.0 |
| 双拼 | 小鹤（flypy） | 需先确认 libime 内置方案是否包含，缺则补表 |
| 授权 | 自有代码 MIT；libime/Fcitx5Utils 以独立 `.so` 动态链接（LGPL） | 保持 MIT 发布，避免内联 GPL 代码导致整包 GPL |

回退方案：若 spike 证明 libime 无法脱离 fcitx5 框架、或小鹤补表成本过高，切 librime（`rime_api.h` C 接口 + 单 schema），`EngineBridge` 接口不变。

## 3. 依赖边界

| 依赖 | 用途 | 说明 |
| --- | --- | --- |
| libime | 拼音/双拼解码、候选、语言模型、用户词典 | 核心，vendor 固定版本 |
| Fcitx5Utils | libime 的通用工具库 | 只取 fcitx5 仓库 `src/lib/fcitx-utils`，不链接 fcitx5 core |
| Boost | libime 内部使用（以实际 CMake 依赖为准） | 若只需头文件则 header-only；需要编译组件时只编所需部分 |

验证点（M2.0 第一件事）：**只链接上述三者能否产出 `.so`**。若链接被迫拉入 fcitx5 主框架，视为路线失败，立即评估回退。

## 4. 总体架构

```text
┌──────────────────────── Android App (Java) ────────────────────────┐
│ KbdIME : InputMethodService                                        │
│   ├─ KeyboardView      自绘键盘（对应 webdemo/js/view.js）          │
│   ├─ KeyboardModel     状态机（对应 webdemo/js/input.js + app.js）  │
│   └─ EngineBridge      引擎接口（唯一后端依赖）                     │
│          └─ LibimeEngine  ── JNI ──┐                                │
└────────────────────────────────────┼───────────────────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │ libkbd_engine.so (MIT wrapper)  │
                    │  libfcitx5utils.so (LGPL)       │
                    │  liblibime*.so   (LGPL)         │
                    └────────────────┬────────────────┘
                                     │
                    词典 / 语言模型 / 用户词典（文件系统）
```

线程模型：

- **引擎线程（单线程）**：唯一的 libime 调用者，串行处理 `input / candidates / select / reset`。
- **词典加载**：`onCreate` 起后台线程加载，加载完成前键盘可用（候选为空），不阻塞 UI。
- **UI 线程**：按键事件投递到引擎线程，结果以不可变对象回传；单次调用目标 ≤ 20 ms，允许同步等待，不做异步回调链。
- 引擎实例与输入会话 1:1；切换输入框时 `reset()`，不销毁引擎。

## 5. EngineBridge 接口

```java
public interface EngineBridge {
    void load(String dataDir, String userDir); // 只读词典 + 可写用户数据
    boolean isReady();

    void setScheme(String scheme);          // quanpin / xiaohe / ziranma / abc / zhongwenzhixing / gb
    void input(int keyCode);                // 字母/数字等字符键
    void backspace();
    State state(int candidateLimit);        // 当前预编辑 + 候选
    String select(int index);               // 返回本次上屏文本
    void reset();                           // 清空当前会话（保留跨输入框上文）
    void recordText(String text);           // 非候选上屏按字符计入上文
    void recordBackspace();                 // 已上屏文本被删，裁掉上文末尾一个字符
    void saveUserDict();                    // 保存自学习历史

    final class State {
        public final String preedit;        // 展示用，如 ni'hao
        public final String[] candidates;   // 最多 candidateLimit 个
    }
}
```

约束：

- `input` 只负责追加按键，不隐式上屏；上屏只发生在 `select`。
- `select` 返回**本次新增的上屏文本**（不是累积整句）：libime 的 `selectedSentence()`
  会累积同一组词内所有已选项，wrapper 按已上屏前缀做差，避免重复上屏。
- `candidateLimit` 由前端传入：竖屏 50、横屏 18；引擎一次返回，前端自行切片，不做二次聚合。
- 所有字符串 UTF-8 / Java String 一一对应，不走字节数组。
- 失败用返回空状态表示，不抛异常到 UI；错误写日志（仅 debug 构建）。

> M2.0 实际实现（已编译运行）：为最小可用，`LibimeEngine` 采用全局单例 + 静态 native 方法，
> 接口为 `load(String dir)` / `input(char)` / `state(int)` / `select(int)`；`State` 暂只有
> `preedit`、`candidates` 两个字段，未含 `hasMore`。`Scheme` 参数与 `long handle` 生命周期
> 留到 M2.1 接口冻结时补齐。

### 5.1 与前端模型的映射

| 前端字段（webdemo） | 后端来源 |
| --- | --- |
| `composing` | 引擎当前按键串（拼音原始字母） |
| `preedit` | `State.preedit`；若引擎不提供分隔，前端按音节自行插入 `'` |
| `candidates` | `State.candidates` |
| `expanded` | 纯前端状态，不通知引擎 |
| `escape` / 符号层 | 纯前端状态，不进入引擎 |
| `select` 上屏 | `EngineBridge.select(index)` 返回的文本，直接插入编辑器 |
| 首选词（空格上屏） | `select(0)`，语义与点击候选一致 |

## 6. JNI 层

- 一个 Java 类 `LibimeEngine`，一个 C++ 类 `Engine`，Java 持有 `long handle`。
- JNI 函数只做参数转换与异常兜底，不承载业务逻辑；业务在 `Engine` 内。
- 生命周期：`nativeCreate` → `nativeLoad`（可多次）→ 调用 → `nativeDestroy`，与 `InputMethodService` 生命周期对齐。
- 内存：候选列表一次性构造 `jobjectArray`；不在 JNI 层缓存 Java 对象。
- 线程：`Engine` 内部加互斥锁，JNI 调用可来自任意线程，但实际由单线程调用。

待验证：libime 是否提供 `preedit()`；若不提供，预编辑由前端拼接原始按键。

## 7. 输入方案（26 键双拼 + 全拼）

libime 1.1.17 的 `ShuangpinBuiltinProfile` 提供 8 种内置双拼，但 KBD 1.1 只暴露 **26 键方案**：

| code | 方案 |
| --- | --- |
| `ziranma` | 自然码 |
| `abc` | 智能ABC |
| `zhongwenzhixing` | 中文之星 |
| `xiaohe` | 小鹤双拼 |
| `gb` | 国标双拼 |

另有 `quanpin` 全拼（`setUseShuangpin(false)`），共 6 种可选方案。默认 `quanpin`。微软/紫光/拼音加加使用非字母键，已移除。

- 前端转义命令 `\zh=code` 切换方案；`KbdIme` 持久化到 `SharedPreferences("kbd")` 的 `scheme`。
- `Engine::setScheme`：更新 profile；若已加载则重建 `PinyinContext`，清空当前组词，保留跨输入框上文。
- `quanpin` 不使用 profile，只关闭双拼解析。
- 宿主端 `pipeline.cpp` 会构造 8 个 profile 并打印 valid/table 数量，确认版本内置表完整。

## 8. 数据与包体

| 内容 | 位置 | 说明 |
| --- | --- | --- |
| 词典 + 语言模型 | `assets/engine/` → 首次启动解压到 `filesDir/engine/` | libime 需要真实文件路径 |
| 用户词典 | `filesDir/user/` | 退出/切后台时落盘 |
| 方案配置 | 代码内枚举 | v1 不做运行时方案编辑 |

目标：**不设 APK 体积上限**，词典与语言模型完整随包，不裁剪联想模型。

实测（M2.0）：`.so` strip 后 3.4 MB；`sc.dict` 4.4 MB；`sc.lm` 34 MB（完整三元模型，
保留联想/预测能力）。debug APK 实测 51.2 MB（含 5.3 MB Unifont），作为首版基线接受。

## 9. 授权与合规

- 自有代码（app / Java 层 / 构建脚本）：MIT。
- 引擎 `.so`：**当前实现为静态链接** libime + Fcitx5Utils + kenlm + zstd，因此
  `libkbd_engine.so` 整体按 **LGPL-2.1-or-later** 分发（上游为 LGPL）。要求在发布物中：
  - 附 LGPL 文本、所用版本与源码获取地址；
  - 提供可替换/可重链接方式：随包公开 `libkbd_engine.so` 的完整对应源码与构建脚本
    （本仓库已满足），或后续改为把 libime 编成独立 `.so` 动态链接。
- zstd 为 BSD-3-Clause；Boost 为 BSL-1.0；kenlm 为 LGPL-2.1。
- 随包提供：LICENSE 文本、所用版本与源码获取地址。
- **不复制** fcitx5-android（GPL-3.0）与 Trime（GPL-3.0）的代码；只参考其构建思路。
- 若未来引入 GPL 代码，整包必须切换为 GPL，需单独决策。

## 10. 构建体系与目录

```text
android/
  settings.gradle
  build.gradle
  app/                       Java，无 AndroidX，minSdk 26
    src/main/java/com/kbd/ime/
      KbdIme.java            输入法服务（生命周期 + Host 桥接）
      KbdInputView.java      Android 适配：Canvas Sink + 触摸转发
      KbdApp.java            状态与动作（对应 app.js）
      KbdRenderer.java       渲染（对应 view.js）
      KbdGestures.java       手势识别（对应 input.js）
      KbdData.java           静态数据（对应 data.js）
      KbdLayout.java         竖屏几何（对应 layout.js）
      EngineBridge.java      引擎接口
      LibimeEngine.java      JNI 封装
      EngineData.java        assets 首次解压
      MainActivity.java      开发宿主
  engine/                    JNI wrapper + CMakeLists（MIT）
third_party/
  libime/                    pinned commit（LGPL）
  fcitx5/                    仅 fcitx5-utils 所需子集（LGPL）
  data/                      词典与模型原始文件与产物
scripts/
  env.sh                     导出 SDK/NDK 变量（不写入 shell rc）
  build-engine.sh            独立编译 .so，便于先于 App 验证
  host-gen-data.sh           宿主机生成 sc.dict / sc.lm
  host-selftest.sh           input→candidates→select 联调
  sync-assets.sh             数据复制进 app assets
  clean-env.sh               按清单清理开发环境
docs/
  kbd-backend.md             本稿
  dev-env.md                 环境安装与清理清单
```

- 构建：Gradle + AGP `externalNativeBuild`；另提供独立 CMake 脚本，保证不依赖 Gradle 也能编引擎。
- 最小依赖：不使用 Kotlin、AndroidX；Java 17 编译，minSdk 26，targetSdk 34。
- 字体：内置 `assets/fonts/unifont.otf`（5.3 MB，与 web 原型同源）。Unifont 的 ASCII
  半宽 16、CJK 全宽 32，正好对应 32px 字号下的 1/2 格；换用系统字体时 `Shft` 会超宽被截断、
  三行标签的行高超出 32px 而互相压叠。
- 拼音行：按系统输入法做法，拼音作为 composing text 写入编辑器（`BackgroundColorSpan` +
  `ForegroundColorSpan`），显示在应用的输入框里；键盘恒为 496，无拼音带。
  详见 [kbd-parity.md](kbd-parity.md) 的 D1。
- 四色调色板跟系统深/浅色切换，无边框思路（按键只比背景亮一档）：深色 `#000/#111/#fff/#555`，浅色 `#f7f7f7/#fff/#111/#9e9e9e`；副字向背景方向退让，拉开与主字的对比。
- 刚性分层：`KbdGestures` 只产出事件、`KbdRenderer` 只读状态绘制、`KbdApp` 独占状态。
  逐格对照见 [kbd-parity.md](kbd-parity.md)。
- 滚动：符号层按**键行**（108 设计 px / 行）滚动、循环；展开候选按**候选行**
  （36 设计 px / 行，5 个一词）滚动、到头即停。一次手势按位移换算成多行，
  拖动中逐行跳变（非平滑），不是整页翻转。
- 验收：`scripts/preview.sh` 先跑 50 条动作真值表断言，再用同一份渲染代码输出
  `docs/preview/*.png`，并断言几何与字体度量。

## 11. 性能指标与验收

| 指标 | 目标 | 测量方式 | 当前实测 |
| --- | --- | --- | --- |
| 引擎冷加载 | ≤ 300 ms（后台，不阻塞首屏） | 真机 logcat 打点 | 待真机 |
| 单键到候选 | ≤ 20 ms（P95） | 引擎内计时，1000 次采样 | 待真机 |
| 常驻内存（引擎） | ≤ 40 MB | `dumpsys meminfo` | 待真机 |
| 双拼首项一致率 | ≥ 95% | 30 串对照全拼 | 待批量对照 |
| 稳定性 | 连续输入 30 分钟无崩溃/无内存增长 | monkey + 手工 | 待真机 |

M2.0 spike 实测（2026-11-08，宿主机 x86_64）：

- `libkbd_engine.so`（arm64-v8a）：未 strip 33.8 MB，`llvm-strip --strip-unneeded` 后 **3.4 MB**；
  导出 11 个 JNI 符号；仅依赖 `liblog/libm/libdl/libc`，未拖入 fcitx5 主框架。
- 小鹤：`ShuangpinProfile(Xiaohe)` 可用，`validInput=26`、`table=505`；抽查 `ni→ni`、`hc→hao`、`wu→wu`、`sh→sang`（s+ang）、`zh→zang`（z+ang）。
- 全链路：加载 `sc.dict`(4.4 MB) + `sc.lm`(34 MB)，输入 `nihc` → preedit `ni hc` → 118 个候选，首选「你好」→ `select(0)` → 已上屏「你好」。
- 复现：`scripts/host-gen-data.sh` 生成数据，`scripts/host-selftest.sh` 跑联调。

真机验收（2026-11-08，鸿蒙 4.0 / Android 12 兼容，arm64）：

- debug APK 51.2 MB（内置 dict + 完整 lm + Unifont），手动安装后系统可识别并启用「KBD 输入法」。
- 键盘可拉起：候选条显示 `ni hc`，候选为「你好 / 拟好 / 你 / 拟 / 泥 …」。
- 结论：M2.0 的 `.so` 加载、小鹤、`input → candidates` 在目标系统成立；性能指标与 `select` 上屏的真机手感待 M2.2 一并测。

M2.2 竖屏第一版（2026-11-08）：

- 移植 `layout.js` 几何、`data.js` 键位、`app.js` 状态机、`input.js` 手势；绘制抽为纯 Java `KbdPainter`。
- 宿主端自检 `scripts/preview.sh`：几何断言全部 PASS，输出 5 张 PNG 于 `docs/preview/`。
- 真机反馈修正：内置 Unifont（系统字体导致 `Shft` 截断、三行标签压叠）；符号页补齐按键底板。
- 引擎修正：`select` 改为返回增量（libime `selectedSentence()` 会累积，分段选词实测 `你` → `你好`）。

## 12. 里程碑

| 阶段 | 内容 | 完成标准 | 状态 |
| --- | --- | --- | --- |
| M2.0 构建 spike | 编 `Fcitx5Utils + libime` arm64-v8a；确认小鹤；量四项指标 | `.so` 可加载，`input → candidates → select` 跑通 | **完成**：编译、小鹤、链路、鸿蒙真机拉起均通过；性能指标待测 |
| M2.1 接口冻结 | `EngineBridge` 与前端模型对齐，写入本稿 | 接口评审通过 | **完成**：`state/input/backspace/select/reset` 已稳定，前端 Host 另接浮层/KeyEvent/简繁/设置 |
| M2.2 原生键盘 | 移植 `layout.js` 与状态机，竖屏优先 | 竖屏可完成日常输入 | **完成**：竖屏 + 横屏 + 自适应几何 + 组合键 + 方案切换；宿主端 134 条断言、20 张快照通过；真机手感待验 |
| M2.3 横屏与打磨 | 横屏、符号、转义、用户词典、真机手感 | 与 web 原型行为一致 | **进行中**：横屏已完成；剩余用户词典、四项性能实测、真机手感 |

## 13. 风险与回退

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| libime 实际依赖拖入 fcitx5 core | 工作量接近移植 fcitx5 | spike 首日验证；失败即回退 librime |
| 小鹤不在内置方案表 | 需要维护 vendor 补丁 | 先补表；若成本高，评估 librime 小鹤 schema |
| NDK 交叉编译 Boost 组件失败 | 构建受阻 | 优先 header-only；必要时裁剪 libime 相关功能 |
| 数据体积 | 首版约 51 MB | 已确认接受；联想模型不裁剪 |
| libime C++ ABI 不稳定 | 升级困难 | 固定版本 vendor，不追上游 |

## 14. 待验证清单

- [x] 只链接 `Fcitx5Utils + libime` 能否生成 arm64-v8a `.so`
- [x] 小鹤是否在 libime 内置双拼方案中
- [x] libime 是否提供 `preedit()`（音节分隔）
- [x] 词典与语言模型的实际文件名与体积（`sc.dict` + `sc.lm`，APK 51.2MB）
- [x] 候选 `limit` 是否支持一次返回 50 条（前端取 128）
- [ ] 用户词典 API 与落盘时机
- [ ] 冷加载、单键延迟、内存、包体四项实测

## 15. 后端下一步（前端已冻结）

1. ~~**用户词典持久化**~~ **已完成**：见 §16（A+B）。
2. **四项性能实测**：冷加载、单键延迟、内存、包体——需真机采集；宿主端脚本只能验证功能与渲染。
3. **简繁输出**：现用 Android ICU `Hans-Hant` 在 Java 层转换（不可用时原文回退）；若质量或可用性不足，再评估内置 OpenCC 词表。
4. **联想/预测**：上文重排已由 `setContextWordsWithPinyin` 实现；如需「下一词推荐」再做 `PinyinPrediction`，不混进当前候选。
5. **JNI 健壮性**：加载失败可读错误、重复 load、16KB page-size 兼容、`GetStringUTFChars` 空指针路径、异常清理。
6. **线程模型**：输入/查询目前在 UI 线程并走全局锁；libime 查询为内存操作，先实测单键延迟，超标再异步化（不改前端接口）。
7. **包体**：51.2MB = 引擎 3.4MB（strip）+ 词典/语言模型 + Unifont；如需压缩，优先裁剪数据而非模型。

## 16. A+B：自学习与上文预测（已实现）

**A 自学习（HistoryBigram）**
- 候选整段完成后调用 `PinyinContext::learn()`：把「上文 + 本次选择（词+拼音码）」写进 `UserLanguageModel::history()`。
- 收键盘/切走时 `UserLanguageModel::save` 落盘到 `filesDir/engine-user/history.bigram`；`load` 时读回。

**B 上文预测（上下文重排）**
- 引擎维护跨输入框上文：`vector<HistoryBigram::WordWithCode>`，按**字符**裁到 10 个。
- 每次上文变化调用 `PinyinContext::setContextWordsWithPinyin(history)`；`PinyinContext::type` 用它作初始 LM 状态，候选自动带上下文重排。
- n→n-1 由 n-gram 回退天然完成，不需要前端逐级尝试。

**记账规则**
- 候选上屏：`select` 内部记账（整段完成时入上文并 `learn`）。
- 英文/符号/转义文本：前端 `recordText` 按字符入上文。
- 已上屏文本删除：`recordBackspace` 裁掉上文末尾一个字符（不关心编辑器实际删了哪个）。
- 切输入框：`reset` 清会话但保留上文；收键盘时落盘。

**已知边界**
- 光标移动、选中替换、撤销等编辑器内部变化 IME 不可见，上文可能漂移；目前没有「清上文」命令，只能重启输入法清空。需要的话再加一条内部命令。
- 10 字符上限只约束内存；上下文状态最多取 `maxOrder()-1` 个词，单键开销可忽略。
