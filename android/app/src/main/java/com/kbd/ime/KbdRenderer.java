package com.kbd.ime;

import java.util.List;

/**
 * 渲染层，对应 webdemo/js/view.js。
 * 只读取状态绘制，不修改任何状态；纯 Java，宿主端预览复用同一份代码。
 */
final class KbdRenderer {

    interface Sink {
        void fillRect(float left, float top, float right, float bottom, int argb);

        /** centered=true 时 cx 为文字中心，否则为左边界；maxWidth<=0 表示不省略。 */
        void text(String text, float cx, float cy, float size, int argb,
                  boolean centered, float maxWidth);
    }

    static final int MODE_PENDING = 0;
    static final int MODE_SLIDE_UP = 1;
    static final int MODE_SLIDE_DOWN = 2;

    static final class Pressed {
        String keyId;
        String symbol;
        int mode = MODE_PENDING;
        boolean pressed;
        int candIndex = -1;
        int expIndex = -1;
    }

    // 四色：深色（无边框思路：按键只比背景亮一点，副字向背景压暗）
    private int bg = 0xFF000000;
    private int keyColor = 0xFF111111;
    private int textColor = 0xFFFFFFFF;
    private int subColor = 0xFF555555;

    void setDark(boolean dark) {
        if (dark) {
            bg = 0xFF000000;        // 深色应用底（截图取样）
            keyColor = 0xFF111111;  // 只比背景亮一点，淡化按键边界
            textColor = 0xFFFFFFFF;
            subColor = 0xFF555555;  // 向背景进一步压暗，拉开与主字的对比
        } else {
            bg = 0xFFF7F7F7;        // 浅色背景（截图输入栏取样 #f7f7f7）
            keyColor = 0xFFFFFFFF;  // 按键最白（保持原型灰度顺序）
            textColor = 0xFF111111;
            subColor = 0xFF9E9E9E;  // 向背景提亮，拉开与主字的对比
        }
    }

    void render(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        sink.fillRect(0, 0, L.width, L.totalH, bg);
        if (L.landscape) {
            renderLandscape(sink, L, app, pressed);
            return;
        }
        drawStatus(sink, L, app);
        drawCandidates(sink, L, app, pressed);

        if (app.expanded) {
            drawExpanded(sink, L, app, pressed);
        } else if (app.symbols) {
            drawSymbols(sink, L, app, pressed);
        } else {
            drawLetters(sink, L, app, pressed);
        }
        drawFunctionRow(sink, L, app, pressed);
    }

    // ---------- 横屏 ----------

    private void renderLandscape(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        if (app.symbols) {
            drawLandscapeSymbols(sink, L, app, pressed);
            return;
        }
        drawLandscapeRows(sink, L, app, pressed);
        float panelH = app.expanded ? L.kbH : L.landPanelH;
        drawLandscapeCandidates(sink, L, app, pressed, panelH);
        if (app.expanded) {
            return;
        }
        drawKey(sink, L, app, L.landSpaceRect(), KbdData.KEYS.get("space"), pressed);
        if (!app.composing() && !app.escape
                && app.status != null && !app.status.isEmpty()) {
            sink.text(app.status, L.midX + L.g, L.S / 2f, L.S,
                    textColor, false, L.midW - 2 * L.g);
        }
    }

    private void drawLandscapeRows(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        for (int r = 0; r < 3; r++) {
            drawKey(sink, L, app, L.landOuterLeftRect(r),
                    KbdData.KEYS.get(KbdData.LANDSCAPE_OUTER_LEFT[r]), pressed);
            for (int c = 0; c < 5; c++) {
                drawKey(sink, L, app, L.landLeftRect(r, c),
                        KbdData.KEYS.get(KbdData.LANDSCAPE_LEFT[r][c]), pressed);
                drawKey(sink, L, app, L.landRightRect(r, c),
                        KbdData.KEYS.get(KbdData.LANDSCAPE_RIGHT[r][c]), pressed);
            }
            drawKey(sink, L, app, L.landOuterRightRect(r),
                    KbdData.KEYS.get(KbdData.LANDSCAPE_OUTER_RIGHT[r]), pressed);
        }
    }

    private void drawLandscapeSymbols(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        for (int r = 0; r < 3; r++) {
            drawKey(sink, L, app, L.landOuterLeftRect(r),
                    KbdData.KEYS.get(KbdData.LANDSCAPE_OUTER_LEFT[r]), pressed);
            drawKey(sink, L, app, L.landOuterRightRect(r),
                    KbdData.KEYS.get(KbdData.LANDSCAPE_OUTER_RIGHT[r]), pressed);
            for (int c = 0; c < L.symbolCols; c++) {
                drawSymbolCell(sink, L, app.symbolAt(r, c), L.landSymbolRect(r, c), pressed);
            }
        }
    }

