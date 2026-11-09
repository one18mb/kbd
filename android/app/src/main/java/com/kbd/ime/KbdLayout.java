package com.kbd.ime;

import java.util.ArrayList;
import java.util.List;

/**
 * 几何层，对应 webdemo/js/layout.js。
 * 输入窗口 (W,H) 与底部安全区 b，模式由 W/H 比例计算，不读系统方向。
 */
final class KbdLayout {

    /** 屏幕 W/H 阈值：> R 横屏，≤ R 竖屏。 */
    static final float R = 0.75f;

    static final int CAND_TEXT_MARGIN = 8;
    static final int CAND_MIN_KEYS = 2;
    static final int PORTRAIT_ROW_KEYS = 10;

    final int width;
    final int height;
    final int b;
    final boolean landscape;

    int n;
    int S;          // 字体/行高
    int t;
    int k;          // 键宽（偶数）
    int g;          // 普通缝
    int c;          // 中缝
    int e;          // c-g
    int m;          // 左右外边距（单侧）
    int keyH;
    int kbW;
    int kbLeft;
    int kbH;
    int totalH;

    // 竖屏
    int side;
    int doubleW;
    int spaceW;
    int barH;
    int barTop;
    int mainTop;
    int mainH;
    int funcTop;
    int candCellW;
    int candGap;
    int candSide;
    int candH;
    int expTop;
    int expH;
    int expRows;
    int expCols;
    int expCellW;
    int expGap;
    int expSide;
    int expRowH;
    int[] symbolRows;
    int symbolK;
    int symbolGap;
    int symbolSide;
    int symbolCols;
    int symbolSlots;

    // 横屏
    int outerW;
    int letterX;
    int letterW;
    int midX;
    int midW;
    int rightX;
    int outerRightX;
    int symbolX;
    int symbolCellW;
    int symbolW;
    int[] rowY;
    int p;
    int landRowKeys;
    int landPanelH;
    int panelX;
    int panelW;
    int panelH;
    int panelCellW;
    int panelCols;
    int panelRows;
    int panelCellH;
    int[] panelRowY;
    int spaceX;
    int spaceY;
    int spaceH;
    int expX;
    int expY;
    int expW;
    int expCellH;
    int[] expRowY;

    // 拼音浮层
    int preeditH;
    int preeditPad;
    int preeditGap;
    int preeditFont;
    int preeditLeft;
    int preeditWidth;

    // 诊断
    boolean tBeyond;
    boolean gCappedByHeight;
    boolean overflow;
    double kbRatio;

    KbdLayout(int width, int height, int b) {
        this(width, height, b, null);
    }

    /** forceLandscape 仅供宿主预览/测试；正式运行传 null。 */
    KbdLayout(int width, int height, int b, Boolean forceLandscape) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.b = Math.max(0, b);

        boolean pOK = this.width >= 160 && this.height >= 208 + this.b;
        boolean lOK = this.width >= 256 && this.height >= 144;
        if (forceLandscape != null) {
            landscape = forceLandscape;
        } else if (!pOK && !lOK) {
            landscape = this.width >= this.height;
        } else if (pOK && !lOK) {
            landscape = false;
        } else if (lOK && !pOK) {
            landscape = true;
        } else {
            landscape = this.width / (double) this.height > R;
        }

