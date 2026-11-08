package com.kbd.ime;

import java.util.ArrayList;
import java.util.List;

/**
 * 几何层，对应 webdemo/js/layout.js 的 portrait / landscape。
 * 只做数值计算，不持有状态、不绘制。
 */
final class KbdLayout {

    static final int S = 32;
    static final int G = 4;
    static final int KEY_H = 3 * S + 2 * G;   // 104
    static final int MAIN_H = 3 * KEY_H + 2 * G;  // 320
    static final int K_BASE = 68;
    static final int BASE_W = 720;            // 竖屏设计宽
    static final int LANDSCAPE_W = 1604;      // 横屏设计宽

    final int width;
    final float scale;
    final boolean landscape;

    final int side;
    final int k;
    final int doubleW;
    final int barH;      // 仅竖屏
    final int barTop;    // 仅竖屏
    final int mainTop;   // 仅竖屏
    final int mainH;     // 仅竖屏
    final int funcTop;   // 仅竖屏
    final int keyH;
    final int kbH;
    final int totalH;

    final int candSide;  // 仅竖屏

    final int expGap;
    final int expRowH;
    final int expTop;    // 仅竖屏
    final int expH;      // 仅竖屏

    // 横屏三行分栏
    final int outerW;
    final int letterX;
    final int letterW;
    final int midX;
    final int midW;
    final int rightX;
    final int outerRightX;
    final int symbolX;
    final int symbolCols;
    final int[] rowY;

    // 拼音浮层（webdemo .floating-preedit）：键盘外、文字宽、32px 高，
    // 与键盘上沿隔 4px，用同色补满这 4px
    final int preeditH;
    final int preeditPad;
    final int preeditGap;
    final int preeditFont;
    final int preeditLeft;
    final int preeditWidth;

    KbdLayout(int width, boolean landscape) {
        this.width = width;
        this.landscape = landscape;
        this.scale = width / (float) (landscape ? LANDSCAPE_W : BASE_W);

        k = s(K_BASE);
        keyH = s(KEY_H);
        expGap = s(G);
        expRowH = s(S);

        if (landscape) {
            side = s(84);
            doubleW = 0;
            barH = 0;
            barTop = 0;
            mainTop = 0;
            mainH = 0;
            funcTop = 0;
            kbH = s(320);
            totalH = kbH;
            candSide = 0;
            expTop = 0;
            expH = 0;
            outerW = s(140);
            letterX = side + s(144);
            letterW = s(356);
            midX = side + s(504);
            midW = s(428);
            rightX = side + s(936);
            outerRightX = side + s(1296);
            symbolX = letterX;
            symbolCols = 16;
            rowY = new int[]{0, s(KEY_H + G), s(2 * (KEY_H + G))};
            preeditLeft = midX;
            preeditWidth = midW;
        } else {
            side = s(2);
            doubleW = 2 * k + s(G);
            barH = s(S);
            barTop = 0;
            mainTop = barH + s(G);                   // 36
            mainH = s(MAIN_H);                       // 320
            funcTop = mainTop + mainH + s(G);        // 360
            kbH = s(496);
            totalH = kbH;
            candSide = s(2);
            expTop = mainTop;
            expH = mainH;
            outerW = 0;
            letterX = 0;
            letterW = 0;
            midX = 0;
            midW = 0;
            rightX = 0;
            outerRightX = 0;
            symbolX = 0;
            symbolCols = 10;
            rowY = new int[]{0, 0, 0};
            preeditLeft = 0;
            preeditWidth = width;
        }

        preeditH = s(S);
        preeditPad = s(2);
        preeditGap = s(G);
        preeditFont = s(S);
    }

    int s(int designPx) {
        return Math.round(designPx * scale);
    }

    float[] keyRect(int row, int col) {
        float x = side + col * (k + s(G));
        float y = mainTop + row * (keyH + s(G));
        return new float[]{x, y, x + k, y + keyH};
    }

    /** 符号格与字母键同网格。 */
    float[] symbolRect(int row, int col) {
        return keyRect(row, col);
    }

    /** 功能行第 index 个键的 x 起点。 */
    float funcX(int index) {
        float x = side;
        String[] ids = KbdData.PORTRAIT_ROWS[3];
        for (int i = 0; i < index && i < ids.length; i++) {
            KbdData.Key key = KbdData.KEYS.get(ids[i]);
            x += (key != null && key.wide ? doubleW : k) + s(G);
        }
        return x;
    }

    float funcWidth(int index) {
        String[] ids = KbdData.PORTRAIT_ROWS[3];
        if (index < 0 || index >= ids.length) {
            return 0;
        }
        KbdData.Key key = KbdData.KEYS.get(ids[index]);
        return key != null && key.wide ? doubleW : k;
    }

    float[] funcRect(int index) {
        float x = funcX(index);
        return new float[]{x, funcTop, x + funcWidth(index), funcTop + keyH};
    }

    // ---------- 横屏：三行分栏 ----------

    private float[] landRect(float x, float w, int row) {
        float y = rowY[row];
        return new float[]{x, y, x + w, y + keyH};
    }

    float[] landOuterLeftRect(int row) {
        return landRect(side, outerW, row);
    }

    float[] landOuterRightRect(int row) {
        return landRect(outerRightX, outerW, row);
    }

    float[] landLeftRect(int row, int col) {
        return landRect(letterX + col * (k + s(G)), k, row);
    }

    float[] landRightRect(int row, int col) {
        return landRect(rightX + col * (k + s(G)), k, row);
    }

    float[] landSymbolRect(int row, int col) {
        return landRect(symbolX + col * (k + s(G)), k, row);
    }

    float[] landSpaceRect() {
        float y = rowY[2];
        return new float[]{midX, y, midX + midW, y + keyH};
    }