    /** 横屏候选：中列 p 键一行，与竖屏同规则（偶数键宽、超长跨行、不截断）。 */
    private void drawLandscapeCandidates(Sink sink, KbdLayout L, KbdApp app,
                                         Pressed pressed, float panelH) {
        List<KbdLayout.CandRow> rows = L.candidateRows(
                app.candidates, L.landRowKeys);
        float top = 0;
        for (int ri = app.candidateRow; ri < rows.size(); ri++) {
            KbdLayout.CandRow row = rows.get(ri);
            float h = L.candRowHeight(row.slots);
            if (top + h > panelH + 0.5f) {
                break;
            }
            float[][] rects = L.candRowRects(row, L.midX, top, h);
            for (int i = 0; i < row.items.size(); i++) {
                int index = row.items.get(i).index;
                float[] rect = rects[i];
                boolean down = pressed.candIndex == index || pressed.expIndex == index;
                if (down) {
                    sink.fillRect(rect[0], rect[1], rect[2], rect[3], textColor);
                }
                String[] lines = L.candLines(
                        app.candidates.get(index), L.landRowKeys);
                for (int li = 0; li < lines.length && li < row.slots; li++) {
                    float cy = top + li * (L.S + L.g) + L.S / 2f;
                    sink.text(lines[li], (rect[0] + rect[2]) / 2, cy, L.S,
                            down ? keyColor : textColor, true, 0);
                }
            }
            top += h + L.g;
        }
    }

    // ---------- 状态行（仅引擎未就绪/出错时占候选行） ----------

    private void drawStatus(Sink sink, KbdLayout L, KbdApp app) {
        if (app.composing() || app.escape) {
            return;
        }
        if (app.status == null || app.status.isEmpty()) {
            return;
        }
        sink.text(app.status, L.g, L.S / 2f, L.S, textColor,
                false, L.width - 2 * L.g);
    }

    // ---------- 候选行 ----------

    private void drawCandidates(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        KbdLayout.CandRow row = app.barRow();
        if (row == null) {
            return;
        }
        float[][] rects = L.candRowRects(row, L.barTop, L.barH);
        for (int i = 0; i < row.items.size(); i++) {
            KbdLayout.CandItem item = row.items.get(i);
            float[] r = rects[i];
            boolean down = pressed.candIndex == item.index;
            if (down) {
                sink.fillRect(r[0], r[1], r[2], r[3], textColor);
            }
            // 常驻行只有 1 行高：超长词只露出首行
            String line = L.candLines(app.candidates.get(item.index))[0];
            sink.text(line, (r[0] + r[2]) / 2, (r[1] + r[3]) / 2,
                    L.S, down ? keyColor : textColor, true, 0);
        }
    }

    // ---------- 字母层 ----------

