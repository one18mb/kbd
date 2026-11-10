(function () {
  'use strict';

  // 自适应布局：输入窗口 (W,H)、底部安全区 b、比例阈值 R。
  // 输出键盘模式与几何，不再读取系统方向 API。
  //
  // 约定：
  //   n  字体级，f = 16n
  //   K  键宽，K = 16 + 2t（偶数）
  //   g  8 条普通缝；c 中缝，c = g 或 g+1
  //   m  左右外边距
  //   b  竖屏底部安全区
  //
  // 竖屏：W = 2m + 10K + 8g + c = 160 + 2m + 9g + 20t + e
  // 横屏：W = 2(2K+g) + 10K + wKs + 12g + 2m
  //        wKs = p*K + (p-1)g + e

  // R 是屏幕 W/H 阈值（<1）：W/H > R 优先横屏，W/H ≤ R 竖屏。
  // R=0.75：3:4 落入竖屏，4:5 及更宽走横屏。
  var DEFAULTS = { W: 720, H: 1604, b: 32, R: 0.75 };

  // 键盘自身 W/H 的建议区间，用于检查模式是否选得合理。
  var KB_RATIO = {
    portrait: [1.0, 1.6],
    landscape: [1.7, 5.0]
  };

  var PRESETS = [
    { label: '160×256 最小', W: 160, H: 256 },
    { label: '256×160 最小横屏', W: 256, H: 160 },
    { label: '360×640', W: 360, H: 640 },
    { label: '720×1604 参考', W: 720, H: 1604 },
    { label: '1080×2400', W: 1080, H: 2400 },
    { label: '1440×3200', W: 1440, H: 3200 },
    { label: '800×800', W: 800, H: 800 },
    { label: '768×1024 3:4', W: 768, H: 1024 },
    { label: '1024×768', W: 1024, H: 768 },
    { label: '1604×720 参考横屏', W: 1604, H: 720 },
    { label: '自定义', W: -1, H: -1, custom: true }
  ];

  function tStart(n) { return n === 1 ? 0 : 17 * n - 25; }
  function tCap(n) { return 17 * n - 8; }
  function gStart(n) { return n === 1 ? 0 : 2 * (n - 1); }
  function gCap(n) { return 2 * n; }
  function wStartP(n) { return n === 1 ? 160 : 360 * (n - 1); }
  function wStartL(n) { return n === 1 ? 256 : 544 * (n - 1); }

  function portraitStage(W) {
    return Math.max(1, Math.ceil(W / 360));
  }

  // 竖屏：宽度级 n 先定，再由 H 向下压。
  function portraitMaxN(W, H, b) {
    var n = portraitStage(W);
    while (n > 1) {
      var gs = gStart(n);
      if (208 * n + 12 * gs + b <= H) break;
      n--;
    }
    return n;
  }

  // 横屏：字体级依赖竖屏短边，再被横屏宽度/高度向下压。
  function landscapeShortN(W, H) {
    return Math.max(1, Math.ceil(Math.min(W, H) / 360));
  }

  function landscapeMaxN(W, H) {
    var n = landscapeShortN(W, H);
    while (n > 1) {
      var gs = gStart(n);
      if (W >= wStartL(n) && 144 * n + 8 * gs <= H) break;
      n--;
    }
    return n;
  }

  function solvePortrait(W, H, b, n) {
    var f = 16 * n;
    var gs = gStart(n);
    var gc = gCap(n);
    var gHeight = Math.floor((H - b - 208 * n) / 12);
    var gMax = Math.min(gc, Math.max(gs, gHeight));
    var ts = tStart(n);
    var tEnd = tCap(n);

    // 先长缝，再长键；m 是余量。
    var g = gs;
    for (var gg = gMax; gg >= gs; gg--) {
      if (160 + 9 * gg + 20 * ts <= W) { g = gg; break; }
    }

    var t = ts;
    var minBase = 160 + 9 * g + 20 * ts;
    if (W >= minBase) t = ts + Math.floor((W - minBase) / 20);

    var base = 160 + 9 * g + 20 * t;
    var e = (W - base) & 1;
    var m = Math.max(0, (W - base - e) / 2);
    var K = 16 + 2 * t;
    var c = g + e;
    var keyH = 3 * f + 2 * g;
    var mainH = 3 * keyH + 2 * g;
    var barH = f;
    var mainTop = barH + g;
    var funcTop = mainTop + mainH + g;
    var kbH = funcTop + keyH + b;
    var doubleW = 2 * K + g;
    var spaceW = 2 * K + c;

    var candCellW = 2 * K + g;
    var expCols = Math.max(1, Math.floor((W - 2 * m + g) / (candCellW + g)));
    var expRows = Math.max(1, Math.floor((mainH + g) / (f + g)));

    return {
      orientation: 'portrait',
      W: W, H: H, b: b,
      n: n, S: f, t: t, K: K, g: g, c: c, e: e, m: m,
      tCap: tEnd, tBeyond: t > tEnd,
      gCap: gc, gCappedByHeight: gMax < gc,
      keyH: keyH, kbW: W, kbLeft: 0, kbH: kbH, side: m,
      barH: barH, barTop: 0, mainTop: mainTop, mainH: mainH, funcTop: funcTop,
      double: doubleW, spaceW: spaceW,
      candCellW: candCellW, candGap: g, candSide: m, candH: f,
      expTop: mainTop, expH: mainH, expRows: expRows,
      expCols: expCols, expCellW: candCellW, expGap: g, expSide: m, expRowH: f,
      pinyinX: 0, pinyinW: W,
      preeditPad: Math.max(1, Math.round(f / 16)),
      pinyinH: f, pinyinGap: g,
      symbolRows: [mainTop, mainTop + keyH + g, mainTop + 2 * (keyH + g)],
      symbolK: K, symbolGap: g, symbolSide: m,
      symbolCols: 10, symbolSlots: 30
    };
  }

  function solveLandscape(W, H, n) {
    var f = 16 * n;
    var gs = gStart(n);
    var gc = gCap(n);
    var gHeight = Math.floor((H - 144 * n) / 8);
    var gMax = Math.min(gc, Math.max(gs, gHeight));
    var ts = tStart(n);
    var tEnd = tCap(n);

    var g = gs;
    for (var gg = gMax; gg >= gs; gg--) {
      if (16 * (16 + 2 * ts) + 15 * gg <= W) { g = gg; break; }
    }

    // 键宽先到本级上限，再让空格 p 和边距 m 吸收长边。
    var K = 16 + 2 * ts;
    var maxK = Math.floor((W - 15 * g) / 16);
    var t = ts;
    if (maxK > K) t = ts + Math.floor((maxK - K) / 2);
    if (t > tEnd) t = tEnd;
    K = 16 + 2 * t;

    var p = 2;
    for (var pp = 6; pp >= 2; pp--) {
      if ((14 + pp) * K + (pp + 13) * g <= W) { p = pp; break; }
    }

    var base = (14 + p) * K + (p + 13) * g;
    var e = (W - base) & 1;
    var m = Math.max(0, (W - base - e) / 2);
    var wKs = p * K + (p - 1) * g + e;

    var keyH = 3 * f + 2 * g;
    var rowY = [0, keyH + g, 2 * (keyH + g)];
    var kbH = 3 * keyH + 2 * g;
    var kbW = W - 2 * m;
    var outerW = 2 * K + g;
    var letterW = 5 * K + 4 * g;
    var letterX = outerW + g;
    var midX = letterX + letterW + g;
    var midW = wKs;
    var rightX = midX + midW + g;
    var outerRightX = rightX + letterW + g;

    var panelH = 2 * keyH + g;
    var panelCellW = Math.max(1, Math.floor((midW - g) / 2));
    var panelRows = Math.max(1, Math.floor((panelH + g) / (f + g)));
    var panelRowY = [];
    for (var i = 0; i < panelRows; i++) panelRowY.push(i * (f + g));

    var expRows = Math.max(1, Math.floor((kbH + g) / (f + g)));
    var expRowY = [];
    for (var j = 0; j < expRows; j++) expRowY.push(j * (f + g));

    var symbolAreaW = kbW - 2 * outerW - g;
    var symbolCols = Math.max(1, Math.floor((symbolAreaW + g) / (K + g)));
    var symbolW = symbolCols * K + (symbolCols - 1) * g;

    return {
      orientation: 'landscape',
      W: W, H: H, b: 0,
      n: n, S: f, t: t, K: K, g: g, c: g + e, e: e, m: m,
      tCap: tEnd, tBeyond: t > tEnd,
      gCap: gc, gCappedByHeight: gMax < gc,
      keyH: keyH, kbW: kbW, kbLeft: m, kbH: kbH, side: m,
      outerW: outerW, letterX: letterX, letterW: letterW,
      rightX: rightX, outerRightX: outerRightX,
      midX: midX, midW: midW, rowY: rowY, p: p,
      panelX: midX, panelW: midW, panelH: panelH,
      panelCellW: panelCellW, panelCols: 2, panelRows: panelRows,
      panelCellH: f, panelRowY: panelRowY,
      spaceX: midX, spaceY: rowY[2], spaceW: midW, spaceH: keyH,
      expX: midX, expY: 0, expW: midW, expH: kbH,
      expCols: 2, expCellW: panelCellW,
      expRows: expRows, expRowY: expRowY, expCellH: f,
      pinyinX: letterX, pinyinW: rightX + letterW - letterX,
      preeditPad: Math.max(1, Math.round(f / 16)),
      pinyinH: f, pinyinGap: g,
      symbolX: letterX, symbolW: symbolW, symbolCols: symbolCols,
      symbolCellW: K, symbolRows: 3, symbolRowY: rowY,
      symbolSlots: symbolCols * 3
    };
  }

  function decideMode(W, H, b, R, override) {
    if (override === 'portrait' || override === 'landscape') {
      return { mode: override, reason: '手动' };
    }
    var pOK = W >= 160 && H >= 208 + b;
    var lOK = W >= 256 && H >= 144;
    if (!pOK && !lOK) {
      return { mode: W >= H ? 'landscape' : 'portrait', reason: '低于最小尺寸' };
    }
    if (pOK && !lOK) return { mode: 'portrait', reason: '横屏宽度不足' };
    if (lOK && !pOK) return { mode: 'landscape', reason: '竖屏高度不足' };

    return W / H > R
      ? { mode: 'landscape', reason: 'W/H > R' }
      : { mode: 'portrait', reason: 'W/H ≤ R' };
  }

  function compute(input) {
    var o = input || {};
    var W = Math.max(1, Math.round(o.W || DEFAULTS.W));
    var H = Math.max(1, Math.round(o.H || DEFAULTS.H));
    var b = Math.max(0, Math.round(o.b == null ? DEFAULTS.b : o.b));
    var R = o.R == null ? DEFAULTS.R : Number(o.R);
    if (!(R > 0.2 && R <= 1)) R = DEFAULTS.R;

    var decided = decideMode(W, H, b, R, o.mode || 'auto');
    var metrics;
    if (decided.mode === 'portrait') {
      var np = portraitMaxN(W, H, b);
      metrics = solvePortrait(W, H, b, np);
    } else {
      var nl = landscapeMaxN(W, H);
      metrics = solveLandscape(W, H, nl);
    }
    metrics.modeReason = decided.reason;
    metrics.ratio = W / H;
    metrics.ratioHW = H / W;
    metrics.R = R;
    metrics.kbRatio = metrics.kbW / metrics.kbH;
    metrics.kbRatioRange = metrics.orientation === 'portrait'
      ? KB_RATIO.portrait : KB_RATIO.landscape;
    metrics.kbRatioOK = metrics.kbRatio >= metrics.kbRatioRange[0]
      && metrics.kbRatio <= metrics.kbRatioRange[1];
    metrics.overflow = metrics.kbH > H || metrics.kbW > W;
    metrics.compactLabels = compactLabels(metrics);
    return metrics;
  }

  // ---------- 候选 ----------

  function fullWidth(c) {
    var code = c.charCodeAt(0);
    return code >= 0x1100 && (code <= 0x115F || code === 0x2329 || code === 0x232A
      || (code >= 0x2E80 && code <= 0xA4CF && code !== 0x303F)
      || (code >= 0xAC00 && code <= 0xD7A3)
      || (code >= 0xF900 && code <= 0xFAFF)
      || (code >= 0xFE30 && code <= 0xFE6F)
      || (code >= 0xFF00 && code <= 0xFF60)
      || (code >= 0xFFE0 && code <= 0xFFE6));
  }

  function textWidth(text, size) {
    var w = 0;
    for (var i = 0; i < text.length; i++) w += fullWidth(text[i]) ? size : size / 2;
    return w;
  }

  function compactLabels(m) {
    var modKeyW = m.orientation === 'landscape' ? m.outerW : m.K;
    var widest = Math.max(textWidth('Ctrl', m.S),
      Math.max(textWidth('Alt', m.S), textWidth('Shft', m.S)));
    var modShort = widest >= modKeyW - 2;
    var subShort = textWidth('Tab', m.S) > m.K - m.g
      || textWidth('Esc', m.S) > m.K - m.g;
    return modShort || subShort;
  }

  function candCellBase(keys, m) {
    return keys * m.K + (keys - 1) * m.g;
  }

  function candKeys(text, rowKeys, m) {
    var width = textWidth(text, m.S) + 8;
    for (var keys = 2; keys <= rowKeys; keys += 2) {
      if (width <= candCellBase(keys, m)) return keys;
    }
    return rowKeys;
  }

  function candLines(text, rowKeys, m) {
    var max = candCellBase(rowKeys, m) - 8;
    if (textWidth(text, m.S) <= max) return [text];
    var lines = [];
    var line = '';
    var width = 0;
    for (var i = 0; i < text.length; i++) {
      var cw = fullWidth(text[i]) ? m.S : m.S / 2;
      if (width + cw > max && line.length > 0) {
        lines.push(line);
        line = '';
        width = 0;
      }
      line += text[i];
      width += cw;
    }
    if (line.length) lines.push(line);
    return lines;
  }

  function candidateRows(candidates, rowKeys, m) {
    var rows = [];
    var row = null;
    for (var i = 0; i < candidates.length; i++) {
      var keys = candKeys(candidates[i], rowKeys, m);
      var slots = candLines(candidates[i], rowKeys, m).length;
      if (!row || row.keys + keys > rowKeys) {
        row = { items: [], keys: 0, slots: 0 };
        rows.push(row);
      }
      row.items.push({ index: i, keys: keys });
      row.keys += keys;
      row.slots = Math.max(row.slots, slots);
    }
    return rows;
  }

  function candRowHeight(slots, m) {
    return slots * m.S + (slots - 1) * m.g;
  }

  function rowsFitting(rows, panelH, m) {
    var used = 0;
    var count = 0;
    for (var i = rows.length - 1; i >= 0; i--) {
      var need = candRowHeight(rows[i].slots, m) + (count > 0 ? m.g : 0);
      if (used + need > panelH) break;
      used += need;
      count++;
    }
    return count;
  }

  var api = {
    DEFAULTS: DEFAULTS,
    KB_RATIO: KB_RATIO,
    PRESETS: PRESETS,
    compute: compute,
    decideMode: decideMode,
    solvePortrait: solvePortrait,
    solveLandscape: solveLandscape,
    portraitMaxN: portraitMaxN,
    landscapeMaxN: landscapeMaxN,
    textWidth: textWidth,
    compactLabels: compactLabels,
    candCellBase: candCellBase,
    candKeys: candKeys,
    candLines: candLines,
    candidateRows: candidateRows,
    candRowHeight: candRowHeight,
    rowsFitting: rowsFitting
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  }
  if (typeof window !== 'undefined') {
    window.KBD = window.KBD || {};
    window.KBD.layout = api;
  }
})();
