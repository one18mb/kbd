(function () {
  'use strict';

  const D = window.KBD;
  const LONG_PRESS_MS = 500;
  const REPEAT_MS = 60;
  const SLIDE_ENTER = 25;
  const SLIDE_EXIT = 12;
  const SWIPE_PX = 30;
  const MAX_ESCAPE = 16;
  const S = 32; // 720p 下唯一主字号：16 / 32 / 64 三选一

  const state = {
    orientation: 'portrait',
    theme: 'auto',
    safeInset: 16,
    lang: 'en',
    layer: 'letters',
    symbolPage: 0,
    escape: false,
    escapeBuffer: '\\',
    mods: {
      shift: { active: false, locked: false },
      ctrl: { active: false, locked: false },
      alt: { active: false, locked: false }
    },
    composing: '',
    candidates: [],
    preedit: '',
    expanded: false,
    text: '',
    cursor: 0,
    keyboardVisible: true,
    logOpen: false
  };

  let metrics = null;
  let keyEls = new Map();
  let sessions = new Map();
  let candidateBarEl = null;
  let candidatePanelEl = null;
  let expandedPanelEl = null;
  let floatingPreeditEl = null;
  let stageScale = 1;
  let deviceRect = null;
  let toastTimer = null;
  let lastKeyTap = { id: null, t: 0 };
  const media = window.matchMedia('(prefers-color-scheme: dark)');
  const logEntries = [];

  const els = {};

  function $(id) { return document.getElementById(id); }

  function cacheEls() {
    [
      'device', 'stageWrap', 'appScreen', 'editor', 'appMode', 'toast',
      'keyboard', 'hiddenBar', 'controls', 'orientationBtn', 'themeBtn',
      'demoBtn', 'symbolBtn', 'escapeBtn', 'showKbdBtn',
      'logBtn', 'resetBtn', 'fullscreenBtn', 'eventLog'
    ].forEach(function (id) { els[id] = $(id); });
  }

  // ---------- theme ----------

  function resolvedTheme() {
    if (state.theme === 'auto') return media.matches ? 'dark' : 'light';
    return state.theme;
  }

  function applyTheme() {
    const t = resolvedTheme();
    document.documentElement.setAttribute('data-theme', t);
    const label = state.theme === 'auto' ? '跟随系统' : (state.theme === 'dark' ? '深色' : '浅色');
    els.themeBtn.textContent = '主题：' + label;
  }

  function cycleTheme() {
    state.theme = state.theme === 'auto' ? 'dark' : (state.theme === 'dark' ? 'light' : 'auto');
    applyTheme();
  }

  // ---------- metrics ----------

  function computeMetrics() {
    const R = state.safeInset;
    const g = 4; // 字行距 = 键行距 = 键列距
    const keyH = 3 * S + 2 * g; // 104

    if (state.orientation === 'portrait') {
      const W = 720;
      const H = 1604;
      const side = 2;
      const K = 68;
      const doubleW = 2 * K + g; // 140
      const barH = 32;
      const mainTop = barH + g; // 36
      const mainH = 3 * keyH + 2 * g; // 320
      const lettersFuncTop = mainTop + mainH + g; // 360
      const symbolsFuncTop = mainH + g; // 324
      const isLetters = state.layer === 'letters';
      const funcTop = isLetters ? lettersFuncTop : symbolsFuncTop;
      const kbH = funcTop + keyH + R;

      return {
        orientation: 'portrait', W: W, H: H, R: R, g: g,
        kbW: W, kbLeft: 0, kbH: kbH, side: side, K: K, double: doubleW,
        keyH: keyH, barH: barH, barGap: g, mainTop: mainTop, mainH: mainH, funcTop: funcTop,
        candCellW: 140, candGap: g, candSide: side, candH: S,
        expTop: mainTop, expH: mainH, expRows: 9,
        expCols: 5, expCellW: 140, expGap: g, expSide: side, expRowH: S,
        pinyinX: 0, pinyinW: W,
        symbolRows: [0, keyH + g, 2 * (keyH + g)], symbolK: K, symbolGap: g, symbolSide: side
      };
    }

    const W = 1604;
    const H = 720;
    const side = 48; // 横屏左右键盘边距，含安全区
    const kbW = W - 2 * side; // 1508
    const K = 68;
    const cols = 21;
    const outerW = K;
    const letterCols = 5;
    const letterW = letterCols * K + (letterCols - 1) * g; // 356
    const letterX = outerW + g; // 72
    const midCols = 9;
    const midW = midCols * K + (midCols - 1) * g; // 644
    const midX = letterX + letterW + g; // 432
    const rightX = midX + midW + g; // 1080
    const outerRightX = rightX + letterW + g; // 1440
    const kbH = 3 * keyH + 2 * g; // 320
    const rowY = [0, keyH + g, 2 * (keyH + g)]; // 0,108,216
    const panelH = 2 * keyH + g; // 212
    const panelCellW = 3 * K + 2 * g; // 212
    const expH = kbH; // 320
    const expCellW = panelCellW; // 212
    const symbolX = letterX; // 72
    const symbolCols = 19;
    const symbolW = symbolCols * K + (symbolCols - 1) * g; // 1364

    return {
      orientation: 'landscape', W: W, H: H, R: R, g: g,
      kbW: kbW, kbLeft: side, kbH: kbH, side: side, K: K, keyH: keyH, cols: cols,
      outerW: outerW, letterX: letterX, letterW: letterW, rightX: rightX, outerRightX: outerRightX,
      midX: midX, midW: midW, rowY: rowY,
      panelX: midX, panelW: midW, panelH: panelH,
      panelCellW: panelCellW, panelCols: 3, panelRows: 6, panelCellH: S,
      panelRowY: [0, S + g, 2 * (S + g), 3 * (S + g), 4 * (S + g), 5 * (S + g)],
      spaceX: midX, spaceY: rowY[2], spaceW: midW, spaceH: keyH,
      expX: midX, expY: 0, expW: midW, expH: expH,
      expCols: 3, expCellW: expCellW, expRows: 9,
      expRowY: [0, S + g, 2 * (S + g), 3 * (S + g), 4 * (S + g), 5 * (S + g), 6 * (S + g), 7 * (S + g), 8 * (S + g)],
      expCellH: S,
      pinyinX: midX, pinyinW: midW,
      symbolX: symbolX, symbolW: symbolW, symbolCols: symbolCols, symbolCellW: K, symbolRows: 3, symbolRowY: rowY,
      candidateNormal: 18, candidateExpanded: 27, symbolPageSlots: 57
    };
  }

  function toLogicalY(clientY) {
    if (!deviceRect) return 0;
    return (clientY - deviceRect.top) / stageScale;
  }

  function toLogicalX(clientX) {
    if (!deviceRect) return 0;
    return (clientX - deviceRect.left) / stageScale;
  }

  function fitStage() {
    if (!metrics) return;
    const wrap = els.stageWrap;
    const availW = wrap.clientWidth;
    const availH = wrap.clientHeight;
    stageScale = Math.min(availW / metrics.W, availH / metrics.H, 1);
    els.device.style.width = metrics.W + 'px';
    els.device.style.height = metrics.H + 'px';
    els.device.style.transform = 'translate(-50%, -50%) scale(' + stageScale + ')';
    deviceRect = els.device.getBoundingClientRect();
  }

  // ---------- small helpers ----------

  function shiftActive() {
    return state.mods.shift.active;
  }

  function symbolSlots() {
    return state.orientation === 'portrait' ? 30 : 57;
  }

  function symbolPageCount() {
    return Math.max(1, Math.ceil(D.symbols.length / symbolSlots()));
  }

  function showToast(msg, kind) {
    if (!els.toast) return;
    els.toast.textContent = msg;
    els.toast.className = 'toast show' + (kind ? ' ' + kind : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      els.toast.className = 'toast';
    }, 1600);
  }

  function logEvent(text) {
    logEntries.push({ t: new Date(), text: text });
    if (logEntries.length > 80) logEntries.shift();
    renderLog();
  }

  function renderLog() {
    if (!els.eventLog) return;
    if (!state.logOpen) {
      els.eventLog.classList.add('hidden');
      return;
    }
    els.eventLog.classList.remove('hidden');
    els.eventLog.innerHTML = '';
    logEntries.slice().reverse().forEach(function (e) {
      const row = document.createElement('div');
      row.className = 'log-row';
      const time = e.t.toLocaleTimeString('zh-CN', { hour12: false });
      row.textContent = time + '  ' + e.text;
      els.eventLog.appendChild(row);
    });
  }

  // ---------- build ----------

  function rebuildAll() {
    metrics = computeMetrics();
    const m = metrics;
    els.device.style.width = m.W + 'px';
    els.device.style.height = m.H + 'px';
    els.device.setAttribute('data-orientation', m.orientation);
    els.appScreen.style.bottom = (state.keyboardVisible ? m.kbH : 0) + 'px';
    els.keyboard.style.left = m.kbLeft + 'px';
    els.keyboard.style.width = m.kbW + 'px';
    els.keyboard.style.height = m.kbH + 'px';
    els.keyboard.style.display = state.keyboardVisible ? 'block' : 'none';
    els.controls.classList.toggle('kbd-hidden', !state.keyboardVisible);
    buildKeyboard();
    renderEditor();
    updateAppMode();
    updateControlLabels();
    fitStage();
  }

  function buildKeyboard() {
    els.keyboard.innerHTML = '';
    keyEls.clear();
    candidateBarEl = null;
    candidatePanelEl = null;
    expandedPanelEl = null;
    floatingPreeditEl = null;
    if (!state.keyboardVisible) return;
    if (state.orientation === 'portrait') buildPortrait();
    else buildLandscape();
    renderCandidateContent();
    updateAllKeyLabels();
    updateAllKeyStates();
  }

  function makeKey(id, x, y, w, h) {
    const def = D.keys[id];
    if (!def) return null;
    const el = document.createElement('div');
    el.className = 'key';
    el.dataset.id = id;
    el.style.left = x + 'px';
    el.style.top = y + 'px';
    el.style.width = w + 'px';
    el.style.height = h + 'px';
    el.innerHTML = '<span class="up"></span><span class="main"></span><span class="down"></span>';
    el.setAttribute('role', 'button');
    el.setAttribute('aria-label', ariaName(id, def));
    if (!def.up && !def.down) el.classList.add('single');
    el.addEventListener('click', function (ev) {
      ev.stopPropagation();
      const now = Date.now();
      if (lastKeyTap.id === id && now - lastKeyTap.t < 500) return;
      tapKey(def);
    });
    els.keyboard.appendChild(el);
    keyEls.set(id, el);
    return el;
  }

  function ariaName(id, def) {
    const names = {
      arrowUp: '上方向键', arrowDown: '下方向键',
      arrowLeft: '左方向键', arrowRight: '右方向键',
      shift: 'Shift', ctrl: 'Ctrl', alt: 'Alt',
      lang: '中/英', space: '空格', backspace: '删除', enter: '确认'
    };
    if (names[id]) return names[id];
    if (def.kind === 'letter') return id;
    return (def.main && (def.main.en || def.main.zh)) || id;
  }

  function makeSymbolKey(sym, x, y, w, h) {
    const el = document.createElement('div');
    el.className = 'key single symbol-key' + (sym ? '' : ' empty');
    el.style.left = x + 'px';
    el.style.top = y + 'px';
    el.style.width = w + 'px';
    el.style.height = h + 'px';
    el.innerHTML = '<span class="main"></span>';
    if (sym) el.dataset.symbol = sym;
    el.setAttribute('role', 'button');
    if (sym) el.setAttribute('aria-label', '符号 ' + sym);
    el.querySelector('.main').textContent = sym || '';
    el.addEventListener('click', function (ev) {
      ev.stopPropagation();
      const tag = 'sym:' + (sym || '');
      const now = Date.now();
      if (lastKeyTap.id === tag && now - lastKeyTap.t < 500) return;
      if (sym) tapSymbol(sym);
    });
    els.keyboard.appendChild(el);
    return el;
  }

  function buildPortrait() {
    const m = metrics;

    if (state.layer === 'symbols') {
      buildPortraitSymbols();
    } else {
      buildPortraitCandidateBar();
      if (state.expanded) buildPortraitExpanded();
      else buildPortraitLetters();
    }

    buildPortraitFunctionRow(m.funcTop);
  }

  function buildPortraitCandidateBar() {
    const m = metrics;
    const bar = document.createElement('div');
    bar.className = 'candidate-bar';
    bar.style.height = m.barH + 'px';
    els.keyboard.appendChild(bar);
    candidateBarEl = bar;
  }

  function buildPortraitLetters() {
    const m = metrics;
    D.portraitRows.slice(0, 3).forEach(function (row, ri) {
      const y = m.mainTop + ri * (m.keyH + m.g);
      row.forEach(function (id, ci) {
        makeKey(id, m.side + ci * (m.K + m.g), y, m.K, m.keyH);
      });
    });
  }

  function buildPortraitSymbols() {
    const m = metrics;
    for (let r = 0; r < 3; r++) {
      const y = m.symbolRows[r];
      for (let c = 0; c < 10; c++) {
        const idx = state.symbolPage * 30 + r * 10 + c;
        const sym = idx < D.symbols.length ? D.symbols[idx] : '';
        makeSymbolKey(sym, m.symbolSide + c * (m.symbolK + m.symbolGap), y, m.symbolK, m.keyH);
      }
    }
  }

  function buildPortraitExpanded() {
    const m = metrics;
    const panel = document.createElement('div');
    panel.className = 'expanded-panel';
    panel.style.left = '0px';
    panel.style.top = m.expTop + 'px';
    panel.style.width = m.W + 'px';
    panel.style.height = m.expH + 'px';
    els.keyboard.appendChild(panel);
    expandedPanelEl = panel;
  }

  function buildPortraitFunctionRow(y) {
    const m = metrics;
    let x = m.side;
    D.portraitRows[3].forEach(function (id) {
      const def = D.keys[id];
      const w = def.double ? m.double : m.K;
      makeKey(id, x, y, w, m.keyH);
      x += w + m.g;
    });
  }

  function buildLandscape() {
    if (state.layer === 'symbols') buildLandscapeSymbols();
    else if (state.expanded) buildLandscapeExpanded();
    else buildLandscapeLetters();
  }

  function makeLandscapeRow(ri) {
    const m = metrics;
    const y = m.rowY[ri];
    makeKey(D.outerLeft[ri], 0, y, m.outerW, m.keyH);
    D.landLeft[ri].forEach(function (id, ci) {
      makeKey(id, m.letterX + ci * (m.K + m.g), y, m.K, m.keyH);
    });
    D.landRight[ri].forEach(function (id, ci) {
      makeKey(id, m.rightX + ci * (m.K + m.g), y, m.K, m.keyH);
    });
    makeKey(D.outerRight[ri], m.outerRightX, y, m.outerW, m.keyH);
  }

  function buildLandscapeLetters() {
    const m = metrics;
    for (let ri = 0; ri < 3; ri++) makeLandscapeRow(ri);
    makeKey('space', m.spaceX, m.spaceY, m.spaceW, m.spaceH);

    const panel = document.createElement('div');
    panel.className = 'candidate-panel';
    panel.style.left = m.panelX + 'px';
    panel.style.top = '0px';
    panel.style.width = m.panelW + 'px';
    panel.style.height = m.panelH + 'px';
    els.keyboard.appendChild(panel);
    candidatePanelEl = panel;
  }

  function buildLandscapeSymbols() {
    const m = metrics;
    for (let ri = 0; ri < 3; ri++) {
      const y = m.rowY[ri];
      makeKey(D.outerLeft[ri], 0, y, m.outerW, m.keyH);
      makeKey(D.outerRight[ri], m.outerRightX, y, m.outerW, m.keyH);
      for (let c = 0; c < m.symbolCols; c++) {
        const idx = state.symbolPage * symbolSlots() + ri * m.symbolCols + c;
        const sym = idx < D.symbols.length ? D.symbols[idx] : '';
        makeSymbolKey(
          sym,
          m.symbolX + c * (m.symbolCellW + m.g),
          y,
          m.symbolCellW,
          m.keyH
        );
      }
    }
  }

  function buildLandscapeExpanded() {
    const m = metrics;
    for (let ri = 0; ri < 3; ri++) makeLandscapeRow(ri);
    const panel = document.createElement('div');
    panel.className = 'expanded-panel';
    panel.style.left = m.expX + 'px';
    panel.style.top = m.expY + 'px';
    panel.style.width = m.expW + 'px';
    panel.style.height = m.expH + 'px';
    els.keyboard.appendChild(panel);
    expandedPanelEl = panel;
  }

  // ---------- floating preedit ----------

  function updateFloatingPreedit() {
    if (!state.keyboardVisible) return;
    const need = state.composing || state.escape;
    if (!need) {
      if (floatingPreeditEl) {
        floatingPreeditEl.remove();
        floatingPreeditEl = null;
      }
      return;
    }
    if (!floatingPreeditEl) {
      floatingPreeditEl = document.createElement('div');
      floatingPreeditEl.className = 'floating-preedit';
      const span = document.createElement('span');
      span.className = 'pinyin-text';
      floatingPreeditEl.appendChild(span);
      els.keyboard.appendChild(floatingPreeditEl);
    }
    if (state.orientation === 'landscape') {
      floatingPreeditEl.style.left = metrics.pinyinX + 'px';
      floatingPreeditEl.style.width = metrics.pinyinW + 'px';
    } else {
      floatingPreeditEl.style.left = '0px';
      floatingPreeditEl.style.width = '100%';
    }
    let text = '';
    if (state.composing) text = state.preedit;
    else if (state.escape) text = state.escapeBuffer;
    floatingPreeditEl.querySelector('.pinyin-text').textContent = text;
  }

  // ---------- labels / state ----------

  function keyMainText(id) {
    const def = D.keys[id];
    if (!def) return '';
    if (id === 'lang') {
      if (state.layer === 'symbols') return '符';
      return state.lang === 'zh' ? '中' : '英';
    }
    if (id === 'enter') {
      if (state.composing) return state.expanded ? '返回' : '展开';
      return '确认';
    }
    if (id === 'space') return '空格';
    if (def.kind === 'letter') return shiftActive() ? id.toUpperCase() : id;
    return (def.main && (def.main[state.lang] || def.main.en)) || '';
  }

  function keySlideText(def, dir) {
    const slide = dir === 'up' ? def.up : def.down;
    if (!slide) return '';
    const v = slide[state.lang] || slide.en;
    return v || '';
  }

  function updateKeyLabel(id) {
    const def = D.keys[id];
    const el = keyEls.get(id);
    if (!def || !el) return;
    const main = el.querySelector('.main');
    const up = el.querySelector('.up');
    const down = el.querySelector('.down');

    if (main) {
      main.textContent = keyMainText(id);
      main.style.fontSize = '';
    }

    if (id === 'lang' && state.layer === 'symbols') {
      el.classList.remove('single');
      if (up) up.textContent = String(state.symbolPage + 1);
      if (down) down.textContent = String(symbolPageCount());
    } else {
      if (up) up.textContent = keySlideText(def, 'up');
      if (down) down.textContent = keySlideText(def, 'down');
      if (!def.up && !def.down) el.classList.add('single');
    }
  }

  function updateAllKeyLabels() {
    keyEls.forEach(function (el, id) { updateKeyLabel(id); });
  }

  function updateKeyState(id) {
    const def = D.keys[id];
    const el = keyEls.get(id);
    if (!def || !el) return;
    if (def.kind === 'modifier') {
      const s = state.mods[def.mod];
      el.classList.toggle('active', s.active);
      el.classList.toggle('locked', s.locked);
    }
    if (id === 'lang') el.classList.toggle('active', state.layer === 'symbols');
    if (id === 'space') el.classList.toggle('active', state.escape);
  }

  function updateAllKeyStates() {
    keyEls.forEach(function (el, id) { updateKeyState(id); });
  }

  // ---------- candidate rendering ----------

  function makeCandCell(parent, text, x, y, w, h) {
    if (!parent || !text) return null;
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'cand-cell';
    b.style.left = x + 'px';
    b.style.top = y + 'px';
    b.style.width = w + 'px';
    b.style.height = h + 'px';
    const span = document.createElement('span');
    span.className = 'cand-text';
    span.textContent = text;
    b.appendChild(span);
    b.setAttribute('aria-label', '候选 ' + text);
    b.addEventListener('click', function (ev) {
      ev.stopPropagation();
      commitCandidate(text);
    });
    parent.appendChild(b);
    return b;
  }

  function escapePreview() {
    const buf = state.escapeBuffer;
    const body = buf.slice(1);
    if (body === '') {
      return {
        help: true,
        text: 'n 换行   t 制表   u Unicode   s 设置   j 简繁'
      };
    }
    const cmd = D.escapeCommands[body];
    if (cmd) {
      return { valid: true, text: '→ ' + cmd.label, cmd: cmd };
    }
    let code = null;
    const m4 = /^u([0-9a-fA-F]{4})$/.exec(body);
    const mb = /^u\{([0-9a-fA-F]{1,6})\}$/.exec(body);
    if (m4) code = parseInt(m4[1], 16);
    else if (mb) code = parseInt(mb[1], 16);
    if (code != null) {
      if (code <= 0x10FFFF && !(code >= 0xD800 && code <= 0xDFFF)) {
        const ch = String.fromCodePoint(code);
        return { valid: true, text: '→ ' + ch, value: ch };
      }
      return { valid: false, text: '无效 Unicode' };
    }
    if (/^u/i.test(body)) return { valid: false, text: 'Unicode 需要 4 位或 {1–6} 位十六进制' };
    if (body === '\\') return { valid: true, text: '→ \\', value: '\\' };
    return { valid: false, text: '未知转义' };
  }

  function renderCandidateContent() {
    if (candidateBarEl) renderPortraitCandidateBar();
    if (candidatePanelEl) renderLandscapeCandidatePanel();
    if (expandedPanelEl) renderExpandedPanel();
    updateFloatingPreedit();
  }

  function renderPortraitCandidateBar() {
    const bar = candidateBarEl;
    const m = metrics;
    bar.innerHTML = '';
    if (!state.composing) return;
    for (let i = 0; i < 5; i++) {
      makeCandCell(
        bar,
        state.candidates[i],
        m.candSide + i * (m.candCellW + m.candGap),
        0,
        m.candCellW,
        m.candH
      );
    }
  }

  function renderLandscapeCandidatePanel() {
    const panel = candidatePanelEl;
    const m = metrics;
    panel.innerHTML = '';
    if (!state.composing) return;
    for (let r = 0; r < m.panelRows; r++) {
      for (let c = 0; c < m.panelCols; c++) {
        const idx = r * m.panelCols + c;
        makeCandCell(
          panel,
          state.candidates[idx],
          c * (m.panelCellW + m.g),
          m.panelRowY[r],
          m.panelCellW,
          m.panelCellH
        );
      }
    }
  }

  function renderExpandedPanel() {
    const panel = expandedPanelEl;
    const m = metrics;
    panel.innerHTML = '';

    const bg = document.createElement('div');
    bg.className = 'expanded-candidate-bg';
    bg.style.left = '0px';
    bg.style.top = '0px';
    bg.style.width = '100%';
    bg.style.height = m.expH + 'px';
    panel.appendChild(bg);

    if (state.orientation === 'portrait') {
      // 顶部常驻候选行已显示前 5 个，展开区从第 6 个开始，补满 9 行。
      for (let r = 0; r < m.expRows; r++) {
        for (let c = 0; c < m.expCols; c++) {
          const idx = 5 + r * m.expCols + c;
          makeCandCell(
            panel,
            state.candidates[idx],
            m.expSide + c * (m.expCellW + m.expGap),
            r * (S + m.g),
            m.expCellW,
            S
          );
        }
      }
      return;
    }

    for (let r = 0; r < m.expRows; r++) {
      for (let c = 0; c < m.expCols; c++) {
        const idx = r * m.expCols + c;
        makeCandCell(
          panel,
          state.candidates[idx],
          c * (m.expCellW + m.g),
          r * (S + m.g),
          m.expCellW,
          S
        );
      }
    }
  }

  // ---------- editor ----------

  function updateAppMode() {
    if (!els.appMode) return;
    els.appMode.textContent = '';
  }

  function appendTextNode(parent, text) {
    if (text) parent.appendChild(document.createTextNode(text));
  }

  function renderEditor() {
    const editor = els.editor;
    editor.innerHTML = '';
    const text = state.text;
    const cursor = state.cursor;
    const before = text.slice(0, cursor);
    const after = text.slice(cursor);
    appendTextNode(editor, before);
    const caret = document.createElement('span');
    caret.className = 'caret';
    editor.appendChild(caret);
    appendTextNode(editor, after);
    requestAnimationFrame(function () {
      const er = editor.getBoundingClientRect();
      const cr = caret.getBoundingClientRect();
      if (cr.bottom > er.bottom - 10) editor.scrollTop += cr.bottom - er.bottom + 40;
      else if (cr.top < er.top + 10) editor.scrollTop -= er.top - cr.top + 40;
    });
  }

  function insertText(str) {
    state.text = state.text.slice(0, state.cursor) + str + state.text.slice(state.cursor);
    state.cursor += str.length;
    renderEditor();
  }

  function prevCharIndex(text, idx) {
    if (idx <= 0) return 0;
    const c = text.charCodeAt(idx - 1);
    if (c >= 0xDC00 && c <= 0xDFFF && idx >= 2) return idx - 2;
    return idx - 1;
  }

  function nextCharIndex(text, idx) {
    if (idx >= text.length) return text.length;
    const c = text.charCodeAt(idx);
    if (c >= 0xD800 && c <= 0xDBFF && idx + 1 < text.length) return idx + 2;
    return idx + 1;
  }

  function backspaceText() {
    if (state.cursor <= 0) return;
    const p = prevCharIndex(state.text, state.cursor);
    state.text = state.text.slice(0, p) + state.text.slice(state.cursor);
    state.cursor = p;
    renderEditor();
  }

  // ---------- pinyin ----------

  function lookupPinyin(raw) {
    const exact = D.pinyin.find(function (e) { return e.p === raw; });
    if (exact) return { pre: exact.pre, c: exact.c.slice() };
    const matches = D.pinyin.filter(function (e) { return e.p.indexOf(raw) === 0; });
    if (matches.length) {
      matches.sort(function (a, b) { return b.p.length - a.p.length; });
      return { pre: raw, c: matches[0].c.slice() };
    }
    return { pre: raw, c: [raw] };
  }

  function updateComposition() {
    const wasExpanded = state.expanded;
    if (!state.composing) {
      state.preedit = '';
      state.candidates = [];
      state.expanded = false;
    } else {
      const r = lookupPinyin(state.composing);
      state.preedit = r.pre;
      state.candidates = r.c;
    }
    if (wasExpanded && !state.composing) {
      rebuildAll();
      return;
    }
    renderCandidateContent();
    updateAllKeyLabels();
    updateAppMode();
  }

  function commitComposition(str) {
    const wasExpanded = state.expanded;
    insertText(str);
    state.composing = '';
    state.preedit = '';
    state.candidates = [];
    state.expanded = false;
    if (wasExpanded) {
      rebuildAll();
      return;
    }
    renderCandidateContent();
    updateAllKeyLabels();
    updateAppMode();
  }

  function commitCandidate(text) {
    if (!state.composing) return;
    logEvent('选词：' + text);
    commitComposition(text);
  }

  function commitFirstCandidate() {
    if (!state.composing) return false;
    const first = state.candidates[0] || state.composing;
    commitComposition(first);
    return true;
  }

  function appendPinyin(letter) {
    if (state.composing.length >= 40) {
      showToast('拼音缓冲已达单行上限');
      return;
    }
    state.composing += letter.toLowerCase();
    updateComposition();
  }

  function deletePinyinOrChar() {
    if (state.composing) {
      state.composing = state.composing.slice(0, -1);
      updateComposition();
      return;
    }
    backspaceText();
  }

  function setExpanded(v) {
    if (state.expanded === v) return;
    state.expanded = v;
    rebuildAll();
    logEvent(v ? '展开候选' : '返回候选');
  }

  // ---------- modifier state ----------

  function clearTempMods() {
    let changed = false;
    Object.keys(state.mods).forEach(function (k) {
      const s = state.mods[k];
      if (s.active && !s.locked) {
        s.active = false;
        changed = true;
      }
    });
    if (changed) {
      updateAllKeyLabels();
      updateAllKeyStates();
    }
  }

  function toggleModifier(name) {
    const s = state.mods[name];
    if (s.locked) {
      s.active = false;
      s.locked = false;
    } else {
      s.active = !s.active;
      s.locked = false;
    }
    updateAllKeyLabels();
    updateAllKeyStates();
  }

  function lockModifier(name) {
    const s = state.mods[name];
    s.active = true;
    s.locked = true;
    updateAllKeyLabels();
    updateAllKeyStates();
  }

  // ---------- escape ----------

  function enterEscape() {
    if (state.composing) {
      showToast('先上屏拼音');
      return;
    }
    state.escape = true;
    state.escapeBuffer = '\\';
    clearTempMods();
    updateAllKeyLabels();
    updateAllKeyStates();
    renderCandidateContent();
    updateAppMode();
  }

  function exitEscape() {
    state.escape = false;
    state.escapeBuffer = '\\';
    updateAllKeyLabels();
    updateAllKeyStates();
    renderCandidateContent();
    updateAppMode();
  }

  function appendEscape(ch) {
    if (state.escapeBuffer.length >= MAX_ESCAPE) return;
    state.escapeBuffer += ch;
    updateFloatingPreedit();
  }

  function executeEscape() {
    const info = escapePreview();
    if (!info.valid) return;
    const cmd = info.cmd;
    if (cmd) {
      if (cmd.type === 'text') {
        insertText(cmd.value);
        logEvent('转义：' + info.text);
      } else if (cmd.value === 'settings') {
        logEvent('转义：设置');
      } else if (cmd.value === 'variant') {
        logEvent('转义：简繁');
      }
    } else if (info.value != null) {
      insertText(info.value);
      logEvent('转义：' + info.value);
    }
    exitEscape();
  }

  // ---------- actions ----------

  function emitChar(label, source) {
    if (state.escape) {
      if (label.length === 1) {
        appendEscape(label);
        return;
      }
      if (label === 'Tab' || label === 'Esc') {
        showToast('转义模式不接受 ' + label);
        return;
      }
      appendEscape(label);
      return;
    }
    if (state.layer === 'symbols') {
      insertText(label);
      logEvent('符号：' + label);
      return;
    }
    if (state.composing) {
      showToast('先上屏拼音');
      clearTempMods();
      return;
    }
    if (label === 'Tab') {
      insertText('\t');
      logEvent('Tab');
    } else if (label === 'Esc') {
      logEvent('Esc');
      showToast('Esc 事件（原型）');
    } else {
      insertText(label);
      logEvent('字符：' + label);
    }
    clearTempMods();
  }

  function letterAction(id) {
    if (state.escape) {
      appendEscape(id);
      if (shiftActive()) clearTempMods();
      return;
    }
    const shift = shiftActive();
    const ctrl = state.mods.ctrl.active;
    const alt = state.mods.alt.active;
    if (ctrl || alt) {
      const chord = (ctrl ? 'Ctrl+' : '') + (alt ? 'Alt+' : '') + (shift ? id.toUpperCase() : id);
      logEvent(chord);
      showToast(chord);
      clearTempMods();
      return;
    }
    if (state.lang === 'zh') {
      if (state.composing) {
        if (shift) {
          showToast('先上屏拼音');
          clearTempMods();
        } else {
          appendPinyin(id);
        }
        return;
      }
      if (shift) {
        insertText(id.toUpperCase());
        logEvent('大写英文：' + id.toUpperCase());
        clearTempMods();
        return;
      }
      appendPinyin(id);
      return;
    }
    const ch = shift ? id.toUpperCase() : id;
    insertText(ch);
    logEvent('字母：' + ch);
    clearTempMods();
  }

  function navAction(dir) {
    if (state.composing || state.escape) {
      showToast('先上屏拼音');
      clearTempMods();
      return;
    }
    if (dir === 'left') state.cursor = prevCharIndex(state.text, state.cursor);
    else if (dir === 'right') state.cursor = nextCharIndex(state.text, state.cursor);
    else if (dir === 'up') {
      const ls = state.text.lastIndexOf('\n', state.cursor - 1);
      const lineStart = ls >= 0 ? ls + 1 : 0;
      const col = state.cursor - lineStart;
      if (ls >= 0) {
        const prevLineStart = state.text.lastIndexOf('\n', ls - 1) + 1;
        state.cursor = Math.min(prevLineStart + col, ls);
      } else state.cursor = 0;
    } else if (dir === 'down') {
      const ls = state.text.lastIndexOf('\n', state.cursor - 1);
      const lineStart = ls >= 0 ? ls + 1 : 0;
      const col = state.cursor - lineStart;
      const nextNl = state.text.indexOf('\n', state.cursor);
      if (nextNl >= 0) {
        const nextStart = nextNl + 1;
        const nextEnd = state.text.indexOf('\n', nextStart);
        const end = nextEnd >= 0 ? nextEnd : state.text.length;
        state.cursor = Math.min(nextStart + col, end);
      } else state.cursor = state.text.length;
    }
    renderEditor();
    clearTempMods();
  }

  function tapKey(def) {
    const id = def.id;
    if (def.kind === 'modifier') {
      toggleModifier(def.mod);
      return;
    }
    if (def.kind === 'lang') {
      shortLang();
      return;
    }
    if (def.kind === 'space') {
      if (state.escape) executeEscape();
      else if (state.composing) { commitFirstCandidate(); clearTempMods(); }
      else { insertText(' '); clearTempMods(); }
      return;
    }
    if (def.kind === 'backspace') {
      if (state.escape) {
        if (state.escapeBuffer.length > 1) {
          state.escapeBuffer = state.escapeBuffer.slice(0, -1);
          updateFloatingPreedit();
        }
      } else if (state.composing) deletePinyinOrChar();
      else backspaceText();
      clearTempMods();
      return;
    }
    if (def.kind === 'enter') {
      if (state.escape) { showToast('转义模式中确认键无效'); return; }
      if (state.composing) {
        setExpanded(!state.expanded);
        clearTempMods();
        return;
      }
      insertText('\n');
      logEvent('换行');
      clearTempMods();
      return;
    }
    if (def.kind === 'nav') {
      const ctrl = state.mods.ctrl.active;
      const alt = state.mods.alt.active;
      if (ctrl || alt) {
        logEvent((ctrl ? 'Ctrl+' : '') + (alt ? 'Alt+' : '') + def.main.en);
      }
      navAction(def.dir);
      return;
    }
    if (def.kind === 'letter') letterAction(id);
  }

  function shortLang() {
    if (state.layer === 'symbols') {
      exitSymbolMode();
      return;
    }
    if (state.composing) {
      showToast('先上屏拼音');
      return;
    }
    state.lang = state.lang === 'zh' ? 'en' : 'zh';
    updateAllKeyLabels();
    updateAllKeyStates();
    renderCandidateContent();
    updateAppMode();
    logEvent('切换语言：' + (state.lang === 'zh' ? '中文' : '英文'));
  }

  function enterSymbolMode() {
    if (state.composing) {
      showToast('先上屏拼音');
      return;
    }
    if (state.escape && state.orientation === 'landscape') {
      exitEscape();
      showToast('横屏符号模式与转义互斥');
    }
    state.layer = 'symbols';
    state.symbolPage = 0;
    state.expanded = false;
    clearTempMods();
    rebuildAll();
    logEvent('进入符号模式');
  }

  function exitSymbolMode() {
    state.layer = 'letters';
    clearTempMods();
    rebuildAll();
    logEvent('退出符号模式');
  }

  function longLang() {
    if (state.layer === 'symbols') exitSymbolMode();
    else enterSymbolMode();
  }

  function longSpace() {
    if (state.escape) {
      exitEscape();
      logEvent('退出转义');
      return;
    }
    if (state.composing) {
      showToast('先上屏拼音');
      return;
    }
    enterEscape();
    logEvent('进入转义');
  }

  function longEnter() {
    if (state.composing) {
      showToast('先上屏拼音');
      return;
    }
    hideKeyboard();
  }

  function hideKeyboard() {
    state.keyboardVisible = false;
    rebuildAll();
    logEvent('收起键盘');
  }

  function showKeyboard() {
    state.keyboardVisible = true;
    rebuildAll();
    logEvent('显示键盘');
  }

  function flipSymbolPage(direction) {
    const count = symbolPageCount();
    if (count <= 1) return;
    state.symbolPage = (state.symbolPage + direction + count) % count;
    rebuildAll();
    logEvent('符号页 → ' + (state.symbolPage + 1) + '/' + count);
  }

  // ---------- pointer handling ----------

  function findSymbolSession(el) {
    return el && el.classList.contains('symbol-key') && el.dataset.symbol;
  }

  function onPointerDown(e) {
    const key = e.target.closest('.key');
    if (!key) return;
    if (sessions.size >= 2) return;
    if (key.classList.contains('empty')) return;
    e.preventDefault();
    const id = key.dataset.id;
    const symbol = findSymbolSession(key);
    if (!id && !symbol) return;
    try { els.keyboard.setPointerCapture(e.pointerId); } catch (err) {}
    const session = {
      pointerId: e.pointerId,
      keyEl: key,
      keyId: id || null,
      symbol: symbol || null,
      startY: toLogicalY(e.clientY),
      startX: toLogicalX(e.clientX),
      mode: symbol ? 'symbol-pending' : 'pending',
      dir: null,
      timer: null,
      repeatTimer: null,
      pressed: true
    };
    if (symbol) {
      session.mode = 'symbol-pending';
      key.classList.add('pressed');
    } else {
      const def = D.keys[id];
      key.classList.add('pressed');
      key.classList.add('pressing');
      if (def && (def.long || def.repeat)) {
        session.timer = setTimeout(function () {
          session.timer = null;
          if (session.mode !== 'pending') return;
          if (def.repeat) {
            session.mode = 'repeat';
            doRepeatAction(def);
            session.repeatTimer = setInterval(function () { doRepeatAction(def); }, REPEAT_MS);
          } else {
            session.mode = 'long';
            doLongAction(def);
          }
        }, LONG_PRESS_MS);
      }
    }
    sessions.set(e.pointerId, session);
  }

  function doRepeatAction(def) {
    if (!def) return;
    if (def.kind === 'backspace') {
      if (state.escape) {
        if (state.escapeBuffer.length > 1) {
          state.escapeBuffer = state.escapeBuffer.slice(0, -1);
          updateFloatingPreedit();
        }
      } else if (state.composing) deletePinyinOrChar();
      else backspaceText();
      return;
    }
    if (def.kind === 'nav') {
      if (state.composing || state.escape) return;
      if (def.dir === 'left') state.cursor = prevCharIndex(state.text, state.cursor);
      else if (def.dir === 'right') state.cursor = nextCharIndex(state.text, state.cursor);
      renderEditor();
    }
  }

  function doLongAction(def) {
    if (def.kind === 'modifier') lockModifier(def.mod);
    else if (def.kind === 'space') longSpace();
    else if (def.kind === 'lang') longLang();
    else if (def.kind === 'enter') longEnter();
  }

  function onPointerMove(e) {
    const s = sessions.get(e.pointerId);
    if (!s) return;
    if (s.mode === 'long' || s.mode === 'repeat') return;
    const y = toLogicalY(e.clientY);
    if (s.symbol) {
      if (Math.abs(y - s.startY) >= SWIPE_PX) {
        s.mode = 'swipe';
        s.dir = y < s.startY ? 'up' : 'down';
        s.keyEl.classList.remove('pressed');
      }
      return;
    }
    const def = D.keys[s.keyId];
    if (!def || (!def.up && !def.down)) return;
    const dy = y - s.startY;
    if (s.mode === 'pending') {
      if (dy <= -SLIDE_ENTER) setSlide(s, 'up');
      else if (dy >= SLIDE_ENTER) setSlide(s, 'down');
      return;
    }
    if (s.mode === 'slide') {
      if (s.dir === 'up' && dy > -SLIDE_EXIT) clearSlide(s);
      else if (s.dir === 'down' && dy < SLIDE_EXIT) clearSlide(s);
      else if (s.dir === 'up' && dy >= SLIDE_ENTER) setSlide(s, 'down');
      else if (s.dir === 'down' && dy <= -SLIDE_ENTER) setSlide(s, 'up');
    }
  }

  function setSlide(s, dir) {
    const def = D.keys[s.keyId];
    if (!def) return;
    if (s.timer) { clearTimeout(s.timer); s.timer = null; }
    s.mode = 'slide';
    s.dir = dir;
    s.keyEl.classList.remove('pressing', 'slide-up', 'slide-down', 'null-slide');
    s.keyEl.classList.add(dir === 'up' ? 'slide-up' : 'slide-down');
    const label = keySlideText(def, dir);
    if (!label) s.keyEl.classList.add('null-slide');
  }

  function clearSlide(s) {
    s.mode = 'pending';
    s.dir = null;
    s.keyEl.classList.remove('slide-up', 'slide-down', 'null-slide');
    s.keyEl.classList.add('pressing');
  }

  function cancelSession(s) {
    if (!s) return;
    if (s.timer) clearTimeout(s.timer);
    if (s.repeatTimer) clearInterval(s.repeatTimer);
    s.keyEl.classList.remove('pressed', 'pressing', 'slide-up', 'slide-down', 'null-slide');
    sessions.delete(s.pointerId);
  }

  function tapSymbol(sym) {
    if (state.escape) {
      appendEscape(sym);
      return;
    }
    insertText(sym);
    logEvent('符号：' + sym);
    clearTempMods();
  }

  function onPointerUp(e) {
    const s = sessions.get(e.pointerId);
    if (!s) return;
    if (s.timer) { clearTimeout(s.timer); s.timer = null; }
    if (s.repeatTimer) { clearInterval(s.repeatTimer); s.repeatTimer = null; }
    s.keyEl.classList.remove('pressed', 'pressing', 'slide-up', 'slide-down', 'null-slide');
    sessions.delete(e.pointerId);
    try { els.keyboard.releasePointerCapture(e.pointerId); } catch (err) {}

    lastKeyTap = {
      id: s.symbol ? ('sym:' + s.symbol) : s.keyId,
      t: Date.now()
    };

    if (s.symbol) {
      if (s.mode === 'swipe') {
        flipSymbolPage(s.dir === 'up' ? 1 : -1);
      } else {
        tapSymbol(s.symbol);
      }
      return;
    }

    if (s.mode === 'pending') {
      const def = D.keys[s.keyId];
      if (def) tapKey(def);
      return;
    }
    if (s.mode === 'slide') {
      const def = D.keys[s.keyId];
      if (!def) return;
      const label = keySlideText(def, s.dir);
      if (!label) {
        logEvent('空滑动：' + s.keyId);
        clearTempMods();
        return;
      }
      emitChar(label, 'slide');
      return;
    }
  }

  function onPointerCancel(e) {
    const s = sessions.get(e.pointerId);
    if (s) cancelSession(s);
  }

  // ---------- controls ----------

  function updateControlLabels() {
    els.orientationBtn.textContent = state.orientation === 'portrait' ? '横屏预览' : '竖屏预览';
    els.showKbdBtn.textContent = state.keyboardVisible ? '收起键盘' : '显示键盘';
    els.logBtn.textContent = state.logOpen ? '隐藏日志' : '事件日志';
  }

  function toggleOrientation() {
    state.orientation = state.orientation === 'portrait' ? 'landscape' : 'portrait';
    if (state.orientation === 'landscape' && state.layer === 'symbols' && state.escape) {
      state.escape = false;
      state.escapeBuffer = '\\';
    }
    state.symbolPage = Math.min(state.symbolPage, symbolPageCount() - 1);
    if (state.symbolPage < 0) state.symbolPage = 0;
    rebuildAll();
    logEvent('方向：' + (state.orientation === 'portrait' ? '竖屏' : '横屏'));
  }

  function demoNihao() {
    state.lang = 'zh';
    state.layer = 'letters';
    state.escape = false;
    state.escapeBuffer = '\\';
    state.composing = 'nihao';
    state.expanded = false;
    rebuildAll();
    updateComposition();
    logEvent("演示：ni'hao");
  }

  function resetAll() {
    state.lang = 'en';
    state.layer = 'letters';
    state.symbolPage = 0;
    state.escape = false;
    state.escapeBuffer = '\\';
    state.mods.shift.active = false; state.mods.shift.locked = false;
    state.mods.ctrl.active = false; state.mods.ctrl.locked = false;
    state.mods.alt.active = false; state.mods.alt.locked = false;
    state.composing = '';
    state.candidates = [];
    state.preedit = '';
    state.expanded = false;
    state.text = '';
    state.cursor = 0;
    rebuildAll();
    logEvent('重置');
  }

  function on(el, ev, fn, opts) {
    if (el) el.addEventListener(ev, fn, opts);
  }

  function bindControls() {
    on(els.orientationBtn, 'click', toggleOrientation);
    on(els.themeBtn, 'click', cycleTheme);
    on(els.demoBtn, 'click', demoNihao);
    on(els.symbolBtn, 'click', function () {
      if (state.layer === 'symbols') exitSymbolMode(); else enterSymbolMode();
    });
    on(els.escapeBtn, 'click', function () {
      if (state.escape) exitEscape(); else enterEscape();
    });
    on(els.showKbdBtn, 'click', function () {
      if (state.keyboardVisible) hideKeyboard(); else showKeyboard();
    });
    on(els.logBtn, 'click', function () {
      state.logOpen = !state.logOpen;
      renderLog();
      updateControlLabels();
      fitStage();
    });
    on(els.resetBtn, 'click', resetAll);
    on(els.fullscreenBtn, 'click', function () {
      if (document.fullscreenElement) document.exitFullscreen();
      else document.documentElement.requestFullscreen().catch(function () {});
    });
    on(els.keyboard, 'pointerdown', onPointerDown, { passive: false });
    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
    window.addEventListener('pointercancel', onPointerCancel);
    window.addEventListener('resize', fitStage);
    if (media.addEventListener) {
      media.addEventListener('change', function () {
        if (state.theme === 'auto') applyTheme();
      });
    }
  }

  // ---------- init ----------

  function init() {
    cacheEls();
    bindControls();
    applyTheme();
    rebuildAll();
    renderLog();
    logEvent('KBD 原型启动');
  }

  init();
})();
