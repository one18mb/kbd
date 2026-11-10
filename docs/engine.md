# 引擎

本文描述 `EngineBridge`、`LibimeEngine`、`Engine` 与 `KbdIme` 的当前实现。

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

`State = { String preedit; String[] candidates; }`。

## 2. libime

- 版本：libime 1.1.17；拼音词典 `sc.dict`、语言模型 `sc.lm`。
- `load(dataDir, userDir)` 加载 `sc.dict` / `sc.lm`；用户数据 `userDir/history.bigram` 存在时读入 `UserLanguageModel`。
- JNI 实现：`nativeLoad`、`nativeInput`、`nativeBackspace`、`nativeState`、`nativeSelect`、`nativeReset`、`nativeRecordText`、`nativeRecordBackspace`、`nativeSaveUserDict`、`nativeSetScheme`。
- 线程：`Engine` 内互斥锁；JNI 可来自任意线程，当前由 IME 单线程调用。

## 3. 方案

libime 内置 8 种双拼，KBD 只暴露 26 键方案：

| code | 方案 |
| --- | --- |
| `quanpin` | 全拼（默认） |
| `xiaohe` | 小鹤双拼 |
| `ziranma` | 自然码 |
| `abc` | 智能ABC |
| `zhongwenzhixing` | 中文之星 |
| `gb` | 国标双拼 |

- `setScheme` 更新 `ShuangpinBuiltinProfile`；若已加载则重建 `PinyinContext`，清空当前组词，保留跨输入框上文。
- `quanpin` 使用 `setUseShuangpin(false)`。
- 方案持久化在 `SharedPreferences("kbd")` 的 `scheme`；启动时先设置方案再加载。

## 4. 输入与候选

```text
input(char)     拼音字母，小写
backspace()     删除一个拼音字母
state(limit)    返回 preedit 与最多 limit 个候选
select(index)   上屏候选，返回文本
reset()         清空当前组词，保留上文
```

- `preedit` 由引擎返回，前端把空格显示为 `'`。
- 组词中删除走 `engine.backspace()`；无组词删除走 `recordBackspace()` + `KEYCODE_DEL`。
- 候选上屏由 `select` 内部记账；英文/符号/转义上屏由 `recordText` 记账。

## 5. A+B：自学习与上文

- `select` 后若 `ctx_->selected()`：`ctx_->learn()` 写入 `UserLanguageModel`，把 `selectedWordsWithPinyin()` 加入 `history_`，裁到 10 个字符，然后 `clear()` 并 `setContextWordsWithPinyin(history_)`。
- `recordText` 按字符加入 `history_`；`recordBackspace` 裁掉末尾一个字符。
- 上文匹配天然按 n → n−1 回退：`PinyinContext` 先尝试完整上文，无匹配时模型回退到更短上下文。
- `saveUserDict` 把 `UserLanguageModel` 保存到 `userDir/history.bigram`；IME 收起时调用。
- `reset` 清空组词与候选，保留 `history_`，切换输入框不会丢上文。

## 6. 数据与构建

- 词典/模型不提交仓库；按 [development.md](development.md) 生成 `third_party/data/sc.dict`、`sc.lm`，再由 `scripts/sync-assets.sh` 复制到 `android/app/src/main/assets/engine`。
- 引擎静态链接 libime / fcitx5 / zstd；只产出 `arm64-v8a`。
- 宿主联调：`sh scripts/host-selftest.sh`。

## 7. 已知边界

- 简繁转换使用平台 ICU `Hans-Hant`；无数据时保持原文。
- 冷加载、单键延迟、内存、包体四项指标仍待真机实测。
- `history_` 只保留 10 字，超长上文会被截断；这是设计选择，不是缺陷。

源码入口：

- [EngineBridge.java](../android/app/src/main/java/com/kbd/ime/EngineBridge.java)
- [LibimeEngine.java](../android/app/src/main/java/com/kbd/ime/LibimeEngine.java)
- [engine.cpp](../android/engine/src/engine.cpp)
- [KbdIme.java](../android/app/src/main/java/com/kbd/ime/KbdIme.java)
