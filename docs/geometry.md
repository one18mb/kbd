# 几何规则

本文描述 `KbdLayout` 当前实现。代码是唯一事实来源；所有数字由 `scripts/preview/LayoutPreview.java` 与 `scripts/webdemo-test.js` 对拍。

## 1. 输入与模式

```text
W, H   输入法可用窗口像素
b      竖屏底部安全区（默认系统 inset，可用 \sb 覆盖）
R      屏幕 W/H 阈值，固定 0.75
```

模式由代码计算，不读 `Configuration.orientation`：

```text
竖屏可行：W ≥ 160 且 H ≥ 208 + b
横屏可行：W ≥ 256 且 H ≥ 144

只有一个可行 → 选它
两个都可行：W/H > 0.75 → 横屏；否则竖屏
都不可行：W ≥ H 取横屏，否则竖屏
```

`R=0.75` 让 3:4 走竖屏，4:5 及更宽走横屏。

## 2. 变量

```text
n   字体级，S = 16n
K   键宽，K = 16 + 2t（偶数）
g   8 条普通缝
c   中缝，c = g 或 g+1
m   左右外边距（单侧）
b   竖屏底部安全区
```

竖屏：

```text
W = 2m + 10K + 8g + c = 160 + 2m + 9g + 20t + e
空格宽 = 2K + c
键高 h = 3S + 2g
键盘高 Hk = 13S + 12g + b
```

横屏：

```text
wK  = 2K + g
wKs = p·K + (p−1)g + e      (p ≥ 2)
W   = 2wK + 10K + wKs + 12g + 2m
    = (14+p)K + (p+13)g + e + 2m
键盘高 Hk = 9S + 8g
```

## 3. 竖屏级与进位

```text
m_max = n
g_max = c_max = 2n
K_max = 34n
```

级起点：

```text
n = 1：t=0，g=c=0，m=0
n ≥ 2：t=17(n−1)−8，g=c=2(n−1)，m=n−1
```

给定 W：

1. 先在 `[g_start, min(2n, (H−b−208n)/12)]` 取能放下 W 的最大 g；
2. 再取能放下 W 的最大 t；
3. 余量给 m，奇偶给 e。

```text
e = (W − 160 − 9g − 20t) & 1
m = (W − 160 − 9g − 20t − e) / 2
c = g + e
```

`m` 是低位缓冲：g 进位前约 0..4，t 进位前约 0..9；固定屏幕宽度下只是一个侧边距，不会持续变化。

字体级先由宽度决定 `n = max(1, ceil(W/360))`，再被高度向下压：

```text
H ≥ 208n + 12·g_start(n) + b
```

高度不足时不升 n，`K` 可以超过 `34n` 继续变宽（读数标 `k 超本级上限`）。

## 4. 横屏

- 字体级依赖短边：`n = max(1, ceil(min(W,H)/360))`，再降到满足 `W ≥ 16·K_start(n)`、`H ≥ 144n + 8·g_start(n)`。
- `K` 先到本级上限，`p` 在 2..6 中取能放下的最大值，余量给 `m`。
- 横屏没有 `b`。
- 拼音浮层从 q 起点到 p 终点：`x = letterX`，`w = rightX + letterW − letterX`。1604×720 下为 `x=228, w=1148`。

## 5. 候选

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

## 6. 短标签

统一紧凑开关：修饰键或 Tab/Esc 任一放不下，就同时使用 `Ct/Al/Sh` 与 `Ta/Es`。

```text
modKeyW = landscape ? outerW : K
modShort = max(textWidth("Ctrl"), textWidth("Alt"), textWidth("Shft")) ≥ modKeyW − 2
subShort = textWidth("Tab") > K − g 或 textWidth("Esc") > K − g
compact = modShort || subShort
```

| 尺寸 | 键盘 | 标签 |
| --- | --- | --- |
| 720×1604 | 720×496 | Ctrl / Alt / Shft / Tab / Esc |
| 768×1024 | 768×728 | Ct / Al / Sh / Ta / Es |
| 800×800 | 796×320 | Ct / Al / Sh / Ta / Es |
| 1024×768 | 1020×320 | Ctrl / Alt / Shft / Tab / Esc |
| 1604×720 | 1436×320 | Ctrl / Alt / Shft / Tab / Esc |

## 7. 验证

```sh
sh scripts/preview.sh          # 134 条动作断言 + 几何/预览断言
sh scripts/webdemo-test.sh     # webdemo 与 Android fixture 对拍
```

fixture 由 `scripts/fixtures/GeometryFixture.java` 从 `KbdLayout` 生成，覆盖 160×256 到 2340×1080 的 21 组尺寸。

源码入口：

- [KbdLayout.java](../android/app/src/main/java/com/kbd/ime/KbdLayout.java)
- [LayoutPreview.java](../scripts/preview/LayoutPreview.java)
- [GeometryFixture.java](../scripts/fixtures/GeometryFixture.java)
- [layout.js](../webdemo/js/layout.js)