    /** 命中：点在矩形内（左闭右开）。 */
    static boolean hit(float[] r, float x, float y) {
        return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    // ---------- 候选格：最小 2 键宽的偶数倍，超长词跨行（竖/横共用） ----------

    static final int CAND_ROW_KEYS = 10;    // 竖屏一整行 = 10 键位
    static final int LAND_ROW_KEYS = 6;     // 横屏中列 = 6 键位（428）
    static final int CAND_MIN_KEYS = 2;
    static final int CAND_TEXT_MARGIN = 8;
    static final int CAND_PANEL_H = MAIN_H;   // 展开面板高 320
    static final int LAND_PANEL_H = 212;      // 横屏未展开面板高（6 × 32 + 5 × 4）

    /** 候选格宽（base px）：占 keys 个键位。 */
    static int candCellBase(int keys) {
        return keys * K_BASE + (keys - 1) * G;
    }

    /** Unifont 32px 的确定性字宽：全角 32、半角 16（与自检“汉字宽 = 2 × ASCII”一致）。 */
    static int textBaseWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += fullWidth(text.charAt(i)) ? S : S / 2;
        }
        return width;
    }

    private static boolean fullWidth(char c) {
        return c >= 0x1100 && (c <= 0x115F || c == 0x2329 || c == 0x232A
                || (c >= 0x2E80 && c <= 0xA4CF && c != 0x303F)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6));
    }

    static int candKeys(String text) {
        return candKeys(text, CAND_ROW_KEYS);
    }

    /** 候选格最小偶数键宽：不超过一行的最大键宽；超出则保持最大键宽并折行。 */
    static int candKeys(String text, int rowKeys) {
        int width = textBaseWidth(text) + CAND_TEXT_MARGIN;
        for (int keys = CAND_MIN_KEYS; keys <= rowKeys; keys += 2) {
            if (width <= candCellBase(keys)) {
                return keys;
            }
        }
        return rowKeys;
    }

    static String[] candLines(String text) {
        return candLines(text, CAND_ROW_KEYS);
    }

    /** 超过一行宽时按行宽折行；否则只有一行。 */
    static String[] candLines(String text, int rowKeys) {
        int max = candCellBase(rowKeys) - CAND_TEXT_MARGIN;
        if (textBaseWidth(text) <= max) {
            return new String[]{text};
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int cw = fullWidth(c) ? S : S / 2;
            if (width + cw > max && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                width = 0;
            }
            line.append(c);
            width += cw;
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.toArray(new String[0]);
    }

    static final class CandItem {
        final int index;   // 候选序号
        final int keys;    // 占用的键宽（偶数）

        CandItem(int index, int keys) {
            this.index = index;
            this.keys = keys;
        }
    }

    static final class CandRow {
        final List<CandItem> items = new ArrayList<>();
        int keys;    // 本行已用键宽
        int slots;   // 行高 = slots 个文字行（超长词为 2）
    }

    static List<CandRow> candidateRows(List<String> candidates) {
        return candidateRows(candidates, CAND_ROW_KEYS);
    }

    /** 候选按 rowKeys 键一行装箱；超长词独占整行并占多行高。 */
    static List<CandRow> candidateRows(List<String> candidates, int rowKeys) {
        List<CandRow> rows = new ArrayList<>();
        CandRow row = null;
        for (int i = 0; i < candidates.size(); i++) {
            String text = candidates.get(i);
            int keys = candKeys(text, rowKeys);
            int slots = candLines(text, rowKeys).length;
            if (row == null || row.keys + keys > rowKeys) {
                row = new CandRow();
                rows.add(row);
            }
            row.items.add(new CandItem(i, keys));
            row.keys += keys;
            row.slots = Math.max(row.slots, slots);
        }
        return rows;
    }

    static int candRowBaseHeight(int slots) {
        return slots * S + (slots - 1) * G;
    }

    static int candScrollMax(List<CandRow> rows) {
        return candScrollMax(rows, CAND_PANEL_H);
    }

    /** 竖屏：常驻行占 1 行，上限 = 末行 − 可容纳行数。 */
    static int candScrollMax(List<CandRow> rows, int panelH) {
        if (rows.isEmpty()) {
            return 0;
        }
        return Math.max(0, rows.size() - 1 - rowsFitting(rows, panelH));
    }

    /** 横屏：面板从第 k 行开始铺满，上限 = 行数 − 可容纳行数。 */
    static int candPanelScrollMax(List<CandRow> rows, int panelH) {
        if (rows.isEmpty()) {
            return 0;
        }
        return Math.max(0, rows.size() - rowsFitting(rows, panelH));
    }

    private static int rowsFitting(List<CandRow> rows, int panelH) {
        int used = 0;
        int count = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            int need = candRowBaseHeight(rows.get(i).slots) + (count > 0 ? G : 0);
            if (used + need > panelH) {
                break;
            }
            used += need;
            count++;
        }
        return count;
    }

    float candCellWidth(int keys) {
        return s(candCellBase(keys));
    }

    float candRowHeight(int slots) {
        return s(candRowBaseHeight(slots));
    }

    /** 一行候选的格子矩形：从候选区左缘起、逐格累加。 */
    float[][] candRowRects(CandRow row, float top, float height) {
        return candRowRects(row, candSide, top, height);
    }

    float[][] candRowRects(CandRow row, float left, float top, float height) {
        float[][] rects = new float[row.items.size()][];
        float x = left;
        for (int i = 0; i < row.items.size(); i++) {
            float w = candCellWidth(row.items.get(i).keys);
            rects[i] = new float[]{x, top, x + w, top + height};
            x += w + s(G);
        }
        return rects;
    }
}
