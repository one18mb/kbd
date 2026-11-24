# 引擎

面向修改 `EngineBridge`、`LibimeEngine`、JNI 或 `engine.cpp` 的开发者。本文描述引擎契约、libime 集成和 A+B 上文/自学习；不包含键盘布局与交互。

代码是唯一事实来源。前端行为见 [behavior.md](behavior.md)。

## 1. 接口

```java
public interface EngineBridge {
    void load(String dataDir, String userDir);
    boolean isReady();
    void setScheme(String scheme);
    void input(char key);
    void backspace();
    State state(int candidateLimit);
    String select(int index);
    void reset();
    void recordText(String text);
    void recordBackspace();
    void saveUserDict();
}
```

```java
State = { String preedit; String[] candidates; }
```

- `dataDir`：只读词典目录，包含 `sc.dict`、`sc.lm`。
- `userDir`：可写目录，保存 `history.bigram`。
- 版本字符串：`kbd-engine 1.4 / libime 1.1.17`。

## 2. 生命周期

```text
setScheme(方案)      可先于 load；只记录，不要求引擎就绪
load(dataDir, userDir)  加载词典/模型，恢复历史，创建 PinyinContext
input/backspace/state/select  仅在 ready 时有效
saveUserDict()       保存 UserLanguageModel；IME 收起或切换时调用
```

- `LibimeEngine` 静态加载 `libkbd_engine.so`；JNI 方法全部转给 `engine.cpp` 的 `Engine`。
- `Engine` 内部有互斥锁；当前由 IME 单线程调用，不承诺多线程并发。
- `load` 失败时 `isReady()` 为 false，错误通过引擎状态暴露给前端。

## 3. 方案

libime 内置 8 种双拼；KBD 只暴露 5 种 26 键双拼，加全拼：

| code | 方案 |
| --- | --- |
| `quanpin` | 全拼（默认） |
| `xiaohe` | 小鹤双拼 |
| `ziranma` | 自然码 |
| `abc` | 智能ABC |
| `zhongwenzhixing` | 中文之星 |
| `gb` | 国标双拼 |

- `setScheme` 更新 `ShuangpinBuiltinProfile`；已加载时重建 `PinyinContext`，清空当前组词，保留跨输入框上文。
- `quanpin` 使用 `setUseShuangpin(false)`；其余方案启用双拼 profile。
- 方案持久化在 `SharedPreferences("kbd")` 的 `scheme`；`KbdIme` 启动时先设置方案再加载词典。

## 4. 输入与候选

```text
input(char)     拼音字母，小写
backspace()     删除一个拼音字母
state(limit)    返回 preedit 与最多 limit 个候选
select(index)   上屏候选，返回本次新增文本
reset()         结束当前组词并清空候选，保留上文
```

- `preedit` 由引擎返回；前端只把空格显示为 `'`，不改引擎内容。
- 组词中删除走 `engine.backspace()`；无组词删除走 `recordBackspace()` + `KEYCODE_DEL`。
- 候选上屏由 `select` 内部记账；英文、符号、转义文本由 `recordText` 记账。
- `select` 返回的是相对上次的增量，前端据此上屏，避免重复提交。

## 5. A+B：自学习与上文

A（自学习）：

- `select` 后若 `ctx_->selected()`，调用 `ctx_->learn()` 写入 `UserLanguageModel`。
- `saveUserDict()` 把模型保存到 `userDir/history.bigram`；IME 收起时调用。

B（跨输入框上文）：

- 已选词通过 `selectedWordsWithPinyin()` 进入 `history_`，按字符裁到 10 字。
- `recordText` 按 UTF-8 字符逐字加入 `history_`；`recordBackspace` 裁掉末尾一个字符。
- `reset` 结束当前组词并保留 `history_`，切换输入框不丢上文。
- 上文匹配由 `PinyinContext` 处理，完整上文无匹配时回退到更短上下文。

`history_` 只保留 10 字是设计选择：更短的上文更稳定，也限制内存与误联想。

## 6. 数据与构建

- 词典/模型不提交仓库：
  - `third_party/data/dict_sc.txt` → `sc.dict`；
  - `third_party/data/lm_sc.arpa` → `sc.lm`。
- `sh scripts/host-gen-data.sh` 生成上述文件，`sh scripts/sync-assets.sh` 复制到 `android/app/src/main/assets/engine`。
- 首次启动时 `EngineData` 把 assets 解压到应用私有 `files/engine`；用户数据在 `files/engine-user`。
- 引擎静态链接 libime / fcitx5 / zstd，只打包 `arm64-v8a`。
- 宿主联调：`sh scripts/host-selftest.sh`。

## 7. 边界与约束

- 引擎不联网，不申请网络权限；输入内容与自学习数据只在本机。
- 简繁转换不在引擎内，由宿主 `KbdIme` 使用平台 ICU `Hans-Hant` 完成；无转换数据时保持原文。
- 上文只保留 10 字；超长上文会被截断。
- 性能指标（冷加载、单键延迟、内存、包体）随设备和词典版本变化，不在文档中承诺固定数值。

源码入口：

- [EngineBridge.java](../android/app/src/main/java/com/kbd/ime/EngineBridge.java)
- [LibimeEngine.java](../android/app/src/main/java/com/kbd/ime/LibimeEngine.java)
- [EngineData.java](../android/app/src/main/java/com/kbd/ime/EngineData.java)
- [engine.cpp](../android/engine/src/engine.cpp)
- [KbdIme.java](../android/app/src/main/java/com/kbd/ime/KbdIme.java)
