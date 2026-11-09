package com.kbd.ime;

import java.util.List;

/**
 * 手势层，对应 webdemo/js/input.js。
 * 只识别手势并产出语义事件，绝不直接修改 KbdApp 的状态。
 * 纯 Java，可脱离 Android 运行测试。
 */
final class KbdGestures {

    static final int LONG_PRESS_MS = 400;
    static final int REPEAT_MS = 60;
    static final float SLIDE_ENTER = 25;
    static final float SLIDE_EXIT = 12;

    /** 计时器由宿主实现：长按与连发。 */
    interface Timer {
        void armLongPress();

        void armRepeat();

        void cancel();
    }

    private static final int KIND_KEY = 0;
    private static final int KIND_CANDIDATE = 1;
    private static final int KIND_SYMBOL = 2;
    private static final int KIND_PANEL = 3;
    private static final int KIND_SCROLL = 4;

    private static final class Session {
        int kind;
        String keyId;
        String symbol;
        int candIndex;
        float[] rect;
        float startY;
        int appliedRows;
        int mode = KbdRenderer.MODE_PENDING;
        boolean pressed;
        /** 长按已触发：松手不得再执行点按（同 webdemo input.js 的 long/repeat 态）。 */
        boolean longFired;
    }

    private final KbdLayout layout;
    private final KbdApp app;
    private final Timer timer;

    private Session session;

    KbdGestures(KbdLayout layout, KbdApp app, Timer timer) {
        this.layout = layout;
        this.app = app;
        this.timer = timer;
    }

    KbdRenderer.Pressed pressed() {
        KbdRenderer.Pressed p = new KbdRenderer.Pressed();
        if (session != null) {
            p.keyId = session.keyId;
            p.mode = session.mode;
            p.pressed = session.pressed;
            if (session.kind == KIND_SYMBOL && session.appliedRows == 0) {
                p.symbol = session.symbol;
            }
            if (session.kind == KIND_CANDIDATE) {
                p.candIndex = session.candIndex;
            }
            if (session.kind == KIND_PANEL) {
                p.expIndex = session.candIndex;
            }
        }
        return p;
    }

    // ---------- 按下 ----------

    boolean down(float x, float y) {
        if (session != null) {
            return true; // 已有会话，第二指忽略
        }
        if (layout.landscape) {
            return downLandscape(x, y);
        }

        if (hitBarCandidate(x, y)) {
            return true;
        }

        if (app.expanded) {
            if (hitPanelCandidate(x, y)) {
                return true;
            }
            String fid = functionAt(x, y);
            if (fid != null) {
                startKey(fid, y);
                return true;
            }
            session = scrollSession(y);
            return true;
        }

        if (app.symbols) {
            int[] cell = symbolCellAt(x, y);
            if (cell != null) {
                startSymbol(cell[0], cell[1], y);
                return true;
            }
            String fid = functionAt(x, y);
            if (fid != null) {
                startKey(fid, y);
                return true;
            }
            session = scrollSession(y);
            return true;
        }

        String id = keyAt(x, y);
        if (id == null) {
            return false;
        }
        startKey(id, y);
        return true;
    }

    // ---------- 横屏 ----------

    private boolean downLandscape(float x, float y) {
        if (app.symbols) {
            int[] cell = landscapeSymbolCellAt(x, y);
            if (cell != null) {
                startSymbol(cell[0], cell[1], y);
                return true;
            }
            String fid = landscapeOuterAt(x, y);
            if (fid != null) {
                startKey(fid, y);
                return true;
            }
            session = scrollSession(y);
            return true;
        }

        float panelH = app.expanded ? layout.kbH : layout.landPanelH;
        if (hitLandscapeCandidate(x, y, panelH)) {
            return true;
        }
        String id = landscapeKeyAt(x, y);
        if (id != null) {
            startKey(id, y);
            return true;
        }
        if (app.expanded) {
            session = scrollSession(y);
        }
        return false;
    }

    private void startSymbol(int row, int col, float y) {
        session = new Session();
        session.kind = KIND_SYMBOL;
        session.symbol = app.symbolAt(row, col);
        session.rect = layout.landscape
                ? layout.landSymbolRect(row, col) : layout.symbolRect(row, col);
        session.startY = y;
        session.pressed = true;
    }

    private void startKey(String id, float y) {
        session = new Session();
        session.kind = KIND_KEY;
        session.keyId = id;
        session.startY = y;
        session.pressed = true;

        KbdData.Key def = KbdData.KEYS.get(id);
        if (def != null && (def.longAction != null || def.repeat)) {
            timer.armLongPress();
        }
    }

    private Session scrollSession(float y) {
        Session s = new Session();
        s.kind = KIND_SCROLL;
        s.startY = y;
        s.pressed = true;
        return s;
    }

    // ---------- 移动 ----------

