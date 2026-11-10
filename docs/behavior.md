# 行为规则

本文描述 `KbdApp`、`KbdGestures`、`KbdRenderer` 与 `KbdData` 的当前行为。代码是唯一事实来源。

## 1. 状态

`KbdApp` 持有全部业务状态：

| 字段 | 含义 |
| --- | --- |
| `chinese` | 中/英模式 |
| `symbols` | 符号层 |
| `escape`, `escapeBuffer` | 转义模式与缓冲（上限 24） |
| `expanded`, `candidateRow` | 候选展开与滚动行 |
| `theme`, `variant`, `scheme` | 主题 / 简繁 / 输入方案 |
| `mods` | shift/ctrl/alt 的 active/locked |
| `preedit`, `candidates` | 引擎返回的组词与候选 |
| `status` | 仅引擎错误时显示；正常为空 |

渲染层不持有业务状态；手势层只产出语义事件。

## 2. 点按

| 键 | 行为 |
| --- | --- |
| 字母 | 转义中追加缓冲；符号层上屏；Ctrl/Alt 按下时发生成键事件；中文组词中 Shift 清修饰，否则追加拼音；无组词且 Shift 时上屏大写；英文模式上屏字母 |
| 空格 | 转义中执行转义；组词中选首选；否则插入空格 |
| 删除 | 转义中删缓冲；组词中 `engine.backspace()`；否则 `recordBackspace()` + `KEYCODE_DEL` |
| 确认 | 转义中无效；组词中展开/收起候选；否则插入换行 |
| 中/英 | 切换中英文；长按进入符号层 |
| 修饰键 | 点按切换粘滞；长按锁定 |
| 方向键 | 无组词/转义时发送 DPAD + ctrl/alt 修饰 |

## 3. 滑动、长按、连发

- 滑入阈值 `25·S/32`，滑出 `12·S/32`（S=32 时 25/12）。
- 上/下滑：发送该键上/下标签；无标签时空滑动清修饰。
- 长按阈值 400ms；修饰键锁定，空格进入转义，删除/方向键连发；连发间隔 60ms。
- 长按后松手不再触发点按。
- 符号层上下滑翻页；候选区滚轮滚动 1 行。
- `Tab`/`Esc` 在紧凑模式下显示 `Ta`/`Es`；Shift 按下时发送 `META_SHIFT` 的 Tab/Esc，可与 Ctrl/Alt 叠加。

## 4. 候选

- 候选序列由引擎给出，前端按 `KbdLayout.candidateRows` 流式装箱。
- 最小 2 键宽、偶数倍键宽、不缩写；超长按行宽折行。
- 竖屏常驻行显示第 `candidateRow` 行；展开面板从下一行开始，行高按 `slots` 计算。
- 横屏中列行宽为 `p` 键；面板从 `candidateRow` 开始，展开时占满键盘高度。
- 滚轮/滑块滚动步长 1 行，上限由 `rowsFitting` 计算。
- 选词后清空组词；若引擎仍有组词则保留候选与展开状态。

## 5. 转义命令

长按空格进入 `\`；确认执行，删除退格，Esc 退出。

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

已删除：`\hp`、`\j`、`\s`、`\sc`、`\tc`、`\dk`、`\lt`、`\au`。

输入方案：

```text
quanpin 全拼（默认）
xiaohe 小鹤双拼
ziranma 自然码
abc 智能ABC
zhongwenzhixing 中文之星
gb 国标双拼
```

## 6. 符号层

- 长按中/英进入符号层；长按退出。
- 符号表由 `KbdData.SYMBOLS` 提供，排除键上/下滑已可输入的字符。
- 竖屏 10 列 × 3 行；横屏列数由 `KbdLayout.symbolCols` 计算。
- 上下滑翻页；点按上屏。

## 7. 渲染

- 四色主题：暗 `#000/#111/#fff/#555`，亮 `#f7f7f7/#fff/#111/#9e9e9e`。
- 无边框：按键只比背景亮一档。
- 键宽 `K`、中缝 `c`、空格 `2K+c`、删除/回车 `2K+g`。
- 动态字号 `S`、键高 `3S+2g`；修饰键/Tab/Esc 统一紧凑标签。
- 拼音浮层在键盘外：竖屏 x=0 宽 W；横屏 q→p（1604×720 下 x=228 宽 1148）。
- 状态行仅在引擎错误时显示。

## 8. 宿主集成

- `KbdIme` 负责生命周期、输入连接、`SharedPreferences("kbd")`、系统 inset 与帮助页启动。
- 拼音不写应用 composing text；由 `KbdPreeditView` 在键盘上方绘制非触摸浮层。
- 桌面 KBD 图标打开 `MainActivity` 完整帮助页。
- 设置持久化键：`theme`、`variant`、`scheme`、`bottomSafe`、`symbolRow`。

源码入口：

- [KbdApp.java](../android/app/src/main/java/com/kbd/ime/KbdApp.java)
- [KbdGestures.java](../android/app/src/main/java/com/kbd/ime/KbdGestures.java)
- [KbdRenderer.java](../android/app/src/main/java/com/kbd/ime/KbdRenderer.java)
- [KbdData.java](../android/app/src/main/java/com/kbd/ime/KbdData.java)
