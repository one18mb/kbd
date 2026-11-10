(function () {
  'use strict';

  // 视图层：只负责把 model(+metrics) 画成 DOM，不持有业务状态。
  // 交互意图通过 handlers 回传给 app。

  var D = window.KBD;
  var L = D.layout;

  var els = {};
  var metrics = null;
  var handlers = {
    onKeyClick: function () {},
    onSymbolClick: function () {},
    onCandidate: function () {}
  };

  var keyEls = new Map();
  var candidateBarEl = null;
  var candidatePanelEl = null;
  var expandedPanelEl = null;
  var floatingPreeditEl = null;
  var stageScale = 1;
  var deviceRect = null;
  var toastTimer = null;
  var media = window.matchMedia('(prefers-color-scheme: dark)');

  function $(id) { return document.getElementById(id); }

  function init() {
    [
      'device', 'stageWrap', 'appScreen', 'editor', 'toast',
      'keyboard', 'controls', 'themeBtn',
      'demoBtn', 'symbolBtn', 'escapeBtn', 'showKbdBtn',
      'logBtn', 'resetBtn', 'fullscreenBtn', 'eventLog',
      'presetSelect', 'wInput', 'hInput', 'rInput', 'bInput', 'schemeSelect',
      'modeSelect', 'layoutReadout'
    ].forEach(function (id) { els[id] = $(id); });
  }

  function setHandlers(h) { handlers = h; }

  function setMetrics(m) {
    metrics = m;
    if (!els.device) return;
    els.device.style.setProperty('--s', m.S + 'px');
    els.device.style.setProperty('--g', m.g + 'px');
    els.device.style.setProperty('--pad', m.preeditPad + 'px');
  }

  // ---------- 舞台缩放 ----------

  function fitStage() {
    if (!metrics) return;
    var fs = !!document.fullscreenElement;
    if (els.controls) els.controls.classList.toggle('hidden', fs);
    if (els.device) els.device.classList.toggle('actual', fs);
    var wrap = els.stageWrap;
    var availW = wrap.clientWidth;
    var availH = wrap.clientHeight;
    // 自适应预览：小屏幕放大到看得清，大屏幕缩到装得下；上限 4 倍。
    var fit = Math.min(availW / metrics.W, availH / metrics.H);
    stageScale = Math.max(0.1, Math.min(fit, 4));
    els.device.style.width = metrics.W + 'px';
    els.device.style.height = metrics.H + 'px';
    els.device.style.transform = 'translate(-50%, -50%) scale(' + stageScale + ')';
    deviceRect = els.device.getBoundingClientRect();
  }

  function toLogicalY(clientY) {
    if (!deviceRect) return 0;
    return (clientY - deviceRect.top) / stageScale;
  }

  function toLogicalX(clientX) {
    if (!deviceRect) return 0;
    return (clientX - deviceRect.left) / stageScale;
  }

  // ---------- 外框 ----------

  function applyFrame(model) {
    var m = metrics;
    els.device.style.width = m.W + 'px';
    els.device.style.height = m.H + 'px';
    els.device.setAttribute('data-orientation', m.orientation);
    els.appScreen.style.bottom = (model.keyboardVisible ? m.kbH : 0) + 'px';
    els.keyboard.style.left = m.kbLeft + 'px';
    els.keyboard.style.width = m.kbW + 'px';
    els.keyboard.style.height = m.kbH + 'px';
    els.keyboard.style.display = model.keyboardVisible ? 'block' : 'none';
    els.controls.classList.toggle('kbd-hidden', !model.keyboardVisible);
  }

  // ---------- 键盘骨架 ----------

  function build(model) {
    els.keyboard.innerHTML = '';
    keyEls.clear();
    candidateBarEl = null;
    candidatePanelEl = null;
    expandedPanelEl = null;
    floatingPreeditEl = null;
    if (!model.keyboardVisible) return;
    if (model.orientation === 'portrait') buildPortrait(model);
    else buildLandscape(model);
    renderCandidates(model);
    updateLabels(model);
    updateStates(model);
  }

  function makeKey(id, x, y, w, h) {
    var def = D.keys[id];
    if (!def) return null;
    var el = document.createElement('div');
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
      handlers.onKeyClick(id);
    });
    els.keyboard.appendChild(el);
    keyEls.set(id, el);
    return el;
  }

  function ariaName(id, def) {
    var names = {
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
    var el = document.createElement('div');
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
      if (sym) handlers.onSymbolClick(sym);
    });
    els.keyboard.appendChild(el);
    return el;
  }

  function buildPortrait(model) {
    buildCandidateBar();
    if (model.layer === 'symbols') buildPortraitSymbols(model);
    else if (model.expanded) buildPortraitExpanded();
    else buildPortraitLetters();
    buildPortraitFunctionRow();
  }

  function buildCandidateBar() {
    var bar = document.createElement('div');
    bar.className = 'candidate-bar';
    bar.style.height = metrics.barH + 'px';
    els.keyboard.appendChild(bar);
    candidateBarEl = bar;
  }

  // 第 4、5 列之间是中缝 c；c 可能比普通缝 g 大 1px。
  function portraitX(m, col) {
    return m.side + col * (m.K + m.g) + (col >= 5 ? (m.c - m.g) : 0);
  }

  function buildPortraitLetters() {
    var m = metrics;
    D.portraitRows.slice(0, 3).forEach(function (row, ri) {
      var y = m.mainTop + ri * (m.keyH + m.g);
      row.forEach(function (id, ci) {
        makeKey(id, portraitX(m, ci), y, m.K, m.keyH);
      });
    });
  }

  function buildPortraitSymbols(model) {
    var m = metrics;
    for (var r = 0; r < 3; r++) {
      var y = m.symbolRows[r];
      for (var c = 0; c < m.symbolCols; c++) {
        var idx = model.symbolPage * m.symbolSlots + r * m.symbolCols + c;
        var sym = idx < D.symbols.length ? D.symbols[idx] : '';
        makeSymbolKey(sym, portraitX(m, c), y, m.symbolK, m.keyH);
      }
    }
  }

  function buildPortraitExpanded() {
    var m = metrics;
    var panel = document.createElement('div');
    panel.className = 'expanded-panel';
    panel.style.left = '0px';
    panel.style.top = m.expTop + 'px';
    panel.style.width = m.W + 'px';
    panel.style.height = m.expH + 'px';
    els.keyboard.appendChild(panel);
    expandedPanelEl = panel;
  }

  function buildPortraitFunctionRow() {
    var m = metrics;
    var x = m.side;
    D.portraitRows[3].forEach(function (id) {
      var def = D.keys[id];
      // 空格跨中缝，宽度是 2K+c；删除/回车是 2K+g。
      var w = id === 'space' ? m.spaceW : (def.double ? m.double : m.K);
      makeKey(id, x, m.funcTop, w, m.keyH);
      x += w + m.g;
    });
  }

  function buildLandscape(model) {
    if (model.layer === 'symbols') buildLandscapeSymbols(model);
    else if (model.expanded) buildLandscapeExpanded();
    else buildLandscapeLetters();
  }

  function makeLandscapeRow(ri) {
    var m = metrics;
    var y = m.rowY[ri];
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
    var m = metrics;
    for (var ri = 0; ri < 3; ri++) makeLandscapeRow(ri);
    makeKey('space', m.spaceX, m.spaceY, m.spaceW, m.spaceH);

    var panel = document.createElement('div');
    panel.className = 'candidate-panel';
    panel.style.left = m.panelX + 'px';
    panel.style.top = '0px';
    panel.style.width = m.panelW + 'px';
    panel.style.height = m.panelH + 'px';
    els.keyboard.appendChild(panel);
    candidatePanelEl = panel;
  }

  function buildLandscapeSymbols(model) {
    var m = metrics;
    for (var ri = 0; ri < 3; ri++) {
      var y = m.rowY[ri];
      makeKey(D.outerLeft[ri], 0, y, m.outerW, m.keyH);
      makeKey(D.outerRight[ri], m.outerRightX, y, m.outerW, m.keyH);
      for (var c = 0; c < m.symbolCols; c++) {
        var idx = model.symbolPage * m.symbolSlots + ri * m.symbolCols + c;
        var sym = idx < D.symbols.length ? D.symbols[idx] : '';
        makeSymbolKey(sym, m.symbolX + c * (m.symbolCellW + m.g), y, m.symbolCellW, m.keyH);
      }
    }
  }

  function buildLandscapeExpanded() {
    var m = metrics;
    for (var ri = 0; ri < 3; ri++) makeLandscapeRow(ri);
    var panel = document.createElement('div');
    panel.className = 'expanded-panel';
    panel.style.left = m.expX + 'px';
    panel.style.top = m.expY + 'px';
    panel.style.width = m.expW + 'px';
    panel.style.height = m.expH + 'px';
    els.keyboard.appendChild(panel);
    expandedPanelEl = panel;
  }

  // ---------- 标签 ----------

  var MOD_SHORT = { ctrl: 'Ct', alt: 'Al', shift: 'Sh' };

  // Unifont 确定性宽度：全角 S，半角 S/2。
  function labelWidth(text, s) {
    var w = 0;
    for (var i = 0; i < text.length; i++) {
      var c = text.charCodeAt(i);
      var full = c >= 0x1100 && (c <= 0x115F || c === 0x2329 || c === 0x232A
        || (c >= 0x2E80 && c <= 0xA4CF && c !== 0x303F)
        || (c >= 0xAC00 && c <= 0xD7A3)
        || (c >= 0xF900 && c <= 0xFAFF)
        || (c >= 0xFE30 && c <= 0xFE6F)
        || (c >= 0xFF00 && c <= 0xFF60)
        || (c >= 0xFFE0 && c <= 0xFFE6));
      w += full ? s : s / 2;
    }
    return w;
  }

  // 修饰键或 Tab/Esc 任一放不下，就一起进入紧凑标签模式。
  function compactLabels() {
    if (!metrics) return false;
    var keyW = metrics.orientation === 'landscape' ? metrics.outerW : metrics.K;
    var widest = Math.max(
      labelWidth('Ctrl', metrics.S),
      labelWidth('Alt', metrics.S),
      labelWidth('Shft', metrics.S)
    );
    var modShort = widest >= keyW - 2;
    var subShort = labelWidth('Tab', metrics.S) > metrics.K - metrics.g
      || labelWidth('Esc', metrics.S) > metrics.K - metrics.g;
    return modShort || subShort;
  }

  function modifierLabel(def, model) {
    var full = (def.main && (def.main[model.lang] || def.main.en)) || '';
    var short = MOD_SHORT[def.mod];
    if (!short || !metrics) return full;
    return compactLabels() ? short : full;
  }

  function keyMainText(model, id) {
    var def = D.keys[id];
    if (!def) return '';
    if (id === 'lang') {
      if (model.layer === 'symbols') return '符';
      return model.lang === 'zh' ? '中' : '英';
    }
    if (id === 'enter') {
      if (model.composing) return model.expanded ? '返回' : '展开';
      return '确认';
    }
    if (id === 'space') return model.escape ? '\\' : '空格';
    if (def.kind === 'modifier') return modifierLabel(def, model);
    if (def.kind === 'letter') return model.mods.shift.active ? id.toUpperCase() : id;
    return (def.main && (def.main[model.lang] || def.main.en)) || '';
  }

  function keySlideText(model, def, dir) {
    var slide = dir === 'up' ? def.up : def.down;
    if (!slide) return '';
    return slide[model.lang] || slide.en || '';
  }

  function slideLabel(model, id, dir) {
    var def = D.keys[id];
    if (!def) return '';
    return keySlideText(model, def, dir);
  }

  // 紧凑模式下 Tab/Esc 缩成 Ta/Es，和 Ct/Al/Sh 同时生效。
  function fitSubLabel(label) {
    if (!compactLabels()) return label;
    if (label === 'Tab') return 'Ta';
    if (label === 'Esc') return 'Es';
    return label;
  }

  function pageCount() {
    return Math.max(1, Math.ceil(D.symbols.length / metrics.symbolSlots));
  }

  function updateKeyLabel(model, id) {
    var def = D.keys[id];
    var el = keyEls.get(id);
    if (!def || !el) return;
    var main = el.querySelector('.main');
    var up = el.querySelector('.up');
    var down = el.querySelector('.down');

    if (main) main.textContent = keyMainText(model, id);

    if (id === 'lang' && model.layer === 'symbols') {
      el.classList.remove('single');
      if (up) up.textContent = String(model.symbolPage + 1);
      if (down) down.textContent = String(pageCount());
    } else {
      if (up) up.textContent = fitSubLabel(keySlideText(model, def, 'up'));
      if (down) down.textContent = fitSubLabel(keySlideText(model, def, 'down'));
      if (!def.up && !def.down) el.classList.add('single');
    }
  }

  function updateLabels(model) {
    keyEls.forEach(function (el, id) { updateKeyLabel(model, id); });
  }

  function updateKeyState(model, id) {
    var def = D.keys[id];
    var el = keyEls.get(id);
    if (!def || !el) return;
    if (def.kind === 'modifier') {
      var s = model.mods[def.mod];
      el.classList.toggle('active', s.active);
      el.classList.toggle('locked', s.locked);
    }
    if (id === 'lang') el.classList.toggle('active', model.layer === 'symbols');
    if (id === 'space') el.classList.toggle('active', model.escape);
  }

  function updateStates(model) {
    keyEls.forEach(function (el, id) { updateKeyState(model, id); });
  }

  // ---------- 候选与拼音 ----------

  function makeCandCell(parent, lines, x, y, w, h, index) {
    if (!parent || !lines || !lines.length) return null;
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'cand-cell';
    b.style.left = x + 'px';
    b.style.top = y + 'px';
    b.style.width = w + 'px';
    b.style.height = h + 'px';
    b.dataset.index = index == null ? '' : index;
    var span = document.createElement('span');
    span.className = 'cand-text';
    var S = metrics.S, g = metrics.g;
    lines.forEach(function (line, i) {
      var el = document.createElement('span');
      el.className = 'cand-line';
      el.style.height = S + 'px';
      el.style.lineHeight = S + 'px';
      if (i > 0) el.style.marginTop = g + 'px';
      el.textContent = line;
      span.appendChild(el);
    });
    b.appendChild(span);
    b.setAttribute('aria-label', '候选 ' + lines.join(''));
    b.addEventListener('click', function (ev) {
      ev.stopPropagation();
      handlers.onCandidate(lines.join(''), index);
    });
    parent.appendChild(b);
    return b;
  }

  function renderCandidates(model) {
    if (candidateBarEl) renderPortraitBar(model);
    if (candidatePanelEl) renderLandscapePanel(model);
    if (expandedPanelEl) renderExpandedPanel(model);
    renderFloatingPreedit(model);
  }

  function renderPortraitBar(model) {
    var bar = candidateBarEl;
    var m = metrics;
    bar.innerHTML = '';
    if (!model.composing) return;
    var rows = L.candidateRows(model.candidates, 10, m);
    if (!rows.length) return;
    var row = rows[Math.min(model.candidateRow || 0, rows.length - 1)];
    var x = m.candSide;
    row.items.forEach(function (item) {
      var lines = L.candLines(model.candidates[item.index], 10, m);
      var w = L.candCellBase(item.keys, m);
      makeCandCell(bar, [lines[0]], x, 0, w, m.candH, item.index);
      x += w + m.g;
    });
  }

  function renderLandscapePanel(model) {
    var panel = candidatePanelEl;
    var m = metrics;
    panel.innerHTML = '';
    if (!model.composing) return;
    var rows = L.candidateRows(model.candidates, m.p, m);
    var start = Math.min(model.candidateRow || 0, Math.max(0, rows.length - 1));
    var top = 0;
    for (var ri = start; ri < rows.length; ri++) {
      var row = rows[ri];
      var h = L.candRowHeight(row.slots, m);
      if (top + h > m.panelH + 0.5) break;
      var x = 0;
      row.items.forEach(function (item) {
        var lines = L.candLines(model.candidates[item.index], m.p, m);
        var w = L.candCellBase(item.keys, m);
        makeCandCell(panel, lines, x, top, w, h, item.index);
        x += w + m.g;
      });
      top += h + m.g;
    }
  }

  function renderExpandedPanel(model) {
    var panel = expandedPanelEl;
    var m = metrics;
    panel.innerHTML = '';

    var bg = document.createElement('div');
    bg.className = 'expanded-candidate-bg';
    bg.style.left = '0px';
    bg.style.top = '0px';
    bg.style.width = '100%';
    bg.style.height = m.expH + 'px';
    panel.appendChild(bg);

    var portrait = model.orientation === 'portrait';
    var rowKeys = portrait ? 10 : m.p;
    var rows = L.candidateRows(model.candidates, rowKeys, m);
    var start = portrait ? (model.candidateRow || 0) + 1 : (model.candidateRow || 0);
    var x0 = portrait ? m.expSide : 0;
    var top = 0;
    for (var ri = start; ri < rows.length; ri++) {
      var row = rows[ri];
      var h = L.candRowHeight(row.slots, m);
      if (top + h > m.expH + 0.5) break;
      var x = x0;
      row.items.forEach(function (item) {
        var lines = L.candLines(model.candidates[item.index], rowKeys, m);
        var w = L.candCellBase(item.keys, m);
        makeCandCell(panel, lines, x, top, w, h, item.index);
        x += w + m.g;
      });
      top += h + m.g;
    }
  }

  function renderFloatingPreedit(model) {
    if (!model.keyboardVisible) return;
    var need = model.composing || model.escape;
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
      var span = document.createElement('span');
      span.className = 'pinyin-text';
      floatingPreeditEl.appendChild(span);
      els.keyboard.appendChild(floatingPreeditEl);
    }
    if (model.orientation === 'landscape') {
      floatingPreeditEl.style.left = metrics.pinyinX + 'px';
      floatingPreeditEl.style.width = metrics.pinyinW + 'px';
    } else {
      floatingPreeditEl.style.left = '0px';
      floatingPreeditEl.style.width = '100%';
    }
    var text = model.composing ? model.preedit : (model.escape ? model.escapeBuffer : '');
    floatingPreeditEl.querySelector('.pinyin-text').textContent = text;
  }

  // ---------- 编辑器 ----------

  function appendTextNode(parent, text) {
    if (text) parent.appendChild(document.createTextNode(text));
  }

  function renderEditor(model) {
    var editor = els.editor;
    editor.innerHTML = '';
    appendTextNode(editor, model.text.slice(0, model.cursor));
    var caret = document.createElement('span');
    caret.className = 'caret';
    editor.appendChild(caret);
    appendTextNode(editor, model.text.slice(model.cursor));
    requestAnimationFrame(function () {
      var er = editor.getBoundingClientRect();
      var cr = caret.getBoundingClientRect();
      if (cr.bottom > er.bottom - 10) editor.scrollTop += cr.bottom - er.bottom + 40;
      else if (cr.top < er.top + 10) editor.scrollTop -= er.top - cr.top + 40;
    });
  }

  // ---------- 日志 / 提示 / 主题 / 控制条 ----------

  function renderLog(entries, open) {
    if (!els.eventLog) return;
    if (!open) {
      els.eventLog.classList.add('hidden');
      return;
    }
    els.eventLog.classList.remove('hidden');
    els.eventLog.innerHTML = '';
    entries.slice().reverse().forEach(function (e) {
      var row = document.createElement('div');
      row.className = 'log-row';
      row.textContent = e.t.toLocaleTimeString('zh-CN', { hour12: false }) + '  ' + e.text;
      els.eventLog.appendChild(row);
    });
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

  function resolvedTheme(model) {
    if (model.theme === 'auto') return media.matches ? 'dark' : 'light';
    return model.theme;
  }

  function applyTheme(model) {
    var t = resolvedTheme(model);
    document.documentElement.setAttribute('data-theme', t);
    var label = model.theme === 'auto' ? '跟随系统' : (model.theme === 'dark' ? '深色' : '浅色');
    els.themeBtn.textContent = '主题：' + label;
  }

  function updateControlText(model) {
    els.showKbdBtn.textContent = model.keyboardVisible ? '收起键盘' : '显示键盘';
    els.logBtn.textContent = model.logOpen ? '隐藏日志' : '事件日志';
    if (els.schemeSelect && D.schemes && D.schemes[model.scheme]) {
      els.schemeSelect.value = model.scheme;
    }
    if (els.bInput) els.bInput.value = model.b;
    if (els.layoutReadout && metrics) {
      var m = metrics;
      var flags = [];
      if (m.gCappedByHeight) flags.push('g 受高度压制');
      if (m.tBeyond) flags.push('k 超本级上限');
      if (m.overflow) flags.push('超出屏幕');
      if (!m.kbRatioOK) flags.push('键盘比越界');
      els.layoutReadout.textContent =
        (m.orientation === 'portrait' ? '竖屏' : '横屏') +
        ' n=' + m.n + ' f=' + m.S + ' k=' + m.K + ' g=' + m.g + ' c=' + m.c + ' m=' + m.m +
        ' | 键盘 ' + m.kbW + '×' + m.kbH + ' 比' + m.kbRatio.toFixed(2) +
        ' | 应用 ' + (m.H - m.kbH) +
        ' | W/H=' + m.ratio.toFixed(2) + ' R=' + m.R.toFixed(2) +
        ' | ' + m.modeReason +
        ' | 方案 ' + (D.schemes[model.scheme] || model.scheme) +
        ' ' + (model.variant === 't' ? '繁' : '简') + ' b=' + model.b +
        (flags.length ? ' | ' + flags.join('，') : '');
    }
  }

  window.KBD.view = {
    init: init,
    setHandlers: setHandlers,
    setMetrics: setMetrics,
    els: els,
    applyFrame: applyFrame,
    build: build,
    updateLabels: updateLabels,
    updateStates: updateStates,
    renderCandidates: renderCandidates,
    renderEditor: renderEditor,
    renderLog: renderLog,
    showToast: showToast,
    applyTheme: applyTheme,
    updateControlText: updateControlText,
    fitStage: fitStage,
    toLogicalX: toLogicalX,
    toLogicalY: toLogicalY,
    slideLabel: slideLabel
  };
})();
