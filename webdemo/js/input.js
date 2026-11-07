(function () {
  'use strict';

  // 输入层：只识别手势（点按/滑动/长按/连发/符号翻页），
  // 把手势翻译成语义意图交给 app，不直接改业务状态。

  var LONG_PRESS_MS = 500;
  var REPEAT_MS = 60;
  var SLIDE_ENTER = 25;
  var SLIDE_EXIT = 12;
  var SWIPE_PX = 30;

  var D = window.KBD;

  function bind(opts) {
    var root = opts.root;
    var toLogicalX = opts.toLogicalX;
    var toLogicalY = opts.toLogicalY;
    var getSlideLabel = opts.getSlideLabel;
    var allowPageSwipe = opts.allowPageSwipe;
    var H = opts.handlers;
    var sessions = new Map();

    function findSymbol(el) {
      return el && el.classList.contains('symbol-key') && el.dataset.symbol;
    }

    function onPointerDown(e) {
      var key = e.target.closest('.key');
      if (sessions.size >= 2) return;
      var isSymbolKey = !!key && key.classList.contains('symbol-key');
      var symbol = findSymbol(key);
      // 符号层：空白符号格与键盘背景也能滑动翻页。
      var pageOnly = allowPageSwipe() && ((isSymbolKey && !symbol) || !key);
      if (!key && !pageOnly) return;
      if (key && !isSymbolKey && key.classList.contains('empty')) return;
      var id = key ? key.dataset.id : null;
      if (!id && !symbol && !pageOnly) return;
      e.preventDefault();
      try { root.setPointerCapture(e.pointerId); } catch (err) {}

      var session = {
        pointerId: e.pointerId,
        keyEl: key || root,
        keyId: id || null,
        symbol: symbol || null,
        pageSwipe: !!symbol || pageOnly,
        startY: toLogicalY(e.clientY),
        startX: toLogicalX(e.clientX),
        mode: (symbol || pageOnly) ? 'symbol-pending' : 'pending',
        dir: null,
        timer: null,
        repeatTimer: null,
        pressed: true
      };

      if (symbol || pageOnly) {
        if (key) key.classList.add('pressed');
      } else {
        var def = D.keys[id];
        key.classList.add('pressed');
        key.classList.add('pressing');
        if (def && (def.long || def.repeat)) {
          session.timer = setTimeout(function () {
            session.timer = null;
            if (session.mode !== 'pending') return;
            if (def.repeat) {
              session.mode = 'repeat';
              H.repeatKey(def);
              session.repeatTimer = setInterval(function () { H.repeatKey(def); }, REPEAT_MS);
            } else {
              session.mode = 'long';
              H.longKey(def);
            }
          }, LONG_PRESS_MS);
        }
      }
      sessions.set(e.pointerId, session);
    }

    function onPointerMove(e) {
      var s = sessions.get(e.pointerId);
      if (!s) return;
      if (s.mode === 'long' || s.mode === 'repeat') return;
      var y = toLogicalY(e.clientY);

      if (s.pageSwipe) {
        if (Math.abs(y - s.startY) >= SWIPE_PX) {
          s.mode = 'swipe';
          s.dir = y < s.startY ? 'up' : 'down';
          s.keyEl.classList.remove('pressed');
        }
        return;
      }

      var def = D.keys[s.keyId];
      if (!def || (!def.up && !def.down)) return;
      var dy = y - s.startY;

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
      var def = D.keys[s.keyId];
      if (!def) return;
      if (s.timer) { clearTimeout(s.timer); s.timer = null; }
      s.mode = 'slide';
      s.dir = dir;
      s.keyEl.classList.remove('pressing', 'slide-up', 'slide-down', 'null-slide');
      s.keyEl.classList.add(dir === 'up' ? 'slide-up' : 'slide-down');
      if (!getSlideLabel(s.keyId, dir)) s.keyEl.classList.add('null-slide');
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

    function onPointerUp(e) {
      var s = sessions.get(e.pointerId);
      if (!s) return;
      if (s.timer) { clearTimeout(s.timer); s.timer = null; }
      if (s.repeatTimer) { clearInterval(s.repeatTimer); s.repeatTimer = null; }
      s.keyEl.classList.remove('pressed', 'pressing', 'slide-up', 'slide-down', 'null-slide');
      sessions.delete(e.pointerId);
      try { root.releasePointerCapture(e.pointerId); } catch (err) {}

      H.markTap(s.symbol ? ('sym:' + s.symbol) : s.keyId);

      if (s.pageSwipe) {
        if (s.mode === 'swipe') H.pageSwipe(s.dir === 'up' ? 1 : -1);
        else if (s.symbol) H.symbolTap(s.symbol);
        return;
      }

      if (s.mode === 'pending') {
        var def = D.keys[s.keyId];
        if (def) H.tapKey(def);
        return;
      }
      if (s.mode === 'slide') {
        var slideDef = D.keys[s.keyId];
        if (!slideDef) return;
        if (!getSlideLabel(s.keyId, s.dir)) {
          H.emptySlide(slideDef);
          return;
        }
        H.slideKey(slideDef, s.dir);
      }
    }

    function onPointerCancel(e) {
      var s = sessions.get(e.pointerId);
      if (s) cancelSession(s);
    }

    function attach() {
      root.addEventListener('pointerdown', onPointerDown, { passive: false });
      window.addEventListener('pointermove', onPointerMove);
      window.addEventListener('pointerup', onPointerUp);
      window.addEventListener('pointercancel', onPointerCancel);
    }

    return { attach: attach };
  }

  window.KBD.input = { bind: bind };
})();