    private void drawLetters(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        String[][] rows = KbdData.PORTRAIT_ROWS;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 10; c++) {
                drawKey(sink, L, app, L.keyRect(r, c),
                        KbdData.KEYS.get(rows[r][c]), pressed);
            }
        }
    }

    // ---------- 符号层 ----------

    private void drawSymbols(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < L.symbolCols; c++) {
                drawSymbolCell(sink, L, app.symbolAt(r, c), L.symbolRect(r, c), pressed);
            }
        }
    }

    private void drawSymbolCell(Sink sink, KbdLayout L, String symbol,
                                float[] rect, Pressed pressed) {
        if (symbol == null || symbol.isEmpty()) {
            return;
        }
        boolean down = pressed.symbol != null && pressed.symbol.equals(symbol);
        sink.fillRect(rect[0], rect[1], rect[2], rect[3],
                down ? textColor : keyColor);
        sink.text(symbol, (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2,
                L.S, down ? keyColor : textColor, true, rect[2] - rect[0]);
    }

    // ---------- 展开层 ----------

    private void drawExpanded(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        List<KbdLayout.CandRow> rows = L.candidateRows(app.candidates);
        float top = L.expTop;
        float bottom = L.expTop + L.expH;
        for (int ri = app.candidateRow + 1; ri < rows.size(); ri++) {
            KbdLayout.CandRow row = rows.get(ri);
            float h = L.candRowHeight(row.slots);
            if (top + h > bottom + 0.5f) {
                break;
            }
            float[][] rects = L.candRowRects(row, top, h);
            for (int i = 0; i < row.items.size(); i++) {
                KbdLayout.CandItem item = row.items.get(i);
                float[] r = rects[i];
                boolean down = pressed.expIndex == item.index;
                if (down) {
                    sink.fillRect(r[0], r[1], r[2], r[3], textColor);
                }
                String[] lines = L.candLines(app.candidates.get(item.index));
                for (int li = 0; li < lines.length && li < row.slots; li++) {
                    float cy = top + li * (L.S + L.g) + L.S / 2f;
                    sink.text(lines[li], (r[0] + r[2]) / 2, cy, L.S,
                            down ? keyColor : textColor, true, 0);
                }
            }
            top += h + L.g;
        }
    }

    // ---------- 功能行 ----------

    private void drawFunctionRow(Sink sink, KbdLayout L, KbdApp app, Pressed pressed) {
        String[] ids = KbdData.PORTRAIT_ROWS[3];
        for (int i = 0; i < ids.length; i++) {
            drawKey(sink, L, app, L.funcRect(i), KbdData.KEYS.get(ids[i]), pressed);
        }
    }

    // ---------- 单键 ----------

    private void drawKey(Sink sink, KbdLayout L, KbdApp app, float[] rect,
                         KbdData.Key key, Pressed pressed) {
        if (key == null) {
            return;
        }
        boolean isPressed = pressed != null && key.id.equals(pressed.keyId);
        boolean sliding = isPressed
                && (pressed.mode == MODE_SLIDE_UP || pressed.mode == MODE_SLIDE_DOWN);
        boolean active = isActive(app, key);
        boolean inverse = active || (isPressed && pressed.pressed);

        sink.fillRect(rect[0], rect[1], rect[2], rect[3],
                inverse ? textColor : keyColor);

        int mainColor = inverse ? keyColor : textColor;
        int dimColor = subColor;
        float size = L.S;
        float w = rect[2] - rect[0];

        if (sliding) {
            String label = pressed.mode == MODE_SLIDE_UP
                    ? keyUpText(app, key) : keyDownText(app, key);
            if (!label.isEmpty()) {
                sink.text(label, (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2,
                        size, mainColor, true, w - L.g);
            }
            return;
        }

        boolean compact = L.compactLabels();
        String main = key.kind == KbdData.Kind.MODIFIER
                ? modifierLabel(app, key, compact)
                : keyMainText(app, key);
        String up = fitSubLabel(keyUpText(app, key), compact);
        String down = fitSubLabel(keyDownText(app, key), compact);
        boolean single = up.isEmpty() && down.isEmpty();

        if (single) {
            sink.text(main, (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2,
                    size, mainColor, true, w);
        } else {
            boolean pressingNow = isPressed && pressed.mode == MODE_PENDING
                    && pressed.pressed;
            if (!pressingNow) {
                if (!up.isEmpty()) {
                    sink.text(up, (rect[0] + rect[2]) / 2, rect[1] + L.S / 2f,
                            size, dimColor, true, w - L.g);
                }
                if (!down.isEmpty()) {
                    sink.text(down, (rect[0] + rect[2]) / 2,
                            rect[1] + L.keyH - L.S / 2f,
                            size, dimColor, true, w - L.g);
                }
            }
            sink.text(main, (rect[0] + rect[2]) / 2, rect[1] + L.keyH / 2f,
                    size, mainColor, true, w);
        }
    }

    /** Tab/Esc 在紧凑模式下缩成 Ta/Es。 */
    private static String fitSubLabel(String label, boolean compact) {
        if (!compact) {
            return label;
        }
        if ("Tab".equals(label)) {
            return "Ta";
        }
        if ("Esc".equals(label)) {
            return "Es";
        }
        return label;
    }

    /** 修饰键同宽，按最长标签统一决定是否用 Ct/Al/Sh。 */
    private static String modifierLabel(KbdApp app, KbdData.Key key, boolean compact) {
        String full = key.main.pick(app.chinese);
        String shortLabel;
        switch (key.mod) {
            case "ctrl":
                shortLabel = "Ct";
                break;
            case "alt":
                shortLabel = "Al";
                break;
            case "shift":
                shortLabel = "Sh";
                break;
            default:
                return full;
        }
        return compact ? shortLabel : full;
    }

    private static boolean isActive(KbdApp app, KbdData.Key key) {
        if (key.kind == KbdData.Kind.MODIFIER) {
            KbdApp.Mod mod = app.mods.get(key.mod);
            return mod != null && mod.active;
        }
        if ("lang".equals(key.id)) {
            return app.symbols;
        }
        if ("space".equals(key.id)) {
            return app.escape;
        }
        return false;
    }

    static String keyMainText(KbdApp app, KbdData.Key key) {
        switch (key.kind) {
            case LANG:
                return app.symbols ? "符" : (app.chinese ? "中" : "英");
            case ENTER:
                return app.composing()
                        ? (app.expanded ? "返回" : "展开") : "确认";
            case SPACE:
                return app.escape ? "\\" : "空格";
            case LETTER:
                return app.mods.get("shift").active ? key.id.toUpperCase() : key.id;
            default:
                return key.main.pick(app.chinese);
        }
    }

    static String keyUpText(KbdApp app, KbdData.Key key) {
        if ("lang".equals(key.id) && app.symbols) {
            return String.valueOf(app.symbolRow + 1);
        }
        if (app.chinese && "f".equals(key.id)) {
            return app.nextDoubleQuote();
        }
        return key.up == null ? "" : key.up.pick(app.chinese);
    }

    static String keyDownText(KbdApp app, KbdData.Key key) {
        if ("lang".equals(key.id) && app.symbols) {
            return String.valueOf(app.symbolRowCount());
        }
        if (app.chinese && "f".equals(key.id)) {
            return app.nextSingleQuote();
        }
        return key.down == null ? "" : key.down.pick(app.chinese);
    }
}