    void move(float y) {
        if (session == null) {
            return;
        }
        if (session.kind == KIND_SCROLL || session.kind == KIND_SYMBOL
                || session.kind == KIND_PANEL) {
            int rows = rowsFor(y);
            if (rows != session.appliedRows) {
                int delta = rows - session.appliedRows;
                session.appliedRows = rows;
                if (app.symbols) {
                    app.scrollSymbols(delta);
                } else if (app.expanded) {
                    app.scrollCandidates(delta);
                }
            }
            return;
        }
        if (session.kind != KIND_KEY) {
            return;
        }
        if (session.longFired) {
            return;
        }
        KbdData.Key def = KbdData.KEYS.get(session.keyId);
        if (def == null || (def.up == null && def.down == null)) {
            return;
        }
        if (session.mode == KbdRenderer.MODE_SLIDE_UP
                || session.mode == KbdRenderer.MODE_SLIDE_DOWN) {
            // 已经进入滑动：按退出阈值判断回退/切换
        } else if (session.pressed) {
            // 待定
        } else {
            return;
        }

        float dy = y - session.startY;
        float enter = Math.max(1f, layout.S * SLIDE_ENTER / 32f);
        float exit = Math.max(1f, layout.S * SLIDE_EXIT / 32f);

        if (session.mode == KbdRenderer.MODE_PENDING) {
            if (dy <= -enter) {
                slide(KbdRenderer.MODE_SLIDE_UP);
            } else if (dy >= enter) {
                slide(KbdRenderer.MODE_SLIDE_DOWN);
            }
            return;
        }
        if (session.mode == KbdRenderer.MODE_SLIDE_UP) {
            if (dy > -exit) {
                clearSlide();
            } else if (dy >= enter) {
                slide(KbdRenderer.MODE_SLIDE_DOWN);
            }
        } else if (dy < exit) {
            clearSlide();
        } else if (dy <= -enter) {
            slide(KbdRenderer.MODE_SLIDE_UP);
        }
    }

    private int rowsFor(float y) {
        float step = app.symbols
                ? (layout.keyH + layout.g)
                : (layout.expRowH + layout.expGap);
        float dy = session.startY - y;
        int rows = (int) (Math.abs(dy) / Math.max(1f, step)) * (dy >= 0 ? 1 : -1);
        return rows;
    }

    private void slide(int mode) {
        timer.cancel();
        session.mode = mode;
        session.pressed = true;
    }

    private void clearSlide() {
        session.mode = KbdRenderer.MODE_PENDING;
        session.pressed = true;
    }

    // ---------- 松手 ----------

    void up(float x, float y) {
        if (session == null) {
            return;
        }
        Session s = session;
        session = null;
        timer.cancel();

        switch (s.kind) {
            case KIND_CANDIDATE:
            case KIND_PANEL:
                if (s.appliedRows == 0 && KbdLayout.hit(s.rect, x, y)) {
                    app.commitCandidate(s.candIndex);
                }
                return;
            case KIND_SCROLL:
                return;
            case KIND_SYMBOL:
                if (s.appliedRows == 0 && s.symbol != null && !s.symbol.isEmpty()
                        && KbdLayout.hit(s.rect, x, y)) {
                    app.tapSymbol(s.symbol);
                }
                return;
            default:
                break;
        }

        KbdData.Key def = KbdData.KEYS.get(s.keyId);
        if (def == null) {
            return;
        }
        if (s.mode == KbdRenderer.MODE_SLIDE_UP) {
            String label = KbdRenderer.keyUpText(app, def);
            if (label.isEmpty()) {
                app.emptySlide(def.id);
            } else {
                app.emitChar(label);
            }
            return;
        }
        if (s.mode == KbdRenderer.MODE_SLIDE_DOWN) {
            String label = KbdRenderer.keyDownText(app, def);
            if (label.isEmpty()) {
                app.emptySlide(def.id);
            } else {
                app.emitChar(label);
            }
            return;
        }
        // 待定（或长按已触发）：长按已触发的不再执行点按
        if (!s.longFired) {
            app.tapKey(def.id);
        }
    }

    void cancel() {
        session = null;
        timer.cancel();
    }

    /** 长按计时到点。 */
    void longPressDue() {
        if (session == null || session.kind != KIND_KEY
                || session.mode != KbdRenderer.MODE_PENDING) {
            return;
        }
        KbdData.Key def = KbdData.KEYS.get(session.keyId);
        if (def == null) {
            return;
        }
        if (def.repeat) {
            session.longFired = true;
            app.repeatKey(def.id);
            timer.armRepeat();
            return;
        }
        if (def.longAction != null) {
            session.longFired = true;
            session.pressed = true;
            app.longKey(def.id);
        }
    }

    /** 连发计时到点。 */
    void repeatDue() {
        if (session == null || session.kind != KIND_KEY) {
            return;
        }
        KbdData.Key def = KbdData.KEYS.get(session.keyId);
        if (def == null || !def.repeat) {
            return;
        }
        app.repeatKey(def.id);
    }

    // ---------- 命中 ----------

