# 行为规则

面向修改 `KbdApp`、`KbdGestures`、`KbdRenderer` 或 `KbdData` 的开发者。本文只描述交互契约：按键、手势、候选、符号层、转义与宿主集成。

代码是唯一事实来源。几何公式见 [geometry.md](geometry.md)，引擎接口见 [engine.md](engine.md)。

## 1. 状态归属

`KbdApp` 是唯一业务状态持有者：

| 字段 | 含义 |
| --- | --- |
| `chinese` | 中/英模式 |
| `symbols` | 符号层 |
| `escape`, `escapeBuffer` | 转义模式与缓冲（上限 24） |
| `expanded`, `candidateRow` | 候选展开与滚动行 |
| `theme`, `variant` | 主题 / 简繁 |
| `mods` | Shift/Ctrl/Alt 的 active/locked |
| `preedit`, `candidates` | 引擎返回的组词与候选 |
| `status` | 仅引擎错误时显示；正常为空 |

渲染层不持有业务状态；手势层只产出语义事件，不直接改状态。

## 2. 点按

| 键 | 行为 |
| --- | --- |
| 字母 | 转义中追加缓冲；符号层上屏；Ctrl/Alt 按下时发生成键事件（Shift 参与 `META_SHIFT`）；中文组词中 Shift 清修饰，否则追加拼音；无组词且 Shift 时上屏大写；英文模式上屏字母 |
| 空格 | 转义中执行命令；组词中选首选；Ctrl/Alt 按下时发送 `KEYCODE_SPACE`（Shift 参与修饰）；否则插入空格 |
| 删除 | 转义中删缓冲；组词中 `engine.backspace()`；否则 `recordBackspace()` + `KEYCODE_DEL` |
| 换行 | 转义中无效；组词中展开/收起候选；Ctrl/Alt 按下时发送 `KEYCODE_ENTER`（Shift 参与修饰）；否则插入换行；长按收起键盘（仅无组词时） |
| 中/英 | 切换中英文；长按进入/退出符号层 |
| 修饰键 | 点按切换粘滞；长按锁定 |
| 方向键 | 无组词/转义时发送 DPAD，带 Ctrl/Alt/Shift 修饰 |

Tab/Esc 不在主键面，作为 `a`/`z` 的上滑标签存在；按下 Shift 时发送带 `META_SHIFT` 的 Tab/Esc，可与 Ctrl/Alt 叠加。

## 3. 滑动、长按、连发

- 滑入阈值 `25·S/32`，滑出阈值 `12·S/32`。
- 上/下滑：发送该键上/下标签；无标签的空滑动只清临时修饰。
- 长按阈值 400ms；修饰键锁定，空格进入/退出转义，删除与方向键连发，间隔 60ms。
- 长按触发后松手不再执行点按。
- 符号层上下滑翻页。
- 候选区滚轮滚动 1 行。

## 4. 候选

- 候选序列由引擎给出，前端按 `KbdLayout.candidateRows` 流式装箱。
- 最小 2 键宽、偶数倍键宽、不缩写；超长文本按行宽折行。
- 竖屏常驻行显示第 `candidateRow` 行；展开面板从下一行开始，行高按 `slots` 计算。
- 横屏候选面板从中列开始，展开时占满键盘高度。
- 展开面板内滚轮/滑块步长 1 行，上限由 `rowsFitting()` 计算。
- 点按候选上屏；选词后清空组词，若引擎仍有组词则保留候选与展开状态。

## 5. 转义

- 长按空格进入 `\`；此时空格执行、删除退格、再长按空格退出。
- 无效命令由空格取消并退出；删除到只剩 `\` 不退出。
- Tab/Esc 在转义模式下被忽略。
- 转义内容显示在键盘上方的拼音浮层，不写入应用输入框。

| 命令 | 行为 |
| --- | --- |
| `\n` | 换行 |
| `\t` | 制表 |
| `\u4f60` / `\u{4f60}` | 插入 Unicode 码点 |
| `\\` | 插入反斜杠 |
| `\sb=数字` | 覆盖竖屏底部安全区（0–200px），持久化 |
| `\sb=auto` | 底部安全区恢复系统 inset |
| `\zh=方案` | 切换输入方案，持久化 |
| `\sk=dark` / `\sk=light` / `\sk=auto` | 暗色 / 亮色 / 跟随系统，持久化 |
| `\st=s` / `\st=t` | 简体 / 繁体，持久化 |

输入方案：

```text
quanpin          全拼（默认）
xiaohe           小鹤双拼
ziranma          自然码
abc              智能ABC
zhongwenzhixing  中文之星
gb               国标双拼
```

## 6. 符号层

- 长按中/英进入，再长按退出；符号层的 `中/英` 键显示 `符`。
- 符号表由 `KbdData.SYMBOLS` 提供，排除键上/下滑已可输入的字符。
- 竖屏 10 列 × 3 行；横屏列数由 `KbdLayout.symbolCols` 计算。
- 上下滑翻页，点按上屏；翻页位置持久化。

## 7. 渲染

- 四色主题：暗 `#000 / #111 / #fff / #555`，亮 `#f7f7f7 / #fff / #111 / #9e9e9e`。
- 无边框：按键只比背景亮一档；按下状态用前景/背景反色。
- 键宽 `K`、中缝 `c`、空格 `2K+c`、删除/换行 `2K+g`。
- 动态字号 `S`、键高 `3S+2g`；修饰键与 Tab/Esc 统一紧凑标签。
- 拼音浮层在键盘外：竖屏宽 `W`；横屏从 q 到 p。
- 状态行只在引擎错误时出现。

## 8. 宿主集成

- `KbdIme` 负责生命周期、输入连接、系统 inset、拼音浮层和偏好读写。
- 拼音不写入应用 composing text；由 `KbdPreeditView` 在键盘上方绘制非触摸浮层。
- 设置持久化键：`theme`、`variant`、`scheme`、`bottomSafe`、`symbolRow`。
- 桌面 KBD 图标打开 `MainActivity`，显示 manpage 风格手册（用法、命令、手势、隐私、许可、赞赏）。

源码入口：

- [KbdApp.java](../android/app/src/main/java/com/kbd/ime/KbdApp.java)
- [KbdGestures.java](../android/app/src/main/java/com/kbd/ime/KbdGestures.java)
- [KbdRenderer.java](../android/app/src/main/java/com/kbd/ime/KbdRenderer.java)
- [KbdData.java](../android/app/src/main/java/com/kbd/ime/KbdData.java)
- [KbdIme.java](../android/app/src/main/java/com/kbd/ime/KbdIme.java)
