package com.kbd.ime;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import javax.imageio.ImageIO;

/**
 * 宿主端渲染 + 数值自检。用与 Android 端完全相同的 KbdLayout / KbdRenderer / KbdApp。
 * 用法：sh scripts/preview.sh
 */
public final class LayoutPreview {

    private static Font baseFont;

    static final class Sink implements KbdRenderer.Sink {
        private final Graphics2D g;

        Sink(Graphics2D g) {
            this.g = g;
        }

        @Override
        public void fillRect(float l, float t, float r, float b, int argb) {
            g.setColor(new Color(argb, true));
            g.fillRect((int) l, (int) t,
                    (int) Math.ceil(r - l), (int) Math.ceil(b - t));
        }

        @Override
        public void text(String text, float cx, float cy, float size, int argb,
                         boolean centered, float maxWidth) {
            if (text == null || text.isEmpty()) {
                return;
            }
            Font f = baseFont.deriveFont(size);
            g.setFont(f);
            g.setColor(new Color(argb, true));
            String label = ellipsize(text, f, maxWidth);
            FontMetrics fm = g.getFontMetrics(f);
            int x = centered ? (int) (cx - fm.stringWidth(label) / 2f) : (int) cx;
            int baseline = (int) (cy + (fm.getAscent() - fm.getDescent()) / 2f);
            g.drawString(label, x, baseline);
        }

        private String ellipsize(String text, Font f, float maxWidth) {
            FontMetrics fm = g.getFontMetrics(f);
            if (maxWidth <= 0 || fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            String ellipsis = "…";
            int avail = (int) (maxWidth - fm.stringWidth(ellipsis));
            int end = text.length();
            while (end > 0 && fm.stringWidth(text.substring(0, end)) > avail) {
                end--;
            }
            return text.substring(0, end) + ellipsis;
        }
    }

    static final class Host implements KbdApp.Host {
        final StringBuilder committed = new StringBuilder();
        final java.util.List<Integer> keys = new ArrayList<>();
        String composing = "";
        boolean hidden;
        int invalidations;

        public void commitText(String text) {
            committed.append(text);
        }

        public void setComposing(String text) {
            composing = text;
        }

        public void finishComposing() {
            composing = "";
        }

        public void sendKey(int keyCode, int metaState) {
            keys.add(keyCode);
        }

        public String toTraditional(String text) {
            return text;
        }

        public void onSettingsChanged(int theme, int variant) {
        }

        public void onSymbolRowChanged(int row) {
        }

        public void onBottomSafeChanged(int b) {
        }

        public void onSchemeChanged(String scheme) {
        }

        public void onLayoutChanged() {
        }

        public void hideKeyboard() {
            hidden = true;
        }

        public void invalidate() {
            invalidations++;
        }
    }

    static final class Timer implements KbdGestures.Timer {
        public void armLongPress() {
        }

        public void armRepeat() {
        }

        public void cancel() {
        }
    }

    static final int APP_LIGHT = 0xFFF7F7F7;
    static final int APP_DARK = 0xFF000000;
    static final int INPUT_LIGHT = 0xFFFFFFFF;
    static final int INPUT_DARK = 0xFF2B2B2B;
    static final int PREEDIT_BG_LIGHT = 0xFFF7F7F7;
    static final int PREEDIT_BG_DARK = 0xFF000000;
    static final int PREEDIT_INK_LIGHT = 0xFF111111;
    static final int PREEDIT_INK_DARK = 0xFFFFFFFF;

    /** 键盘上方模拟应用栏：输入框保持空；拼音浮层在键盘外（webdemo .floating-preedit）。 */
    private static final int MOCK_APP_H = 56;

    private static void render(String name, int width, boolean landscape, KbdApp app,
                               KbdRenderer.Pressed pressed, String outDir, boolean dark)
            throws Exception {
        render(name, width, landscape ? 720 : 1604, landscape, app, pressed, outDir, dark);
    }

