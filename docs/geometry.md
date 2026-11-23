# 几何规则

面向修改 `KbdLayout`、`KbdRenderer` 或 webdemo 布局的开发者。本文只描述几何契约：给定窗口与安全区，键盘、候选和标签如何落在整数像素上。

代码是唯一事实来源；本文数字由 `scripts/preview/LayoutPreview.java` 与 `scripts/webdemo-test.js` 对拍。行为语义见 [behavior.md](behavior.md)。

## 1. 输入与输出

```text
W      输入法可用窗口宽度（px）
H      输入法可用窗口高度（px）
b      竖屏底部安全区（px；默认系统 inset，可用 \sb 覆盖）
R      屏幕 W/H 模式阈值，固定 0.75
```

输出是一个 `KbdLayout`：模式（竖屏/横屏）、字体级 `n`、字号 `S`、键宽 `K`、缝隙 `g/c`、边距 `m`、键高 `keyH`、键盘尺寸与各区域坐标。

模式不读系统方向 API，只由 `(W, H, b)` 决定。

## 2. 模式判定

```text
竖屏可行：W ≥ 160 且 H ≥ 208 + b
横屏可行：W ≥ 256 且 H ≥ 144

只有一个可行 → 选它
两个都可行：W/H > R → 横屏；否则竖屏
都不可行：W ≥ H 取横屏，否则竖屏
```

`R = 0.75`：3:4 及更窄走竖屏，4:5 及更宽走横屏。

## 3. 公共变量

```text
n   字体级，S = 16n
K   键宽，K = 16 + 2t（偶数）
g   普通缝；c 中缝，c = g 或 g+1
m   左右外边距（单侧）
e   c - g，取 0 或 1
```

级常量：

```text
t_start(n) = n = 1 ? 0 : 17n − 25
t_cap(n)   = 17n − 8
g_start(n) = n = 1 ? 0 : 2(n − 1)
g_cap(n)   = 2n
K_max(n)   = 34n
```

真实尺寸从 `K = 16 + 2t_start` 起步；每进一级，`K`、`g`、`m` 一起抬升。

## 4. 竖屏

行宽 10 键，中缝在第 5、6 列之间：

```text
W = 2m + 10K + 8g + c = 160 + 2m + 9g + 20t + e
空格宽 = 2K + c
删除/回车宽 = 2K + g
键高 keyH = 3S + 2g
键盘高 Hk = 13S + 12g + b
```

求解顺序：

1. `n = max(1, ceil(W / 360))`，再按高度下压到满足 `H ≥ 208n + 12·g_start(n) + b`；
2. `g` 在 `[g_start, min(g_cap, (H − b − 208n)/12)]` 中取能放下 `W` 的最大值；
3. `t` 从 `t_start` 向上取到能放下 `W` 的最大值，允许超过 `t_cap`；
4. 余量给 `m`，奇偶给 `e`：

```text
e = (W − 160 − 9g − 20t) & 1
m = (W − 160 − 9g − 20t − e) / 2
c = g + e
```

`m` 是低位缓冲：`g` 进位前约 0..4，`t` 进位前约 0..9；固定宽度下它只是一个侧边距。

诊断标记：

- `tBeyond`：`t > t_cap`，键宽超过本级上限；
- `gCappedByHeight`：`g` 被高度压低；
- `overflow`：`W < 160` 或 `208n + 12g + b > H`。

## 5. 横屏

横屏没有 `b`。三行字母、左右外列和中列组成：

```text
外列宽 wK  = 2K + g
中列宽 wKs = p·K + (p−1)g + e      (p = 2..6)
W = 2wK + 10K + wKs + 12g + 2m
  = (14+p)K + (p+13)g + e + 2m
键盘高 Hk = 9S + 8g
```

求解顺序：

