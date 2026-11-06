(function () {
  'use strict';

  const D = window.KBD;
  const PAGE_SIZE = 5;
  const LONG_PRESS_MS = 500;
  const REPEAT_MS = 60;
  const SLIDE_ENTER = 25;
  const SLIDE_EXIT = 12;
  const SWIPE_PX = 30;
  const MAX_ESCAPE = 16;

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
    candPage: 0,
    preedit: '',
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
  let stageScale = 1;
  let deviceRect = null;
  let toastTimer = null;
  let suppressCandClick = false;
  let lastKeyTap = { id: null, t: 0 };
  const media = window.matchMedia('(prefers-color-scheme: dark)');
  const logEntries = [];

  const els = {};

  function $(id) { return document.getElementById(id); }

  function cacheEls() {
    [
      'device', 'stageWrap', 'appScreen', 'editor', 'appMode', 'toast',
      'keyboard', 'hiddenBar', 'controls', 'orientationBtn', 'themeBtn',
      'demoBtn', 'symbolBtn', 'escapeBtn', 'safeInset', 'safeLabel', 'showKbdBtn',
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
    if (state.orientation === 'portrait') {
      const W = 720;
      const H = 1604;
      const barH = 120;
      const keyH = 120;
      const gap = 4;
      const side = 2;
      const K = 68;
      const doubleW = 140;
      const kbH = barH + 4 * keyH + 3 * gap + R;
      return {
        orientation: 'portrait', W: W, H: H, kbW: W, kbLeft: 0, kbH: kbH,
        barH: barH, keyH: keyH, gap: gap, side: side, K: K, double: doubleW, R: R
      };
    }
    const W = 1604;
    const H = 720;
    const W_eff = W - 2 * R;
    const F = 120;
    const K = 76;
    const keyH = 100;
    const gap = 4;
    const C = W_eff - 2 * F - 10 * K - 12 * gap;
    const panelW = C - (K + gap);
    const kbH = 3 * keyH + 2 * gap;
    return {
      orientation: 'landscape', W: W, H: H, kbW: W_eff, kbLeft: R, kbH: kbH,
      F: F, K: K, keyH: keyH, gap: gap, C: C, panelW: panelW,
      panelH: 204, R: R
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

  function splitInts(total, count, gap) {
    const avail = total - (count - 1) * (gap || 0);
    const base = Math.floor(avail / count);
    const rem = avail - base * count;
    const out = [];
    for (let i = 0; i < count; i++) out.push(base + (i < rem ? 1 : 0));
    return out;
  }

  function shiftActive() {
    return state.mods.shift.active;
  }

  function symbolSlots() {
    return state.orientation === 'portrait' ? 30 : 60;
  }

  function symbolPageCount() {
    return Math.max(1, Math.ceil(D.symbols.length / symbolSlots()));
  }

  function candPageCount() {
    return Math.max(1, Math.ceil(state.candidates.length / PAGE_SIZE));
  }

  function visibleCandidates() {
    const start = state.candPage * PAGE_SIZE;
    return state.candidates.slice(start, start + PAGE_SIZE);
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
    els.device.style.setProperty('--main-size', (m.orientation === 'portrait' ? '32px' : '28px'));
    els.device.style.setProperty('--sub-size', (m.orientation === 'portrait' ? '32px' : '22px'));
    els.device.style.setProperty('--preedit-size', (m.orientation === 'portrait' ? '32px' : '22px'));
    els.device.style.setProperty('--cand-size', (m.orientation === 'portrait' ? '28px' : '24px'));
    els.device.style.setProperty('--panel-cand-size', '24px');
    els.appScreen.style.bottom = (state.keyboardVisible ? m.kbH : 0) + 'px';
    els.keyboard.style.left = m.kbLeft + 'px';
    els.keyboard.style.width = m.kbW + 'px';
    els.keyboard.style.height = m.kbH + 'px';
    els.keyboard.style.display = state.keyboardVisible ? 'block' : 'none';
    els.hiddenBar.classList.toggle('hidden', state.keyboardVisible);
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

  function buildCandidateBarPortrait() {
    const bar = document.createElement('div');
    bar.className = 'candidate-bar';
    bar.innerHTML = '<div class="preedit"></div><div class="cand-row"></div>';
    els.keyboard.appendChild(bar);
    candidateBarEl = bar;
  }

  function buildPortrait() {
    const m = metrics;
    buildCandidateBarPortrait();
    if (state.layer === 'letters') {
      D.portraitRows.forEach(function (row, ri) {
        let x = m.side;
        const y = m.barH + ri * (m.keyH + m.gap);
        row.forEach(function (id) {
          const def = D.keys[id];
          const w = def.double ? m.double : m.K;
          makeKey(id, x, y, w, m.keyH);
          x += w + m.gap;
        });
      });
    } else {
      for (let r = 0; r < 3; r++) {
        let x = m.side;
        const y = m.barH + r * (m.keyH + m.gap);
        for (let c = 0; c < 10; c++) {
          const idx = state.symbolPage * 30 + r * 10 + c;
          makeSymbolKey(idx < D.symbols.length ? D.symbols[idx] : '', x, y, m.K, m.keyH);
          x += m.K + m.gap;
        }
      }
      let x = m.side;
      const y = m.barH + 3 * (m.keyH + m.gap);
      D.portraitRows[3].forEach(function (id) {
        const def = D.keys[id];
        const w = def.double ? m.double : m.K;
        makeKey(id, x, y, w, m.keyH);
        x += w + m.gap;
      });
    }
  }

  function buildLandscape() {
    const m = metrics;

    if (state.layer === 'letters') {
      for (let ri = 0; ri < 3; ri++) {
        const y = ri * (m.keyH + m.gap);
        makeKey(D.outerLeft[ri], 0, y, m.F, m.keyH);

        let x = m.F + m.gap;
        D.landLeft[ri].forEach(function (id) {
          makeKey(id, x, y, m.K, m.keyH);
          x += m.K + m.gap;
        });

        if (ri === 2) {
          const row3Left = D.landLeft[2];
          const spaceX = m.F + m.gap + row3Left.length * m.K + (row3Left.length - 1) * m.gap + m.gap;
          makeKey('space', spaceX, y, m.C, m.keyH);
        }

        const rightRow = D.landRight[ri];
        const total = rightRow.length * m.K + (rightRow.length - 1) * m.gap;
        let rx = m.kbW - m.F - m.gap - total;
        rightRow.forEach(function (id) {
          makeKey(id, rx, y, m.K, m.keyH);
          rx += m.K + m.gap;
        });

        makeKey(D.outerRight[ri], m.kbW - m.F, y, m.F, m.keyH);
      }

      const panelX = m.F + m.gap + 6 * m.K + 5 * m.gap + m.gap;
      const panel = document.createElement('div');
      panel.className = 'candidate-panel';
      panel.style.left = panelX + 'px';
      panel.style.top = '0px';
      panel.style.width = m.panelW + 'px';
      panel.style.height = m.panelH + 'px';
      panel.innerHTML = '<div class="panel-status"></div><div class="panel-top panel-row"></div><div class="panel-bottom panel-row"></div>';
      els.keyboard.appendChild(panel);
      candidatePanelEl = panel;
    } else {
      for (let ri = 0; ri < 3; ri++) {
        makeKey(D.outerLeft[ri], 0, ri * (m.keyH + m.gap), m.F, m.keyH);
        makeKey(D.outerRight[ri], m.kbW - m.F, ri * (m.keyH + m.gap), m.F, m.keyH);
      }
      const middleX = m.F + m.gap;
      const middleW = m.kbW - 2 * (m.F + m.gap);
      const n = 20;
      const k = Math.floor((middleW - (n - 1) * m.gap) / n);
      const leftover = middleW - (n * k + (n - 1) * m.gap);
      const pad = Math.floor(leftover / 2);
      for (let r = 0; r < 3; r++) {
        for (let c = 0; c < n; c++) {
          const idx = state.symbolPage * 60 + r * n + c;
          const sym = idx < D.symbols.length ? D.symbols[idx] : '';
          makeSymbolKey(sym, middleX + pad + c * (k + m.gap), r * (m.keyH + m.gap), k, m.keyH);
        }
      }
    }
  }

  // ---------- labels / state ----------

  function keyMainText(id) {
    const def = D.keys[id];
    if (!def) return '';
    if (id === 'lang') {
      if (state.layer === 'symbols') {
        if (state.orientation === 'landscape') {
          return '符 ' + (state.symbolPage + 1) + '/' + symbolPageCount();
        }
        return '符';
      }
      return state.lang === 'zh' ? '中' : '英';
    }
    if (id === 'space') return state.escape ? state.escapeBuffer : '空格';
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
      if (id === 'space' && state.escape) {
        const len = state.escapeBuffer.length;
        const base = state.orientation === 'portrait' ? 32 : 28;
        const size = len <= 5 ? base : (len <= 8 ? Math.round(base * 0.72) : Math.round(base * 0.55));
        main.style.fontSize = size + 'px';
      } else if (id === 'lang' && state.layer === 'symbols' && state.orientation === 'landscape') {
        main.style.fontSize = '22px';
      } else {
        main.style.fontSize = '';
      }
    }
    if (up) up.textContent = keySlideText(def, 'up');
    if (down) down.textContent = keySlideText(def, 'down');
  }

  function updateAllKeyLabels() {
    keyEls.forEach(function (el, id) { updateKeyLabel(id); });
    // symbol page label on landscape lang key
    if (state.orientation === 'landscape' && state.layer === 'symbols' && keyEls.get('lang')) {
      updateKeyLabel('lang');
    }
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

  function makeCandButton(text, width, cls) {
    const b = document.createElement(text ? 'button' : 'span');
    b.className = cls || 'cand';
    b.style.width = width + 'px';
    b.textContent = text || '';
    if (text) {
      b.addEventListener('click', function (ev) {
        ev.stopPropagation();
        if (suppressCandClick) { suppressCandClick = false; return; }
        commitCandidate(text);
      });
    } else {
      b.classList.add('empty');
    }
    return b;
  }

  function makeMoreButton(width, hClass) {
    const b = document.createElement('button');
    b.className = 'more ' + (hClass || '');
    b.style.width = width + 'px';
    b.textContent = '>';
    b.addEventListener('click', function (ev) {
      ev.stopPropagation();
      if (suppressCandClick) { suppressCandClick = false; return; }
      nextCandidatePage();
    });
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
    if (candidateBarEl) renderPortraitCandidate();
    if (candidatePanelEl) renderLandscapeCandidate();
  }

  function renderPortraitCandidate() {
    const pre = candidateBarEl.querySelector('.preedit');
    const row = candidateBarEl.querySelector('.cand-row');
    pre.textContent = '';
    row.innerHTML = '';
    if (state.escape) {
      pre.textContent = state.escapeBuffer;
      const info = escapePreview();
      const line = document.createElement('div');
      line.className = 'escape-line';
      line.textContent = info.text;
      row.appendChild(line);
      return;
    }
    if (state.layer === 'symbols') {
      pre.textContent = '符号 ' + (state.symbolPage + 1) + '/' + symbolPageCount();
      return;
    }
    if (state.composing) {
      pre.textContent = state.preedit;
      const widths = splitInts(716 - 24 - 5 * 8, 5, 0);
      const visible = visibleCandidates();
      for (let i = 0; i < 5; i++) {
        row.appendChild(makeCandButton(visible[i] || '', widths[i], 'cand'));
      }
      const hasMore = candPageCount() > 1;
      if (hasMore) row.appendChild(makeMoreButton(24, 'more'));
      else {
        const spacer = document.createElement('span');
        spacer.className = 'more empty';
        spacer.style.width = '24px';
        row.appendChild(spacer);
      }
    }
  }

  function renderLandscapeCandidate() {
    const status = candidatePanelEl.querySelector('.panel-status');
    const top = candidatePanelEl.querySelector('.panel-top');
    const bottom = candidatePanelEl.querySelector('.panel-bottom');
    status.textContent = '';
    top.innerHTML = '';
    bottom.innerHTML = '';
    if (state.escape) {
      status.textContent = state.escapeBuffer;
      const info = escapePreview();
      const p = document.createElement('div');
      p.className = 'escape-line';
      p.textContent = info.text;
      top.appendChild(p);
      const h = document.createElement('div');
      h.className = 'escape-line small';
      h.textContent = info.help ? '' : 'n 换行  t 制表  u Unicode  s 设置';
      bottom.appendChild(h);
      return;
    }
    if (state.composing) {
      status.textContent = state.preedit;
      const P = metrics.panelW;
      const topW = splitInts(P - 4, 2, 0);
      const bottomAvailable = P - 12 - 24;
      const bottomW = splitInts(bottomAvailable, 3, 0);
      const visible = visibleCandidates();
      for (let i = 0; i < 2; i++) {
        top.appendChild(makeCandButton(visible[i] || '', topW[i], 'cand panel-cand'));
      }
      for (let i = 2; i < 5; i++) {
        bottom.appendChild(makeCandButton(visible[i] || '', bottomW[i - 2], 'cand panel-cand'));
      }
      if (candPageCount() > 1) bottom.appendChild(makeMoreButton(24, 'more panel-more'));
      else {
        const spacer = document.createElement('span');
        spacer.className = 'more empty';
        spacer.style.width = '24px';
        bottom.appendChild(spacer);
      }
    }
  }

  function nextCandidatePage() {
    if (state.candidates.length <= PAGE_SIZE) return;
    state.candPage = (state.candPage + 1) % candPageCount();
    renderCandidateContent();
    logEvent('候选翻页 → ' + (state.candPage + 1) + '/' + candPageCount());
  }

  // ---------- editor ----------

  function updateAppMode() {
    let mode = state.lang === 'zh' ? '中' : '英';
    if (state.layer === 'symbols') mode += ' · 符 ' + (state.symbolPage + 1) + '/' + symbolPageCount();
    if (state.escape) mode += ' · 转义';
    els.appMode.textContent = mode;
  }

  function appendTextNode(parent, text) {
    if (text) parent.appendChild(document.createTextNode(text));
  }

  function renderEditor() {
    const editor = els.editor;
    editor.innerHTML = '';
    const text = state.text;
    const cursor = state.cursor;
    if (!text && !state.composing) {
      const ph = document.createElement('span');
      ph.className = 'placeholder';
      ph.textContent = '点击键盘输入…';
      editor.appendChild(ph);
    }
    const before = text.slice(0, cursor);
    const after = text.slice(cursor);
    appendTextNode(editor, before);
    if (state.composing) {
      const comp = document.createElement('span');
      comp.className = 'composition';
      comp.textContent = state.preedit;
      editor.appendChild(comp);
    }
    const caret = document.createElement('span');
    caret.className = 'caret';
    editor.appendChild(caret);
    appendTextNode(editor, after);
    updateAppMode();
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
    if (!state.composing) {
      state.preedit = '';
      state.candidates = [];
      state.candPage = 0;
    } else {
      const r = lookupPinyin(state.composing);
      state.preedit = r.pre;
      state.candidates = r.c;
      state.candPage = 0;
    }
    renderCandidateContent();
    renderEditor();
  }

  function clearComposition() {
    state.composing = '';
    state.preedit = '';
    state.candidates = [];
    state.candPage = 0;
  }

  function commitComposition(str) {
    insertText(str);
    clearComposition();
    renderCandidateContent();
    updateAllKeyLabels();
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
    if (state.escapeBuffer.length >= MAX_ESCAPE) {
      showToast('转义缓冲已满');
      return;
    }
    state.escapeBuffer += ch;
    updateAllKeyLabels();
    renderCandidateContent();
  }

  function executeEscape() {
    const info = escapePreview();
    if (!info.valid) {
      showToast(info.text || '未知转义', 'warn');
      return;
    }
    const cmd = info.cmd;
    if (cmd) {
      if (cmd.type === 'text') {
        insertText(cmd.value);
        logEvent('转义：' + info.text);
      } else if (cmd.value === 'settings') {
        showToast('设置入口（原型控制栏）');
        logEvent('转义：设置');
      } else if (cmd.value === 'variant') {
        showToast('简繁切换（预留）');
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
      if (state.escape) appendEscape(label); else insertText(label);
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
          updateAllKeyLabels();
          renderCandidateContent();
        }
      } else if (state.composing) deletePinyinOrChar();
      else backspaceText();
      clearTempMods();
      return;
    }
    if (def.kind === 'enter') {
      if (state.escape) { showToast('转义模式中确认键无效'); return; }
      if (state.composing) {
        const raw = state.composing;
        logEvent('确认上屏原始字符：' + raw);
        commitComposition(raw);
        clearTempMods();
      } else {
        insertText('\n');
        logEvent('换行');
        clearTempMods();
      }
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
    clearTempMods();
    buildKeyboard();
    renderCandidateContent();
    updateAppMode();
    logEvent('进入符号模式');
  }

  function exitSymbolMode() {
    state.layer = 'letters';
    clearTempMods();
    buildKeyboard();
    renderCandidateContent();
    updateAppMode();
    logEvent('退出符号模式');
  }

  function longLang() {
    if (state.layer === 'symbols') {
      exitSymbolMode();
    } else {
      enterSymbolMode();
    }
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
    renderCandidateContent();
    logEvent('收起键盘');
  }

  function showKeyboard() {
    state.keyboardVisible = true;
    rebuildAll();
    renderCandidateContent();
    logEvent('显示键盘');
  }

  function flipSymbolPage(direction) {
    const count = symbolPageCount();
    if (count <= 1) return;
    state.symbolPage = (state.symbolPage + direction + count) % count;
    buildKeyboard();
    renderCandidateContent();
    updateAppMode();
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
          updateAllKeyLabels();
          renderCandidateContent();
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
    s.keyEl.classList.remove('slide-up', 'slide-down', 'null-slide');
    s.keyEl.classList.add(dir === 'up' ? 'slide-up' : 'slide-down');
    const label = keySlideText(def, dir);
    if (!label) s.keyEl.classList.add('null-slide');
  }

  function clearSlide(s) {
    s.mode = 'pending';
    s.dir = null;
    s.keyEl.classList.remove('slide-up', 'slide-down', 'null-slide');
  }

  function cancelSession(s) {
    if (!s) return;
    if (s.timer) clearTimeout(s.timer);
    if (s.repeatTimer) clearInterval(s.repeatTimer);
    s.keyEl.classList.remove('pressed', 'slide-up', 'slide-down', 'null-slide');
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
    s.keyEl.classList.remove('pressed', 'slide-up', 'slide-down', 'null-slide');
    sessions.delete(e.pointerId);
    try { els.keyboard.releasePointerCapture(e.pointerId); } catch (err) {}

    lastKeyTap = {
      id: s.symbol ? ('sym:' + s.symbol) : s.keyId,
      t: Date.now()
    };

    if (s.symbol) {
      if (s.mode === 'swipe') {
        flipSymbolPage(s.dir === 'up' ? 1 : -1);
      } else if (s.symbol) {
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

  // ---------- candidate swipe ----------

  let candSwipe = null;
  function bindCandidateSwipe() {
    els.keyboard.addEventListener('pointerdown', function (e) {
      const row = e.target.closest('.cand-row, .panel-top, .panel-bottom');
      if (!row) return;
      candSwipe = { x: e.clientX, y: e.clientY, pointerId: e.pointerId };
    });
    window.addEventListener('pointerup', function (e) {
      if (!candSwipe || candSwipe.pointerId !== e.pointerId) { candSwipe = null; return; }
      const dx = e.clientX - candSwipe.x;
      const dy = e.clientY - candSwipe.y;
      candSwipe = null;
      if (Math.abs(dx) >= SWIPE_PX && Math.abs(dx) > Math.abs(dy)) {
        suppressCandClick = true;
        setTimeout(function () { suppressCandClick = false; }, 400);
        if (dx < 0) nextCandidatePage();
        else {
          if (state.candidates.length > PAGE_SIZE) {
            state.candPage = (state.candPage - 1 + candPageCount()) % candPageCount();
            renderCandidateContent();
          }
        }
      }
    });
  }

  // ---------- controls ----------

  function updateControlLabels() {
    els.orientationBtn.textContent = state.orientation === 'portrait' ? '横屏预览' : '竖屏预览';
    els.showKbdBtn.textContent = state.keyboardVisible ? '收起键盘' : '显示键盘';
    els.safeLabel.textContent = '安全边距 ' + state.safeInset;
    els.safeInset.value = String(state.safeInset);
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
    state.candPage = 0;
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
    state.candPage = 0;
    state.preedit = '';
    state.text = '';
    state.cursor = 0;
    state.escape = false;
    rebuildAll();
    renderCandidateContent();
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
    on(els.safeInset, 'input', function () {
      state.safeInset = Number(els.safeInset.value);
      if (els.safeLabel) els.safeLabel.textContent = '安全边距 ' + state.safeInset;
      rebuildAll();
      logEvent('安全边距 ' + state.safeInset);
    });
    on(els.showKbdBtn, 'click', function () {
      if (state.keyboardVisible) hideKeyboard(); else showKeyboard();
    });
    if (els.hiddenBar) {
      const hbBtn = els.hiddenBar.querySelector('button');
      if (hbBtn) on(hbBtn, 'click', showKeyboard);
    }
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
    bindCandidateSwipe();
    window.addEventListener('resize', fitStage);
    if (media.addEventListener) {
      media.addEventListener('change', function () {
        if (state.theme === 'auto') applyTheme();
      });
    }
    on(els.editor, 'click', function () {
      showToast('使用下方键盘输入');
    });
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