    private static void render(String name, int width, int height, boolean landscape, KbdApp app,
                               KbdRenderer.Pressed pressed, String outDir, boolean dark)
            throws Exception {
        KbdLayout L = new KbdLayout(width, height, 32, landscape);
        app.setLayout(L);
        app.setLandscape(landscape);
        int imageH = MOCK_APP_H + L.totalH;
        BufferedImage image = new BufferedImage(width, imageH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // 应用区域 + 输入框（横屏对齐中列）
        g.setColor(new Color(dark ? APP_DARK : APP_LIGHT));
        g.fillRect(0, 0, width, MOCK_APP_H);
        g.setColor(new Color(dark ? INPUT_DARK : INPUT_LIGHT));
        if (landscape) {
            g.fillRect(L.midX, 12, L.midW, 32);
        } else {
            g.fillRect(8, 12, width - 16, 32);
        }

        String composing = app.escape ? app.escapeBuffer
                : (app.composing() ? app.displayPreedit() : "");
        if (!composing.isEmpty()) {
            Font pf = baseFont.deriveFont((float) L.preeditFont);
            FontMetrics pfm = g.getFontMetrics(pf);
            int textW = pfm.stringWidth(composing);
            int chipW = Math.min(L.preeditWidth, textW + 2 * L.preeditPad);
            int chipX = L.preeditLeft;
            int chipY = MOCK_APP_H - L.preeditGap - L.preeditH;
            g.setColor(new Color(dark ? PREEDIT_BG_DARK : PREEDIT_BG_LIGHT));
            g.fillRect(chipX, chipY, chipW, L.preeditH);
            g.fillRect(chipX, chipY + L.preeditH, chipW, L.preeditGap);  // 补满 4px 间隔
            g.setFont(pf);
            g.setColor(new Color(dark ? PREEDIT_INK_DARK : PREEDIT_INK_LIGHT));
            String label = textW <= chipW - 2 * L.preeditPad ? composing : ellipsizePreedit(
                    composing, pfm, chipW - 2 * L.preeditPad);
            int baseline = chipY + (L.preeditH + pfm.getAscent() - pfm.getDescent()) / 2;
            g.drawString(label, chipX + L.preeditPad, baseline);
        }

        Graphics2D kg = (Graphics2D) g.create();
        kg.translate(0, MOCK_APP_H);
        KbdRenderer renderer = new KbdRenderer();
        renderer.setDark(dark);
        renderer.render(new Sink(kg), L, app, pressed);
        kg.dispose();
        g.dispose();

        File out = new File(outDir, name + ".png");
        ImageIO.write(image, "png", out);
        System.out.println("wrote " + out + "  " + width + "x" + imageH);
    }

    private static String ellipsizePreedit(String text, FontMetrics fm, int maxWidth) {
        int avail = maxWidth - fm.stringWidth("…");
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) > avail) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    private static boolean check(String label, boolean ok, String detail) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + label
                + (detail.isEmpty() ? "" : "  " + detail));
        return ok;
    }

    private static boolean checks() {
        KbdLayout L = new KbdLayout(720, 1604, 32);
        boolean ok = true;

        ok &= check("键盘高度 496，无拼音带",
                L.barTop == 0 && L.kbH == 496 && L.totalH == 496,
                "barTop=" + L.barTop + " kbH=" + L.kbH + " total=" + L.totalH);
        ok &= check("拼音浮层 32 高 / 内边距 2 / 间隔 4 / 字号 32",
                L.preeditH == 32 && L.preeditPad == 2
                        && L.preeditGap == 4 && L.preeditFont == 32,
                "h=" + L.preeditH + " pad=" + L.preeditPad
                        + " gap=" + L.preeditGap + " font=" + L.preeditFont);
        ok &= check("主键区高度 320", L.mainH == 320, "mainH=" + L.mainH);
        ok &= check("键高+间隔 = 108", L.keyH + L.g == 108,
                "pitch=" + (L.keyH + L.g));
        ok &= check("功能行右边界 718（左右各 2px）",
                Math.round(L.funcRect(6)[2]) == 718, "right=" + Math.round(L.funcRect(6)[2]));
        ok &= check("候选最小 2 键宽 = 140 / 最大 10 键宽 = 716",
                Math.round(L.candCellWidth(2)) == 140 && Math.round(L.candCellWidth(10)) == 716,
                "2键=" + Math.round(L.candCellWidth(2))
                        + " 10键=" + Math.round(L.candCellWidth(10)));
        ok &= check("候选格宽为偶数键宽：4 键 = 284 / 6 键 = 428",
                Math.round(L.candCellWidth(4)) == 284 && Math.round(L.candCellWidth(6)) == 428,
                "4键=" + Math.round(L.candCellWidth(4))
                        + " 6键=" + Math.round(L.candCellWidth(6)));
        ok &= check("10 键一行右边界 718",
                L.candSide + Math.round(L.candCellWidth(10)) == 718,
                "right=" + (L.candSide + Math.round(L.candCellWidth(10))));
        ok &= check("中华人民共和国 = 4 键",
                L.candKeys("中华人民共和国") == 4,
                "keys=" + L.candKeys("中华人民共和国"));

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        Font f = baseFont.deriveFont(32f);
        pg.setFont(f);
        FontMetrics fm = pg.getFontMetrics(f);
        ok &= check("Unifont 行高 ≤ 32", fm.getAscent() + fm.getDescent() <= 32,
                "lineHeight=" + (fm.getAscent() + fm.getDescent()));
        ok &= check("Shft 宽 ≤ 68", fm.stringWidth("Shft") <= 68,
                "width=" + fm.stringWidth("Shft"));
        ok &= check("汉字宽 = 2 × ASCII", fm.stringWidth("好") == 2 * fm.stringWidth("a"),
                fm.stringWidth("好") + " vs " + fm.stringWidth("a"));
        ok &= check("确定性字宽 = Unifont 实测",
                KbdLayout.textWidth("中华人民共和国", 32) == fm.stringWidth("中华人民共和国")
                        && KbdLayout.textWidth("a你，", 32) == fm.stringWidth("a你，"),
                KbdLayout.textWidth("a你，", 32) + " vs " + fm.stringWidth("a你，"));
        pg.dispose();

        java.util.List<String> eighty = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            eighty.add("候");
        }
        java.util.List<KbdLayout.CandRow> rows = L.candidateRows(eighty);
        ok &= check("5 个 2 键宽装满 10 键 → 80 个候选 = 16 行", rows.size() == 16,
                "rows=" + rows.size());
        ok &= check("面板可容纳 9 行 → 滚动上限 6",
                L.candScrollMax(rows) == 6, "max=" + L.candScrollMax(rows));

        ok &= check("符号行数 43", (KbdData.SYMBOLS.size() + 9) / 10 == 43,
                "rows=" + ((KbdData.SYMBOLS.size() + 9) / 10));

        // 自适应落点
        KbdLayout minP = new KbdLayout(160, 256, 32);
        ok &= check("最小竖屏 160×240",
                !minP.landscape && minP.kbH == 240
                        && minP.S == 16 && minP.k == 16 && minP.g == 0,
                "kb=" + minP.kbW + "x" + minP.kbH + " S=" + minP.S + " k=" + minP.k);
        KbdLayout minL = new KbdLayout(256, 160, 32);
        ok &= check("最小横屏 256×144",
                minL.landscape && minL.kbH == 144 && minL.k == 16,
                "kb=" + minL.kbW + "x" + minL.kbH + " k=" + minL.k);
        KbdLayout threeFour = new KbdLayout(768, 1024, 32);
        ok &= check("3:4 竖屏 768×728",
                !threeFour.landscape && threeFour.kbH == 728
                        && threeFour.k == 70 && threeFour.c == 6,
                "kb=" + threeFour.kbW + "x" + threeFour.kbH
                        + " k=" + threeFour.k + " c=" + threeFour.c);
        KbdLayout square = new KbdLayout(800, 800, 32);
        ok &= check("1:1 横屏 796×320",
                square.landscape && square.kbW == 796 && square.kbH == 320
                        && square.k == 46,
                "kb=" + square.kbW + "x" + square.kbH + " k=" + square.k);
        KbdLayout tall = new KbdLayout(1080, 2400, 32);
        ok &= check("1080×2400 竖屏 1080×728",
                !tall.landscape && tall.kbH == 728 && tall.k == 102,
                "kb=" + tall.kbW + "x" + tall.kbH + " k=" + tall.k);
        ok &= check("修饰键短标签阈值",
                KbdLayout.textWidth("Ctrl", 32) <= 68 - 2
                        && KbdLayout.textWidth("Ctrl", 48) >= 70 - 2,
                "32px=" + KbdLayout.textWidth("Ctrl", 32)
                        + " 48px=" + KbdLayout.textWidth("Ctrl", 48));
        ok &= check("Tab/Esc 短标签阈值",
                KbdLayout.textWidth("Tab", 48) > 70 - 6
                        && KbdLayout.textWidth("Tab", 32) <= 68 - 4,
                "48px=" + KbdLayout.textWidth("Tab", 48)
                        + " 32px=" + KbdLayout.textWidth("Tab", 32));
        ok &= check("短标签统一：720 全称 / 768 全短 / 800 横屏全短 / 1024 横屏全称",
                !L.compactLabels()
                        && threeFour.compactLabels()
                        && square.compactLabels()
                        && !new KbdLayout(1024, 768, 32).compactLabels(),
                "720=" + L.compactLabels()
                        + " 768=" + threeFour.compactLabels()
                        + " 800=" + square.compactLabels()
                        + " 1024=" + new KbdLayout(1024, 768, 32).compactLabels());

        // 横屏（webdemo landscape）
        KbdLayout LL = new KbdLayout(1604, 720, 32);
        ok &= check("横屏键盘高 320", LL.kbH == 320 && LL.totalH == 320,
                "kbH=" + LL.kbH);
        ok &= check("横屏左外列 x=84 宽 140",
                Math.round(LL.landOuterLeftRect(0)[0]) == 84
                        && Math.round(LL.landOuterLeftRect(0)[2]) == 224,
                "rect=" + Math.round(LL.landOuterLeftRect(0)[0]) + ".."
                        + Math.round(LL.landOuterLeftRect(0)[2]));
        ok &= check("横屏字母簇 x=228 宽 356；右簇 x=1020 宽 356",
                Math.round(LL.landLeftRect(0, 0)[0]) == 228
                        && Math.round(LL.landRightRect(0, 4)[2]) == 1376,
                "left=" + Math.round(LL.landLeftRect(0, 0)[0])
                        + " rightEnd=" + Math.round(LL.landRightRect(0, 4)[2]));
        ok &= check("横屏中列 x=588 宽 428",
                LL.midX == 588 && LL.midW == 428, "midX=" + LL.midX + " midW=" + LL.midW);
        ok &= check("横屏右外列 x=1380 宽 140，右侧余量 84",
                Math.round(LL.landOuterRightRect(0)[0]) == 1380
                        && 1604 - Math.round(LL.landOuterRightRect(0)[2]) == 84,
                "x=" + Math.round(LL.landOuterRightRect(0)[0]));
        ok &= check("横屏符号 16 列，区宽 1148",
                LL.symbolCols == 16
                        && Math.round(LL.landSymbolRect(0, 15)[2]) - LL.symbolX == 1148,
                "cols=" + LL.symbolCols);
        ok &= check("横屏候选最小 2 键 140 / 最大 6 键 428",
                LL.candCellBase(2) == 140 && LL.candCellBase(6) == 428,
                "2键=" + LL.candCellBase(2) + " 6键=" + LL.candCellBase(6));
        ok &= check("横屏 中华人民共和国 = 4 键（行宽 6 键）",
                LL.candKeys("中华人民共和国", LL.landRowKeys) == 4,
                "keys=" + LL.candKeys("中华人民共和国", LL.landRowKeys));
        java.util.List<String> pack = Arrays.asList("中华人民共和国", "你好");
        java.util.List<KbdLayout.CandRow> landRows =
                LL.candidateRows(pack, LL.landRowKeys);
        ok &= check("横屏装箱：4+2 = 一行 6 键、2 格",
                landRows.size() == 1 && landRows.get(0).keys == 6
                        && landRows.get(0).items.size() == 2,
                "rows=" + landRows.size());
        String landUltra = "中华人民共和国万岁中华人民共和国万岁中华人民共和国万岁";
        ok &= check("横屏超长跨行：6 键行内折 3 行",
                LL.candKeys(landUltra, LL.landRowKeys) == 6
                        && LL.candLines(landUltra, LL.landRowKeys).length == 3,
                "lines=" + LL.candLines(landUltra, LL.landRowKeys).length);
        ok &= check("横屏展开 9 行正好 320",
                LL.candRowBaseHeight(1) * 9 + 8 * LL.g == 320,
                "height=" + (LL.candRowBaseHeight(1) * 9 + 8 * LL.g));
        ok &= check("横屏空格 428×104 贴底",
                Math.round(LL.landSpaceRect()[2] - LL.landSpaceRect()[0]) == 428
                        && Math.round(LL.landSpaceRect()[3]) == 320,
                "space=" + Math.round(LL.landSpaceRect()[1]) + ".."
                        + Math.round(LL.landSpaceRect()[3]));
        ok &= check("横屏拼音浮层 q→p x=228 宽 1148",
                LL.preeditLeft == 228 && LL.preeditWidth == 1148,
                "x=" + LL.preeditLeft + " w=" + LL.preeditWidth);
        return ok;
    }

    private static KbdApp composingApp(String preedit) {
        KbdApp app = new KbdApp(new Host());
        app.status = "";
        app.preedit = preedit;
        app.candidates = new ArrayList<>(Arrays.asList(
                "中华人民共和国",
                "你好", "拟好", "你", "拟", "泥", "逆", "腻", "呢", "妮", "倪",
                "你号", "妮豪", "你好啊", "溺好", "拟号", "妮好", "你浩", "泥好", "尼好", "逆号",
                "呢好", "倪好", "腻好", "匿好", "你郝", "拟郝", "泥郝", "你耗", "尼耗", "溺耗"));
        for (int i = app.candidates.size(); i < 80; i++) {
            app.candidates.add("候" + i);
        }
        return app;
    }

    public static void main(String[] args) throws Exception {
        baseFont = Font.createFont(Font.TRUETYPE_FONT, new File(args[0]));
        String outDir = args[1];
        boolean ok = checks();

        KbdApp idle = new KbdApp(new Host());
        idle.status = "";
        render("00-idle", 720, false, idle, new KbdRenderer.Pressed(), outDir, true);
        render("00b-idle-light", 720, false, idle, new KbdRenderer.Pressed(), outDir, false);

        KbdApp letters = composingApp("ni hc");
        render("01-letters", 720, false, letters, new KbdRenderer.Pressed(), outDir, true);
        render("01b-letters-light", 720, false, letters, new KbdRenderer.Pressed(), outDir, false);

        KbdApp symbols = new KbdApp(new Host());
        symbols.status = "";
        symbols.symbols = true;
        render("02-symbols", 720, false, symbols, new KbdRenderer.Pressed(), outDir, true);
        symbols.scrollSymbols(3);
        render("02b-symbols-scrolled", 720, false, symbols, new KbdRenderer.Pressed(), outDir, true);

        KbdApp expanded = composingApp("ni hc");
        expanded.expanded = true;
        render("03-expanded", 720, false, expanded, new KbdRenderer.Pressed(), outDir, true);
        expanded.scrollCandidates(3);
        render("03b-expanded-scrolled", 720, false, expanded, new KbdRenderer.Pressed(), outDir, true);

        KbdApp english = new KbdApp(new Host());
        english.status = "";
        english.chinese = false;
        english.mods.get("shift").active = true;
        render("04-english-shift", 720, false, english, new KbdRenderer.Pressed(), outDir, true);

        KbdApp escape = new KbdApp(new Host());
        escape.status = "";
        escape.escape = true;
        escape.escapeBuffer = "\\u4f60";
        render("05-escape", 720, false, escape, new KbdRenderer.Pressed(), outDir, true);

        KbdApp slide = composingApp("ni hc");
        KbdRenderer.Pressed pressed = new KbdRenderer.Pressed();
        pressed.keyId = "q";
        pressed.mode = KbdRenderer.MODE_SLIDE_UP;
        pressed.pressed = true;
        render("06-slide-up", 720, false, slide, pressed, outDir, true);

        // 超一整行宽的候选：展开面板里折成 2 行、占 2 行高
        KbdApp ultra = new KbdApp(new Host());
        ultra.status = "";
        ultra.preedit = "ni hc";
        ultra.candidates = new ArrayList<>(Arrays.asList(
                "你好",
                "中华人民共和国万岁中华人民共和国万岁中华人民共和国万岁",
                "拟好", "你", "拟"));
        ultra.expanded = true;
        render("07-ultra", 720, false, ultra, new KbdRenderer.Pressed(), outDir, true);

        // 横屏
        KbdApp landLetters = composingApp("ni hc");
        render("10-landscape-letters", 1604, true, landLetters,
                new KbdRenderer.Pressed(), outDir, true);
        KbdApp landSymbols = new KbdApp(new Host());
        landSymbols.status = "";
        landSymbols.symbols = true;
        render("10b-landscape-symbols", 1604, true, landSymbols,
                new KbdRenderer.Pressed(), outDir, true);
        KbdApp landExpanded = composingApp("ni hc");
        landExpanded.expanded = true;
        render("10c-landscape-expanded", 1604, true, landExpanded,
                new KbdRenderer.Pressed(), outDir, true);
        landExpanded.scrollCandidates(2);
        render("10d-landscape-expanded-scrolled", 1604, true, landExpanded,
                new KbdRenderer.Pressed(), outDir, true);

        // 自适应尺寸
        render("20-adaptive-768x1024", 768, 1024, false, composingApp("ni hc"),
                new KbdRenderer.Pressed(), outDir, true);
        render("21-adaptive-800x800", 800, 800, true, composingApp("ni hc"),
                new KbdRenderer.Pressed(), outDir, true);
        render("22-adaptive-300x260", 300, 260, true, composingApp("ni hc"),
                new KbdRenderer.Pressed(), outDir, true);
        KbdApp minApp = new KbdApp(new Host());
        minApp.status = "";
        render("23-adaptive-160x256", 160, 256, false, minApp,
                new KbdRenderer.Pressed(), outDir, true);

        System.out.println(ok ? "自检通过" : "自检失败");
        if (!ok) {
            System.exit(1);
        }
    }
}
