(function () {
  'use strict';

  // 应用层：持有全部状态与业务动作，串起 layout / view / input。

  var D = window.KBD;
  var L = D.layout;
  var V = D.view;

  var MAX_ESCAPE = 16;

  var state = {
    // 屏幕输入：模式由 layout 计算，不再读系统方向。
    W: 720,
    H: 1604,
    b: 32,
    ratio: 0.75,
    modeOverride: 'auto',
    orientation: 'portrait', // 由 layout 计算后写回，供 view/input 使用
    theme: 'auto',
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

  var metrics = null;
  var lastTap = { tag: null, t: 0 };
  var logEntries = [];

  // ---------- 通用 ----------

  function logEvent(text) {
    logEntries.push({ t: new Date(), text: text });
    if (logEntries.length > 80) logEntries.shift();
    V.renderLog(logEntries, state.logOpen);
  }

  function markTap(tag) {
    lastTap = { tag: tag, t: Date.now() };
  }

  function isDuplicateTap(tag) {
    return lastTap.tag === tag && Date.now() - lastTap.t < 500;
  }

  function symbolPageCount() {
    var slots = metrics ? metrics.symbolSlots : 30;
    return Math.max(1, Math.ceil(D.symbols.length / slots));
  }

  // 结构变化：屏幕/模式/层/展开/键盘显隐 → 重建键盘。
  function rebuildAll() {
    metrics = L.compute({
      W: state.W,
      H: state.H,
      b: state.b,
      R: state.ratio,
      mode: state.modeOverride
    });
    state.orientation = metrics.orientation;
    var maxPage = Math.max(0, Math.ceil(D.symbols.length / metrics.symbolSlots) - 1);
    if (state.symbolPage > maxPage) state.symbolPage = maxPage;
    if (state.symbolPage < 0) state.symbolPage = 0;
    V.setMetrics(metrics);
    V.applyFrame(state);
    V.build(state);
    V.renderEditor(state);
    V.updateControlText(state);
    V.fitStage();
  }

  // 内容变化：标签/激活态/候选/拼音。
  function refreshLabels() { V.updateLabels(state); }
  function refreshStates() { V.updateStates(state); }
  function refreshCandidates() { V.renderCandidates(state); }

  // ---------- 编辑器 ----------

  function insertText(str) {
    state.text = state.text.slice(0, state.cursor) + str + state.text.slice(state.cursor);
    state.cursor += str.length;
    V.renderEditor(state);
  }

  function prevCharIndex(text, idx) {
    if (idx <= 0) return 0;
    var c = text.charCodeAt(idx - 1);
    if (c >= 0xDC00 && c <= 0xDFFF && idx >= 2) return idx - 2;
    return idx - 1;
  }

  function nextCharIndex(text, idx) {
    if (idx >= text.length) return text.length;
    var c = text.charCodeAt(idx);
    if (c >= 0xD800 && c <= 0xDBFF && idx + 1 < text.length) return idx + 2;
    return idx + 1;
  }

  function backspaceText() {
    if (state.cursor <= 0) return;
    var p = prevCharIndex(state.text, state.cursor);
    state.text = state.text.slice(0, p) + state.text.slice(state.cursor);
    state.cursor = p;
    V.renderEditor(state);
  }

  // ---------- 拼音 ----------

  function lookupPinyin(raw) {
    var exact = D.pinyin.find(function (e) { return e.p === raw; });
    if (exact) return { pre: exact.pre, c: exact.c.slice() };
    var matches = D.pinyin.filter(function (e) { return e.p.indexOf(raw) === 0; });
    if (matches.length) {
      matches.sort(function (a, b) { return b.p.length - a.p.length; });
      return { pre: raw, c: matches[0].c.slice() };
    }
    return { pre: raw, c: [raw] };
  }

  function updateComposition() {
    var wasExpanded = state.expanded;
    if (!state.composing) {
      state.preedit = '';
      state.candidates = [];
      state.expanded = false;
    } else {
      var r = lookupPinyin(state.composing);
      state.preedit = r.pre;
      state.candidates = r.c;
    }
    if (wasExpanded && !state.composing) {
      rebuildAll();
      return;
    }
    refreshCandidates();
    refreshLabels();
  }

  function commitComposition(str) {
    var wasExpanded = state.expanded;
    insertText(str);
    state.composing = '';
    state.preedit = '';
    state.candidates = [];
    state.expanded = false;
    if (wasExpanded) {
      rebuildAll();
      return;
    }
    refreshCandidates();
    refreshLabels();
  }

  function commitCandidate(text) {
    if (!state.composing) return;
    logEvent('选词：' + text);
    commitComposition(text);
  }

  function commitFirstCandidate() {
    if (!state.composing) return false;
    commitComposition(state.candidates[0] || state.composing);
    return true;
  }

  function appendPinyin(letter) {
    if (state.composing.length >= 40) {
      V.showToast('拼音缓冲已达单行上限');
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

  // ---------- 修饰键 ----------

  function clearTempMods() {
    var changed = false;
    Object.keys(state.mods).forEach(function (k) {
      var s = state.mods[k];
      if (s.active && !s.locked) {
        s.active = false;
        changed = true;
      }
    });
    if (changed) {
      refreshLabels();
      refreshStates();
    }
  }

  function toggleModifier(name) {
    var s = state.mods[name];
    if (s.locked) {
      s.active = false;
      s.locked = false;
    } else {
      s.active = !s.active;
      s.locked = false;
    }
    refreshLabels();
    refreshStates();
  }

  function lockModifier(name) {
    var s = state.mods[name];
    s.active = true;
    s.locked = true;
    refreshLabels();
    refreshStates();
  }

  // ---------- 转义 ----------

  function enterEscape() {
    if (state.composing) {
      V.showToast('先上屏拼音');
      return;
    }
    state.escape = true;
    state.escapeBuffer = '\\';
    clearTempMods();
    refreshLabels();
    refreshStates();
    refreshCandidates();
  }

  function exitEscape() {
    state.escape = false;
    state.escapeBuffer = '\\';
    refreshLabels();
    refreshStates();
    refreshCandidates();
  }

  function appendEscape(ch) {
    if (state.escapeBuffer.length >= MAX_ESCAPE) return;
    state.escapeBuffer += ch;
    refreshCandidates();
  }

  function escapePreview() {
    var body = state.escapeBuffer.slice(1);
    if (body === '') {
      return { help: true, text: 'n 换行   t 制表   u Unicode   s 设置   j 简繁' };
    }
    var cmd = D.escapeCommands[body];
    if (cmd) return { valid: true, text: '→ ' + cmd.label, cmd: cmd };

    var code = null;
    var m4 = /^u([0-9a-fA-F]{4})$/.exec(body);
    var mb = /^u\{([0-9a-fA-F]{1,6})\}$/.exec(body);
    if (m4) code = parseInt(m4[1], 16);
    else if (mb) code = parseInt(mb[1], 16);
    if (code != null) {
      if (code <= 0x10FFFF && !(code >= 0xD800 && code <= 0xDFFF)) {
        var ch = String.fromCodePoint(code);
        return { valid: true, text: '→ ' + ch, value: ch };
      }
      return { valid: false, text: '无效 Unicode' };
    }
    if (/^u/i.test(body)) return { valid: false, text: 'Unicode 需要 4 位或 {1–6} 位十六进制' };
    if (body === '\\') return { valid: true, text: '→ \\', value: '\\' };
    return { valid: false, text: '未知转义' };
  }

  function executeEscape() {
    var info = escapePreview();
    // 空格短按：有效才执行；无效视为取消，直接退回普通空格。
    if (!info.valid) {
      var pending = state.escapeBuffer;
      exitEscape();
      logEvent('取消转义：' + pending);
      return;
    }
    var cmd = info.cmd;
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

  // ---------- 字符动作 ----------

  function emitChar(label) {
    if (state.escape) {
      if (label.length === 1) {
        appendEscape(label);
        return;
      }
      if (label === 'Tab' || label === 'Esc') {
        V.showToast('转义模式不接受 ' + label);
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
      V.showToast('先上屏拼音');
      clearTempMods();
      return;
    }
    if (label === 'Tab') {
      insertText('\t');
      logEvent('Tab');
    } else if (label === 'Esc') {
      logEvent('Esc');
      V.showToast('Esc 事件（原型）');
    } else {
      insertText(label);
      logEvent('字符：' + label);
    }
    clearTempMods();
  }

  function letterAction(id) {
    if (state.escape) {
      appendEscape(id);
      if (state.mods.shift.active) clearTempMods();
      return;
    }
    var shift = state.mods.shift.active;
    var ctrl = state.mods.ctrl.active;
    var alt = state.mods.alt.active;
    if (ctrl || alt) {
      var chord = (ctrl ? 'Ctrl+' : '') + (alt ? 'Alt+' : '') + (shift ? id.toUpperCase() : id);
      logEvent(chord);
      V.showToast(chord);
      clearTempMods();
      return;
    }
    if (state.lang === 'zh') {
      if (state.composing) {
        if (shift) {
          V.showToast('先上屏拼音');
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
    var ch = shift ? id.toUpperCase() : id;
    insertText(ch);
    logEvent('字母：' + ch);
    clearTempMods();
  }

  function navAction(dir) {
    if (state.composing || state.escape) {
      V.showToast('先上屏拼音');
      clearTempMods();
      return;
    }
    if (dir === 'left') state.cursor = prevCharIndex(state.text, state.cursor);
    else if (dir === 'right') state.cursor = nextCharIndex(state.text, state.cursor);
    else if (dir === 'up') {
      var ls = state.text.lastIndexOf('\n', state.cursor - 1);
      var lineStart = ls >= 0 ? ls + 1 : 0;
      var col = state.cursor - lineStart;
      if (ls >= 0) {
        var prevLineStart = state.text.lastIndexOf('\n', ls - 1) + 1;
        state.cursor = Math.min(prevLineStart + col, ls);
      } else state.cursor = 0;
    } else if (dir === 'down') {
      var ls2 = state.text.lastIndexOf('\n', state.cursor - 1);
      var lineStart2 = ls2 >= 0 ? ls2 + 1 : 0;
      var col2 = state.cursor - lineStart2;
      var nextNl = state.text.indexOf('\n', state.cursor);
      if (nextNl >= 0) {
        var nextStart = nextNl + 1;
        var nextEnd = state.text.indexOf('\n', nextStart);
        var end = nextEnd >= 0 ? nextEnd : state.text.length;
        state.cursor = Math.min(nextStart + col2, end);
      } else state.cursor = state.text.length;
    }
    V.renderEditor(state);
    clearTempMods();
  }

  function tapKey(def) {
    var id = def.id;
    if (def.kind === 'modifier') { toggleModifier(def.mod); return; }
    if (def.kind === 'lang') { shortLang(); return; }
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
          refreshCandidates();
        }
      } else if (state.composing) deletePinyinOrChar();
      else backspaceText();
      clearTempMods();
      return;
    }
    if (def.kind === 'enter') {
      if (state.escape) { V.showToast('转义模式中确认键无效'); return; }
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
      var ctrl = state.mods.ctrl.active;
      var alt = state.mods.alt.active;
      if (ctrl || alt) logEvent((ctrl ? 'Ctrl+' : '') + (alt ? 'Alt+' : '') + def.main.en);
      navAction(def.dir);
      return;
    }
    if (def.kind === 'letter') letterAction(id);
  }

  function shortLang() {
    if (state.layer === 'symbols') { exitSymbolMode(); return; }
    if (state.composing) { V.showToast('先上屏拼音'); return; }
    state.lang = state.lang === 'zh' ? 'en' : 'zh';
    refreshLabels();
    refreshStates();
    refreshCandidates();
    logEvent('切换语言：' + (state.lang === 'zh' ? '中文' : '英文'));
  }

  function enterSymbolMode() {
    if (state.composing) { V.showToast('先上屏拼音'); return; }
    if (state.escape && state.orientation === 'landscape') {
      exitEscape();
      V.showToast('横屏符号模式与转义互斥');
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
    if (state.escape) { exitEscape(); logEvent('退出转义'); return; }
    if (state.composing) { V.showToast('先上屏拼音'); return; }
    enterEscape();
    logEvent('进入转义');
  }

  function longEnter() {
    if (state.composing) { V.showToast('先上屏拼音'); return; }
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
    var count = symbolPageCount();
    if (count <= 1) return;
    state.symbolPage = (state.symbolPage + direction + count) % count;
    rebuildAll();
    logEvent('符号页 → ' + (state.symbolPage + 1) + '/' + count);
  }

  // ---------- 手势回调 ----------

  function repeatKey(def) {
    if (!def) return;
    if (def.kind === 'backspace') {
      if (state.escape) {
        if (state.escapeBuffer.length > 1) {
          state.escapeBuffer = state.escapeBuffer.slice(0, -1);
          refreshCandidates();
        }
      } else if (state.composing) deletePinyinOrChar();
      else backspaceText();
      return;
    }
    if (def.kind === 'nav') {
      if (state.composing || state.escape) return;
      if (def.dir === 'left') state.cursor = prevCharIndex(state.text, state.cursor);
      else if (def.dir === 'right') state.cursor = nextCharIndex(state.text, state.cursor);
      V.renderEditor(state);
    }
  }

  function longKey(def) {
    if (def.kind === 'modifier') lockModifier(def.mod);
    else if (def.kind === 'space') longSpace();
    else if (def.kind === 'lang') longLang();
    else if (def.kind === 'enter') longEnter();
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

  // ---------- 屏幕与模式 ----------

  function clampScreen() {
    state.W = Math.max(120, Math.min(4000, Math.round(state.W)));
    state.H = Math.max(120, Math.min(4000, Math.round(state.H)));
    if (!(state.ratio > 0.2 && state.ratio <= 1)) state.ratio = L.DEFAULTS.R;
  }

  function syncLayoutControls(presetName) {
    var els = V.els;
    if (els.wInput) els.wInput.value = state.W;
    if (els.hInput) els.hInput.value = state.H;
    if (els.rInput) els.rInput.value = state.ratio;
    if (els.modeSelect) els.modeSelect.value = state.modeOverride;
    if (els.presetSelect) {
      var label = presetName || '';
      var found = false;
      for (var i = 0; i < els.presetSelect.options.length; i++) {
        if (els.presetSelect.options[i].value === label) { found = true; break; }
      }
      els.presetSelect.value = found ? label : '自定义';
    }
  }

  function applyScreen(presetName) {
    clampScreen();
    if (state.orientation === 'landscape' && state.layer === 'symbols' && state.escape) {
      state.escape = false;
      state.escapeBuffer = '\\';
    }
    rebuildAll();
    syncLayoutControls(presetName);
    logEvent('屏幕 ' + state.W + '×' + state.H + ' → ' +
      (state.orientation === 'portrait' ? '竖屏' : '横屏') +
      ' n=' + metrics.n + ' k=' + metrics.K + ' g=' + metrics.g + ' m=' + metrics.m);
  }

  function findPresetLabel(W, H) {
    for (var i = 0; i < L.PRESETS.length; i++) {
      var p = L.PRESETS[i];
      if (!p.custom && p.W === W && p.H === H) return p.label;
    }
    return '自定义';
  }

  function populatePresets() {
    var select = V.els.presetSelect;
    if (!select) return;
    select.innerHTML = '';
    L.PRESETS.forEach(function (p) {
      var opt = document.createElement('option');
      opt.value = p.label;
      opt.textContent = p.label;
      select.appendChild(opt);
    });
  }

  function bindLayoutControls() {
    var els = V.els;
    if (els.presetSelect) {
      els.presetSelect.addEventListener('change', function () {
        var label = els.presetSelect.value;
        var p = L.PRESETS.filter(function (x) { return x.label === label; })[0];
        if (!p) return;
        if (p.custom) { syncLayoutControls('自定义'); return; }
        state.W = p.W;
        state.H = p.H;
        applyScreen(p.label);
      });
    }

    var numTimer = null;

    function commitNumbers() {
      var w = parseFloat(els.wInput && els.wInput.value);
      var h = parseFloat(els.hInput && els.hInput.value);
      var r = parseFloat(els.rInput && els.rInput.value);
      if (!isFinite(w) || !isFinite(h) || !isFinite(r)) return;
      state.W = w;
      state.H = h;
      state.ratio = r;
      applyScreen(findPresetLabel(state.W, state.H));
    }

    function scheduleNumbers() {
      clearTimeout(numTimer);
      numTimer = setTimeout(commitNumbers, 250);
    }

    [els.wInput, els.hInput, els.rInput].forEach(function (el) {
      if (!el) return;
      el.addEventListener('input', scheduleNumbers);
      el.addEventListener('change', function () {
        clearTimeout(numTimer);
        commitNumbers();
      });
    });

    if (els.modeSelect) {
      els.modeSelect.addEventListener('change', function () {
        state.modeOverride = els.modeSelect.value;
        applyScreen(findPresetLabel(state.W, state.H));
      });
    }
  }

  // 视口变化只影响画布缩放；键盘模式由 (W,H,h_a) 决定。
  function handleViewportChange() {
    V.fitStage();
  }

  // ---------- 主题与演示 ----------

  function applyTheme() { V.applyTheme(state); }

  function cycleTheme() {
    state.theme = state.theme === 'auto' ? 'dark' : (state.theme === 'dark' ? 'light' : 'auto');
    applyTheme();
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

  // ---------- 绑定与启动 ----------

  function on(el, ev, fn, opts) {
    if (el) el.addEventListener(ev, fn, opts);
  }

  function bindControls() {
    var els = V.els;

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
      V.renderLog(logEntries, state.logOpen);
      V.updateControlText(state);
      V.fitStage();
    });
    on(els.resetBtn, 'click', resetAll);
    on(els.fullscreenBtn, 'click', function () {
      if (document.fullscreenElement) document.exitFullscreen();
      else document.documentElement.requestFullscreen().catch(function () {});
    });

    window.KBD.input.bind({
      root: els.keyboard,
      toLogicalX: V.toLogicalX,
      toLogicalY: V.toLogicalY,
      getSlideLabel: function (id, dir) { return V.slideLabel(state, id, dir); },
      allowPageSwipe: function () { return state.layer === 'symbols'; },
      handlers: {
        tapKey: tapKey,
        longKey: longKey,
        repeatKey: repeatKey,
        slideKey: function (def, dir) { emitChar(V.slideLabel(state, def.id, dir)); },
        emptySlide: function (def) { logEvent('空滑动：' + def.id); clearTempMods(); },
        symbolTap: tapSymbol,
        pageSwipe: flipSymbolPage,
        markTap: markTap
      }
    }).attach();

    bindLayoutControls();

    window.addEventListener('resize', handleViewportChange);
    window.addEventListener('orientationchange', handleViewportChange);
    document.addEventListener('fullscreenchange', handleViewportChange);

    var media = window.matchMedia('(prefers-color-scheme: dark)');
    if (media.addEventListener) {
      media.addEventListener('change', function () {
        if (state.theme === 'auto') applyTheme();
      });
    }
  }

  function init() {
    V.init();
    V.setHandlers({
      onKeyClick: function (id) {
        if (isDuplicateTap(id)) return;
        var def = D.keys[id];
        if (def) tapKey(def);
      },
      onSymbolClick: function (sym) {
        if (isDuplicateTap('sym:' + sym)) return;
        tapSymbol(sym);
      },
      onCandidate: commitCandidate
    });
    populatePresets();
    bindControls();
    applyTheme();
    rebuildAll();
    syncLayoutControls(findPresetLabel(state.W, state.H));
    V.renderLog(logEntries, state.logOpen);
    logEvent('KBD 自适应原型启动');
  }

  init();
})();
