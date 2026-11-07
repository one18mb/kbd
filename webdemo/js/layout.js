(function () {
  'use strict';

  // 布局常量：720p 下唯一主字号 32，行距/键距/列距都是 4。
  // 键高 = 3 行字 + 2 个行距 = 104，所有方向一致。
  var S = 32;
  var G = 4;
  var KEY_H = 3 * S + 2 * G;

  var SCREEN = {
    portrait: { W: 720, H: 1604 },
    landscape: { W: 1604, H: 720 }
  };

  function symbolSlots(orientation) {
    return orientation === 'portrait' ? 30 : 48;
  }

  // 竖屏：10 列全宽；键盘 = 候选行 + 3 键行 + 功能行 + 底部安全边距。
  function portrait(R) {
    var W = SCREEN.portrait.W;
    var H = SCREEN.portrait.H;
    var side = 2;
    var K = 68;
    var doubleW = 2 * K + G; // 140
    var barH = S; // 32
    var mainTop = barH + G; // 36
    var mainH = 3 * KEY_H + 2 * G; // 320
    var funcTop = mainTop + mainH + G; // 360
    var kbH = funcTop + KEY_H + R; // 496

    return {
      orientation: 'portrait', W: W, H: H, R: R, g: G,
      kbW: W, kbLeft: 0, kbH: kbH, side: side, K: K, double: doubleW,
      keyH: KEY_H, barH: barH, barGap: G, mainTop: mainTop, mainH: mainH, funcTop: funcTop,
      candCellW: doubleW, candGap: G, candSide: side, candH: S,
      expTop: mainTop, expH: mainH, expRows: 9,
      expCols: 5, expCellW: doubleW, expGap: G, expSide: side, expRowH: S,
      pinyinX: 0, pinyinW: W,
      symbolRows: [mainTop, mainTop + KEY_H + G, mainTop + 2 * (KEY_H + G)],
      symbolK: K, symbolGap: G, symbolSide: side,
      symbolSlots: symbolSlots('portrait')
    };
  }

  // 横屏：外列 = 双键宽，中间 6 列放候选/空格；键宽键高与竖屏一致。
  function landscape(R) {
    var W = SCREEN.landscape.W;
    var H = SCREEN.landscape.H;
    var side = 84; // 左右键盘边距（含屏幕安全区）
    var kbW = W - 2 * side; // 1436
    var K = 68;
    var outerW = 2 * K + G; // 140
    var letterCols = 5;
    var letterW = letterCols * K + (letterCols - 1) * G; // 356
    var letterX = outerW + G; // 144
    var midCols = 6;
    var midW = midCols * K + (midCols - 1) * G; // 428
    var midX = letterX + letterW + G; // 504
    var rightX = midX + midW + G; // 936
    var outerRightX = rightX + letterW + G; // 1296
    var kbH = 3 * KEY_H + 2 * G; // 320
    var rowY = [0, KEY_H + G, 2 * (KEY_H + G)];
    var panelH = 2 * KEY_H + G; // 212
    var panelCellW = 3 * K + 2 * G; // 212
    var expCols = 2;
    var expRows = 9;
    var symbolCols = 16;
    var symbolW = symbolCols * K + (symbolCols - 1) * G; // 1148
    var rowYs = [];
    for (var i = 0; i < expRows; i++) rowYs.push(i * (S + G));

    return {
      orientation: 'landscape', W: W, H: H, R: R, g: G,
      kbW: kbW, kbLeft: side, kbH: kbH, side: side, K: K, keyH: KEY_H,
      outerW: outerW, letterX: letterX, letterW: letterW, rightX: rightX, outerRightX: outerRightX,
      midX: midX, midW: midW, rowY: rowY,
      panelX: midX, panelW: midW, panelH: panelH,
      panelCellW: panelCellW, panelCols: 2, panelRows: 6, panelCellH: S, panelRowY: rowYs.slice(0, 6),
      spaceX: midX, spaceY: rowY[2], spaceW: midW, spaceH: KEY_H,
      expX: midX, expY: 0, expW: midW, expH: kbH,
      expCols: expCols, expCellW: panelCellW, expRows: expRows, expRowY: rowYs, expCellH: S,
      pinyinX: midX, pinyinW: midW,
      symbolX: letterX, symbolW: symbolW, symbolCols: symbolCols,
      symbolCellW: K, symbolRows: 3, symbolRowY: rowY,
      symbolSlots: symbolSlots('landscape')
    };
  }

  function compute(orientation, safeInset) {
    return orientation === 'portrait' ? portrait(safeInset) : landscape(safeInset);
  }

  window.KBD = window.KBD || {};
  window.KBD.layout = {
    S: S,
    G: G,
    KEY_H: KEY_H,
    SCREEN: SCREEN,
    symbolSlots: symbolSlots,
    compute: compute
  };
})();