1. `n = max(1, ceil(min(W,H) / 360))`，再降到满足 `W ≥ 16·K_start(n)` 且 `H ≥ 144n + 8·g_start(n)`；
2. `g` 在 `[g_start, min(g_cap, (H − 144n)/8)]` 中取能放下 `W` 的最大值；
3. `t` 取能放下 `W` 的最大值，并封顶在 `t_cap`；
4. `p` 从 6 往 2 取能放下的最大值；
5. 余量给 `m`，奇偶给 `e`。

位置：

```text
letterX      = m + wK + g
midX         = letterX + letterW + g
rightX       = midX + wKs + g
outerRightX  = rightX + letterW + g
```

拼音浮层从 q 起点到 p 终点：`preeditLeft = letterX`，`preeditWidth = rightX + letterW − letterX`。例如 1604×720 下为 `x=228, w=1148`。

## 6. 候选

候选与符号共用当前 `S/g/K`：

```text
格宽 = keys·K + (keys−1)·g
最小 2 键宽，偶数倍键宽，不缩写
超长按行宽折行，不截断
行高 = slots·S + (slots−1)·g
```

- 竖屏行宽 10 键；横屏行宽 `p` 键。
- 竖屏常驻候选行显示序列第 `candidateRow` 行；展开面板从 `candidateRow+1` 开始。
- 横屏候选面板从 `candidateRow` 开始，展开面板高 `Hk`。
- 滚动上限由 `rowsFitting()` 计算，滚动步长 1 行。

## 7. 短标签

统一紧凑开关：修饰键或 Tab/Esc 任一放不下，就同时使用 `Ct/Al/Sh` 与 `Ta/Es`。主标签与副标签都占满整个键宽；紧凑后 `Ta/Es` 宽 `S`，而 `K ≥ S` 恒成立。

```text
modKeyW = landscape ? outerW : K
modShort = max(textWidth("Ctrl"), textWidth("Alt"), textWidth("Shft")) ≥ modKeyW − 2
subShort = textWidth("Tab") > K 或 textWidth("Esc") > K
compact = modShort || subShort
```

| 尺寸（示例） | 键盘 | 标签 |
| --- | --- | --- |
| 720×1604 | 720×496 | Ctrl / Alt / Shft / Tab / Esc |
| 768×1024 | 768×728 | Ct / Al / Sh / Ta / Es |
| 800×800 | 796×320 | Ct / Al / Sh / Ta / Es |
| 1024×768 | 1020×320 | Ctrl / Alt / Shft / Tab / Esc |
| 1604×720 | 1436×320 | Ctrl / Alt / Shft / Tab / Esc |

## 8. 验证

```sh
sh scripts/preview.sh          # 134 条动作断言 + 几何断言 + 预览 PNG
sh scripts/webdemo-test.sh     # webdemo 与 Android fixture 对拍（789 项）
```

fixture 由 `scripts/fixtures/GeometryFixture.java` 从 `KbdLayout` 生成，覆盖 160×256 到 2340×1080 的 21 组尺寸；修改几何后先重生成 fixture，再跑 webdemo 对拍。

源码入口：

- [KbdLayout.java](../android/app/src/main/java/com/kbd/ime/KbdLayout.java)
- [KbdRenderer.java](../android/app/src/main/java/com/kbd/ime/KbdRenderer.java)
- [LayoutPreview.java](../scripts/preview/LayoutPreview.java)
- [GeometryFixture.java](../scripts/fixtures/GeometryFixture.java)
- [layout.js](../webdemo/js/layout.js)

## 9. 不变量

- `K` 恒为偶数，`K ≥ S`。
- `c = g` 或 `g+1`；中缝只吸收 1px 奇偶。
- 竖屏左右边距对称；横屏 `kbW = W − 2m`，`kbLeft = m`。
- 任何尺寸下 `Ta/Es` 与 `Ct/Al/Sh` 同开同关。
- 修改公式后，`preview.sh`、fixture 和 `webdemo-test.sh` 必须同时通过。