    /** 常驻候选行：按当前行的可变宽度逐格命中。 */
    private boolean hitBarCandidate(float x, float y) {
        KbdLayout.CandRow row = app.barRow();
        if (row == null) {
            return false;
        }
        float[][] rects = layout.candRowRects(row, layout.barTop, layout.barH);
        for (int i = 0; i < row.items.size(); i++) {
            if (KbdLayout.hit(rects[i], x, y)) {
                session = new Session();
                session.kind = KIND_CANDIDATE;
                session.candIndex = row.items.get(i).index;
                session.rect = rects[i];
                session.startY = y;
                session.pressed = true;
                return true;
            }
        }
        return false;
    }

    /** 展开面板：按流式行/可变宽度命中，序号即候选序号。 */
    private boolean hitPanelCandidate(float x, float y) {
        List<KbdLayout.CandRow> rows = layout.candidateRows(app.candidates);
        float top = layout.expTop;
        float bottom = layout.expTop + layout.expH;
        for (int ri = app.candidateRow + 1; ri < rows.size(); ri++) {
            KbdLayout.CandRow row = rows.get(ri);
            float h = layout.candRowHeight(row.slots);
            if (top + h > bottom + 0.5f) {
                break;
            }
            float[][] rects = layout.candRowRects(row, top, h);
            for (int i = 0; i < row.items.size(); i++) {
                if (KbdLayout.hit(rects[i], x, y)) {
                    session = new Session();
                    session.kind = KIND_PANEL;
                    session.candIndex = row.items.get(i).index;
                    session.rect = rects[i];
                    session.startY = y;
                    session.pressed = true;
                    return true;
                }
            }
            top += h + layout.g;
        }
        return false;
    }

    private int[] symbolCellAt(float x, float y) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < layout.symbolCols; c++) {
                if (KbdLayout.hit(layout.symbolRect(r, c), x, y)) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    private String landscapeKeyAt(float x, float y) {
        for (int r = 0; r < 3; r++) {
            if (KbdLayout.hit(layout.landOuterLeftRect(r), x, y)) {
                return KbdData.LANDSCAPE_OUTER_LEFT[r];
            }
            if (KbdLayout.hit(layout.landOuterRightRect(r), x, y)) {
                return KbdData.LANDSCAPE_OUTER_RIGHT[r];
            }
            for (int c = 0; c < 5; c++) {
                if (KbdLayout.hit(layout.landLeftRect(r, c), x, y)) {
                    return KbdData.LANDSCAPE_LEFT[r][c];
                }
                if (KbdLayout.hit(layout.landRightRect(r, c), x, y)) {
                    return KbdData.LANDSCAPE_RIGHT[r][c];
                }
            }
        }
        if (KbdLayout.hit(layout.landSpaceRect(), x, y)) {
            return "space";
        }
        return null;
    }

    /** 符号层只剩左右外列可点。 */
    private String landscapeOuterAt(float x, float y) {
        for (int r = 0; r < 3; r++) {
            if (KbdLayout.hit(layout.landOuterLeftRect(r), x, y)) {
                return KbdData.LANDSCAPE_OUTER_LEFT[r];
            }
            if (KbdLayout.hit(layout.landOuterRightRect(r), x, y)) {
                return KbdData.LANDSCAPE_OUTER_RIGHT[r];
            }
        }
        return null;
    }

    private int[] landscapeSymbolCellAt(float x, float y) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < layout.symbolCols; c++) {
                if (KbdLayout.hit(layout.landSymbolRect(r, c), x, y)) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    private boolean hitLandscapeCandidate(float x, float y, float panelH) {
        List<KbdLayout.CandRow> rows = layout.candidateRows(
                app.candidates, layout.landRowKeys);
        float top = 0;
        for (int ri = app.candidateRow; ri < rows.size(); ri++) {
            KbdLayout.CandRow row = rows.get(ri);
            float h = layout.candRowHeight(row.slots);
            if (top + h > panelH + 0.5f) {
                break;
            }
            float[][] rects = layout.candRowRects(row, layout.midX, top, h);
            for (int i = 0; i < row.items.size(); i++) {
                if (KbdLayout.hit(rects[i], x, y)) {
                    session = new Session();
                    session.kind = app.expanded ? KIND_PANEL : KIND_CANDIDATE;
                    session.candIndex = row.items.get(i).index;
                    session.rect = rects[i];
                    session.startY = y;
                    session.pressed = true;
                    return true;
                }
            }
            top += h + layout.g;
        }
        return false;
    }

    private String functionAt(float x, float y) {
        String[] ids = KbdData.PORTRAIT_ROWS[3];
        for (int i = 0; i < ids.length; i++) {
            if (KbdLayout.hit(layout.funcRect(i), x, y)) {
                return ids[i];
            }
        }
        return null;
    }

    private String keyAt(float x, float y) {
        String[][] rows = KbdData.PORTRAIT_ROWS;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 10; c++) {
                if (KbdLayout.hit(layout.keyRect(r, c), x, y)) {
                    return rows[r][c];
                }
            }
        }
        return functionAt(x, y);
    }
}