        if (landscape) {
            solveLandscape(this.width, this.height);
        } else {
            solvePortrait(this.width, this.height, this.b);
        }
        totalH = kbH;
        kbRatio = kbH > 0 ? kbW / (double) kbH : 0;
    }

    // ---------- 级 ----------

    static int tStart(int n) {
        return n == 1 ? 0 : 17 * n - 25;
    }

    static int tCap(int n) {
        return 17 * n - 8;
    }

    static int gStart(int n) {
        return n == 1 ? 0 : 2 * (n - 1);
    }

    static int gCap(int n) {
        return 2 * n;
    }

    private int portraitMaxN(int width, int height, int b) {
        int n = Math.max(1, (width + 359) / 360);
        while (n > 1) {
            if (208 * n + 12 * gStart(n) + b <= height) {
                break;
            }
            n--;
        }
        return n;
    }

    private int landscapeMaxN(int width, int height) {
        int n = Math.max(1, (Math.min(width, height) + 359) / 360);
        while (n > 1) {
            int kStart = 16 + 2 * tStart(n);
            if (width >= 16 * kStart && 144 * n + 8 * gStart(n) <= height) {
                break;
            }
            n--;
        }
        return n;
    }

    // ---------- 竖屏 ----------

    private void solvePortrait(int W, int H, int b) {
        n = portraitMaxN(W, H, b);
        S = 16 * n;
        int gs = gStart(n);
        int gc = gCap(n);
        int gHeight = (H - b - 208 * n) / 12;
        int gMax = Math.min(gc, Math.max(gs, gHeight));
        int ts = tStart(n);
        int tEnd = tCap(n);

        g = gs;
        for (int gg = gMax; gg >= gs; gg--) {
            if (160 + 9 * gg + 20 * ts <= W) {
                g = gg;
                break;
            }
        }
        t = ts;
        int minBase = 160 + 9 * g + 20 * ts;
        if (W >= minBase) {
            t = ts + (W - minBase) / 20;
        }
        int base = 160 + 9 * g + 20 * t;
        e = (W - base) & 1;
        m = Math.max(0, (W - base - e) / 2);
        c = g + e;
        k = 16 + 2 * t;
        tBeyond = t > tEnd;
        gCappedByHeight = gMax < gc;
        overflow = 208 * n + 12 * g + b > H || W < 160;

        side = m;
        keyH = 3 * S + 2 * g;
        mainH = 3 * keyH + 2 * g;
        barH = S;
        barTop = 0;
        mainTop = barH + g;
        funcTop = mainTop + mainH + g;
        kbW = W;
        kbLeft = 0;
        kbH = funcTop + keyH + b;
        doubleW = 2 * k + g;
        spaceW = 2 * k + c;

        candCellW = 2 * k + g;
        candGap = g;
        candSide = m;
        candH = S;
        expTop = mainTop;
        expH = mainH;
        expRows = Math.max(1, (mainH + g) / (S + g));
        expCols = Math.max(1, (W - 2 * m + g) / (candCellW + g));
        expCellW = candCellW;
        expGap = g;
        expSide = m;
        expRowH = S;

        symbolCols = PORTRAIT_ROW_KEYS;
        symbolSlots = symbolCols * 3;
        symbolK = k;
        symbolGap = g;
        symbolSide = m;
        symbolRows = new int[]{
                mainTop,
                mainTop + keyH + g,
                mainTop + 2 * (keyH + g)
        };

        preeditH = S;
        preeditPad = Math.max(1, Math.round(S / 16f));
        preeditGap = g;
        preeditFont = S;
        preeditLeft = 0;
        preeditWidth = W;
    }

    // ---------- 横屏 ----------

    private void solveLandscape(int W, int H) {
        n = landscapeMaxN(W, H);
        S = 16 * n;
        int gs = gStart(n);
        int gc = gCap(n);
        int gHeight = (H - 144 * n) / 8;
        int gMax = Math.min(gc, Math.max(gs, gHeight));
        int ts = tStart(n);
        int tEnd = tCap(n);

        g = gs;
        for (int gg = gMax; gg >= gs; gg--) {
            if (16 * (16 + 2 * ts) + 15 * gg <= W) {
                g = gg;
                break;
            }
        }

        k = 16 + 2 * ts;
        int maxK = (W - 15 * g) / 16;
        t = ts;
        if (maxK > k) {
            t = ts + (maxK - k) / 2;
        }
        if (t > tEnd) {
            t = tEnd;
        }
        k = 16 + 2 * t;

        p = 2;
        for (int pp = 6; pp >= 2; pp--) {
            if ((14 + pp) * k + (pp + 13) * g <= W) {
                p = pp;
                break;
            }
        }
        int base = (14 + p) * k + (p + 13) * g;
        e = (W - base) & 1;
        m = Math.max(0, (W - base - e) / 2);
        c = g + e;
        tBeyond = t > tEnd;
        gCappedByHeight = gMax < gc;
        overflow = 144 * n + 8 * g > H || W < 256;

        side = m;
        keyH = 3 * S + 2 * g;
        rowY = new int[]{0, keyH + g, 2 * (keyH + g)};
        kbH = 3 * keyH + 2 * g;
        kbW = W - 2 * m;
        kbLeft = m;
        outerW = 2 * k + g;
        doubleW = outerW;
        letterW = 5 * k + 4 * g;
        midW = p * k + (p - 1) * g + e;
        // 对外坐标一律是视图绝对值，包含左右安全区 m。
        int lx = outerW + g;
        int mx = lx + letterW + g;
        int rx = mx + midW + g;
        int orx = rx + letterW + g;
        letterX = kbLeft + lx;
        midX = kbLeft + mx;
        rightX = kbLeft + rx;
        outerRightX = kbLeft + orx;
        spaceW = midW;

        landRowKeys = p;
        landPanelH = 2 * keyH + g;
        panelX = midX;
        panelW = midW;
        panelH = landPanelH;
        panelCellW = Math.max(1, (midW - g) / 2);
        panelCols = 2;
        panelRows = Math.max(1, (panelH + g) / (S + g));
        panelCellH = S;
        panelRowY = new int[panelRows];
        for (int i = 0; i < panelRows; i++) {
            panelRowY[i] = i * (S + g);
        }

        expX = midX;
        expY = 0;
        expW = midW;
        expH = kbH;
        expCols = 2;
        expCellW = panelCellW;
        expCellH = S;
        expRows = Math.max(1, (kbH + g) / (S + g));
        expRowY = new int[expRows];
        for (int i = 0; i < expRows; i++) {
            expRowY[i] = i * (S + g);
        }

        spaceX = midX;
        spaceY = rowY[2];
        spaceH = keyH;

        symbolX = letterX;
        symbolCellW = k;
        symbolCols = Math.max(1, (kbW - 2 * outerW) / (k + g));
        symbolW = symbolCols * k + (symbolCols - 1) * g;
        symbolSlots = symbolCols * 3;

        preeditH = S;
        preeditPad = Math.max(1, Math.round(S / 16f));
        preeditGap = g;
        preeditFont = S;
        preeditLeft = midX;
        preeditWidth = midW;
    }

    // ---------- 竖屏矩形 ----------

    float[] keyRect(int row, int col) {
        float x = side + col * (k + g) + (col >= 5 ? (c - g) : 0);
        float y = mainTop + row * (keyH + g);
        return new float[]{x, y, x + k, y + keyH};
    }

    float[] symbolRect(int row, int col) {
        return keyRect(row, col);
    }

    float funcWidth(int index) {
        String[] ids = KbdData.PORTRAIT_ROWS[3];
        if (index < 0 || index >= ids.length) {
            return 0;
        }
        if ("space".equals(ids[index])) {
            return spaceW;
        }
        KbdData.Key key = KbdData.KEYS.get(ids[index]);
        return key != null && key.wide ? doubleW : k;
    }

    float funcX(int index) {
        float x = side;
        for (int i = 0; i < index; i++) {
            x += funcWidth(i) + g;
        }
        return x;
    }

    float[] funcRect(int index) {
        float x = funcX(index);
        return new float[]{x, funcTop, x + funcWidth(index), funcTop + keyH};
    }

    // ---------- 横屏矩形 ----------

    private float[] landRect(float x, float w, int row) {
        float y = rowY[row];
        return new float[]{x, y, x + w, y + keyH};
    }

    float[] landOuterLeftRect(int row) {
        return landRect(kbLeft, outerW, row);
    }

    float[] landOuterRightRect(int row) {
        return landRect(outerRightX, outerW, row);
    }

    float[] landLeftRect(int row, int col) {
        return landRect(letterX + col * (k + g), k, row);
    }

    float[] landRightRect(int row, int col) {
        return landRect(rightX + col * (k + g), k, row);
    }

    float[] landSymbolRect(int row, int col) {
        return landRect(symbolX + col * (k + g), k, row);
    }

    float[] landSpaceRect() {
        return landRect(spaceX, spaceW, 2);
    }

    static boolean hit(float[] r, float x, float y) {
        return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    // ---------- 候选 ----------

    int candCellBase(int keys) {
        return keys * k + (keys - 1) * g;
    }

    /** Unifont 确定性字宽：全角 size，半角 size/2。 */
    static int textWidth(String text, int size) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += fullWidth(text.charAt(i)) ? size : size / 2;
        }
        return width;
    }

    int textWidth(String text) {
        return textWidth(text, S);
    }

    static boolean fullWidth(char c) {
        return c >= 0x1100 && (c <= 0x115F || c == 0x2329 || c == 0x232A
                || (c >= 0x2E80 && c <= 0xA4CF && c != 0x303F)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6));
    }

    int candKeys(String text) {
        return candKeys(text, PORTRAIT_ROW_KEYS);
    }

    int candKeys(String text, int rowKeys) {
        int width = textWidth(text) + CAND_TEXT_MARGIN;
        for (int keys = CAND_MIN_KEYS; keys <= rowKeys; keys += 2) {
            if (width <= candCellBase(keys)) {
                return keys;
            }
        }
        return rowKeys;
    }

    String[] candLines(String text) {
        return candLines(text, PORTRAIT_ROW_KEYS);
    }

    String[] candLines(String text, int rowKeys) {
        int max = candCellBase(rowKeys) - CAND_TEXT_MARGIN;
        if (textWidth(text) <= max) {
            return new String[]{text};
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int cw = fullWidth(ch) ? S : S / 2;
            if (width + cw > max && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                width = 0;
            }
            line.append(ch);
            width += cw;
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.toArray(new String[0]);
    }

    static final class CandItem {
        final int index;
        final int keys;

        CandItem(int index, int keys) {
            this.index = index;
            this.keys = keys;
        }
    }

    static final class CandRow {
        final List<CandItem> items = new ArrayList<>();
        int keys;
        int slots;
    }

    List<CandRow> candidateRows(List<String> candidates) {
        return candidateRows(candidates, PORTRAIT_ROW_KEYS);
    }

    List<CandRow> candidateRows(List<String> candidates, int rowKeys) {
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

    int candRowBaseHeight(int slots) {
        return slots * S + (slots - 1) * g;
    }

    int candScrollMax(List<CandRow> rows) {
        return candScrollMax(rows, expH);
    }

    int candScrollMax(List<CandRow> rows, int panelH) {
        if (rows.isEmpty()) {
            return 0;
        }
        return Math.max(0, rows.size() - 1 - rowsFitting(rows, panelH));
    }

    int candPanelScrollMax(List<CandRow> rows, int panelH) {
        if (rows.isEmpty()) {
            return 0;
        }
        return Math.max(0, rows.size() - rowsFitting(rows, panelH));
    }

    private int rowsFitting(List<CandRow> rows, int panelH) {
        int used = 0;
        int count = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            int need = candRowBaseHeight(rows.get(i).slots) + (count > 0 ? g : 0);
            if (used + need > panelH) {
                break;
            }
            used += need;
            count++;
        }
        return count;
    }

    int candCellWidth(int keys) {
        return candCellBase(keys);
    }

    int candRowHeight(int slots) {
        return candRowBaseHeight(slots);
    }

    float[][] candRowRects(CandRow row, float top, float height) {
        return candRowRects(row, candSide, top, height);
    }

    float[][] candRowRects(CandRow row, float left, float top, float height) {
        float[][] rects = new float[row.items.size()][];
        float x = left;
        for (int i = 0; i < row.items.size(); i++) {
            float w = candCellWidth(row.items.get(i).keys);
            rects[i] = new float[]{x, top, x + w, top + height};
            x += w + g;
        }
        return rects;
    }
}
