package com.kbd.ime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 动作真值表测试（对应 docs/kbd-parity.md 第 2 节）。
 * 纯 Java，不依赖 Android、不依赖引擎/native。
 */
public final class ParityTests {

    static final class Host implements KbdApp.Host {
        final StringBuilder committed = new StringBuilder();
        final List<Integer> keys = new ArrayList<>();
        final List<Integer> metas = new ArrayList<>();
        String composing = "";
        int savedTheme = -1;
        int savedVariant = -1;
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
            metas.add(metaState);
        }

        public String toTraditional(String text) {
            return text;
        }

        public void onSettingsChanged(int theme, int variant) {
            savedTheme = theme;
            savedVariant = variant;
        }

        public void hideKeyboard() {
            hidden = true;
        }

        public void invalidate() {
            invalidations++;
        }
    }

    private static int passed;
    private static int failed;

    private static void check(String label, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL  " + label);
        }
    }

    private static KbdApp newApp(Host host) {
        KbdApp app = new KbdApp(host);
        app.status = "";
        return app;
    }

    private static KbdApp composingApp(Host host) {
        KbdApp app = newApp(host);
        app.preedit = "ni hc";
        app.candidates = new ArrayList<>(Arrays.asList("你好", "拟好", "你", "拟", "泥", "逆"));
        return app;
    }

    private static float[] funcRect(KbdLayout layout, String id) {
        String[] ids = KbdData.PORTRAIT_ROWS[3];
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(id)) {
                return layout.funcRect(i);
            }
        }
        throw new IllegalArgumentException(id);
    }

    // 手势层：长按触发后，松手不得再执行点按（webdemo input.js 的 long/repeat 态）
    private static void longPressRelease() {
        KbdLayout L = new KbdLayout(720, false);
        KbdGestures.Timer timer = new KbdGestures.Timer() {
            public void armLongPress() {
            }

            public void armRepeat() {
            }

            public void cancel() {
            }
        };

        Host h = new Host();
        KbdApp app = newApp(h);
        KbdGestures g = new KbdGestures(L, app, timer);
        float[] space = funcRect(L, "space");
        float sx = (space[0] + space[2]) / 2;
        float sy = (space[1] + space[3]) / 2;
        g.down(sx, sy);
        g.longPressDue();
        check("长按空格 → 进转义", app.escape);
        g.up(sx, sy);
        check("长按空格松手 → 仍在转义", app.escape && "\\".equals(app.escapeBuffer));

        Host h2 = new Host();
        KbdApp locked = newApp(h2);
        KbdGestures g2 = new KbdGestures(L, locked, timer);
        float[] sh = funcRect(L, "shift");
        float shx = (sh[0] + sh[2]) / 2;
        float shy = (sh[1] + sh[3]) / 2;
        g2.down(shx, shy);
        g2.longPressDue();
        check("长按 shift → 锁定", locked.mods.get("shift").locked);
        g2.up(shx, shy);
        check("长按 shift 松手 → 仍锁定", locked.mods.get("shift").locked
                && locked.mods.get("shift").active);

        Host h3 = new Host();
        KbdApp del = newApp(h3);
        KbdGestures g3 = new KbdGestures(L, del, timer);
        float[] bs = funcRect(L, "backspace");
        float bx = (bs[0] + bs[2]) / 2;
        float by = (bs[1] + bs[3]) / 2;
        g3.down(bx, by);
        g3.longPressDue();
        check("长按删除 → 先删一次", h3.keys.size() == 1);
        g3.up(bx, by);
        check("长按删除松手 → 不再追加", h3.keys.size() == 1);
    }

    // 候选格：最小 2 键宽，偶数倍键宽，超长跨行
    private static void candidateWidths() {
        check("候选最小 2 键宽 = 140", KbdLayout.candCellBase(2) == 140);
        check("候选最大 10 键宽 = 716", KbdLayout.candCellBase(10) == 716);
        check("4 键宽 = 284 / 6 键宽 = 428",
                KbdLayout.candCellBase(4) == 284 && KbdLayout.candCellBase(6) == 428);
        check("你 → 2 键", KbdLayout.candKeys("你") == 2);
        check("中华人民共和国 → 4 键", KbdLayout.candKeys("中华人民共和国") == 4);
        check("中华人民共和国成立 → 6 键",
                KbdLayout.candKeys("中华人民共和国成立") == 6);

        String veryLong = "中华人民共和国万岁中华人民共和国万岁中华人民共和国万岁";
        check("超一整行 → 10 键 + 折成 2 行",
                KbdLayout.candKeys(veryLong) == 10
                        && KbdLayout.candLines(veryLong).length == 2);

        List<String> pack = new ArrayList<>();
        pack.add("中华人民共和国");
        pack.add("你好");
        pack.add("拟好");
        pack.add("你");
        List<KbdLayout.CandRow> rows = KbdLayout.candidateRows(pack);
        check("装箱：4+2+2+2 = 一行 10 键、4 格",
                rows.size() == 1 && rows.get(0).keys == 10 && rows.get(0).items.size() == 4);
    }

    // ctrl/alt：发送带修饰态的按键（终端类应用）
    private static void modifierCombos() {
        Host h = new Host();
        KbdApp app = newApp(h);
        app.mods.get("ctrl").active = true;
        app.letter("c");
        check("Ctrl+C → KEYCODE_C + META_CTRL",
                h.keys.size() == 1 && h.keys.get(0) == 31
                        && h.metas.get(0) == KbdApp.META_CTRL);
        check("Ctrl+C 后清修饰", !app.mods.get("ctrl").active);

        Host h2 = new Host();
        KbdApp alt = newApp(h2);
        alt.mods.get("alt").active = true;
        alt.letter("f");
        check("Alt+F → KEYCODE_F + META_ALT",
                h2.keys.size() == 1 && h2.keys.get(0) == 34
                        && h2.metas.get(0) == KbdApp.META_ALT);

        Host h3 = new Host();
        KbdApp both = newApp(h3);
        both.mods.get("ctrl").active = true;
        both.mods.get("alt").active = true;
        both.letter("x");
        check("Ctrl+Alt+X → 双修饰",
                h3.keys.size() == 1 && h3.keys.get(0) == 52
                        && h3.metas.get(0) == (KbdApp.META_CTRL | KbdApp.META_ALT));

        Host h4 = new Host();
        KbdApp comp = composingApp(h4);
        comp.mods.get("ctrl").active = true;
        comp.letter("c");
        check("组词中 Ctrl+C → 仍拦截", h4.keys.isEmpty());

        Host h5 = new Host();
        KbdApp nav = newApp(h5);
        nav.mods.get("ctrl").active = true;
        nav.nav("left");
        check("Ctrl+左 → DPAD_LEFT + META_CTRL",
                h5.keys.size() == 1 && h5.keys.get(0) == KbdApp.KEY_DPAD_LEFT
                        && h5.metas.get(0) == KbdApp.META_CTRL);
    }

    // 标准键盘 ASCII 字符的组合键
    private static void asciiCombos() {
        Host h = new Host();
        KbdApp app = newApp(h);
        app.mods.get("ctrl").active = true;
        app.emitChar("1");
        check("Ctrl+1 → KEYCODE_1 + META_CTRL",
                h.keys.size() == 1 && h.keys.get(0) == 8
                        && h.metas.get(0) == KbdApp.META_CTRL);

        Host h2 = new Host();
        KbdApp both = newApp(h2);
        both.mods.get("ctrl").active = true;
        both.mods.get("alt").active = true;
        both.emitChar("[");
        check("Ctrl+Alt+[ → KEYCODE_LEFT_BRACKET",
                h2.keys.size() == 1 && h2.keys.get(0) == 71
                        && h2.metas.get(0) == (KbdApp.META_CTRL | KbdApp.META_ALT));

        Host h3 = new Host();
        KbdApp tab = newApp(h3);
        tab.mods.get("ctrl").active = true;
        tab.emitChar("Tab");
        check("Ctrl+Tab → KEYCODE_TAB",
                h3.keys.size() == 1 && h3.keys.get(0) == KbdApp.KEY_TAB
                        && h3.metas.get(0) == KbdApp.META_CTRL);

        Host h4 = new Host();
        KbdApp zh = newApp(h4);
        zh.mods.get("ctrl").active = true;
        zh.emitChar("！");
        check("中文 Ctrl+！ → KEYCODE_1 + CTRL|SHIFT",
                h4.keys.size() == 1 && h4.keys.get(0) == 8
                        && h4.metas.get(0) == (KbdApp.META_CTRL | KbdApp.META_SHIFT));

        Host h5 = new Host();
        KbdApp at = newApp(h5);
        at.mods.get("ctrl").active = true;
        at.emitChar("@");
        check("Ctrl+@ → KEYCODE_2 + CTRL|SHIFT",
                h5.keys.size() == 1 && h5.keys.get(0) == 9
                        && h5.metas.get(0) == (KbdApp.META_CTRL | KbdApp.META_SHIFT));

        Host h6 = new Host();
        KbdApp dots = newApp(h6);
        dots.mods.get("ctrl").active = true;
        dots.emitChar("……");
        check("…… 无物理键码 → 仍按文本插入",
                h6.keys.isEmpty() && "……".equals(h6.committed.toString()));
    }

    // 中文 f 键：引号循环
    private static void fQuotes() {
        Host h = new Host();
        KbdApp app = newApp(h);
        KbdData.Key f = KbdData.KEYS.get("f");
        String up = KbdRenderer.keyUpText(app, f);
        check("中文 f 上滑先出 “", "“".equals(up));
        app.emitChar(up);
        check("中文 f 上滑循环到 ”", "”".equals(KbdRenderer.keyUpText(app, f)));
        app.emitChar("”");
        check("再循环回 “", "“".equals(KbdRenderer.keyUpText(app, f)));
        check("双引号上屏 “”", "“”".equals(h.committed.toString()));

        String down = KbdRenderer.keyDownText(app, f);
        check("中文 f 下滑先出 ‘", "‘".equals(down));
        app.emitChar(down);
        check("中文 f 下滑循环到 ’", "’".equals(KbdRenderer.keyDownText(app, f)));

        KbdApp en = newApp(new Host());
        en.chinese = false;
        check("英文 f 上滑仍是 \"", "\"".equals(KbdRenderer.keyUpText(en, f)));
        check("英文 f 下滑仍是 '", "'".equals(KbdRenderer.keyDownText(en, f)));
    }

    // 横屏：6 键一行流式装箱（与竖屏同规则）
    private static void landscapeMode() {
        KbdApp app = newApp(new Host());
        app.candidates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            app.candidates.add("候" + i);
        }
        app.setLandscape(true);
        check("横屏符号每行 16 列", app.symbolCols() == 16);

        List<KbdLayout.CandRow> rows = KbdLayout.candidateRows(
                app.candidates, KbdLayout.LAND_ROW_KEYS);
        check("横屏 6 键一行 → 30 个候选 = 10 行", rows.size() == 10);
        check("横屏首行满 6 键、3 格",
                rows.get(0).keys == 6 && rows.get(0).items.size() == 3);
        check("横屏首格 = 第 0 个", rows.get(0).items.get(0).index == 0);

        app.expanded = true;
        app.scrollCandidates(100);
        check("横屏候选滚到末行", app.candidateRow == 1);
        check("横屏末行可见 = 第 29 个", rows.get(1 + 8).items.get(2).index == 29);
        app.scrollCandidates(-100);
        check("横屏候选回首行", app.candidateRow == 0);
    }

    // 横屏手势：字母簇与空格
    private static void landscapeGestures() {
        KbdLayout L = new KbdLayout(1604, true);

        Host h = new Host();
        KbdApp app = newApp(h);
        app.chinese = false;
        app.setLandscape(true);
        KbdGestures g = new KbdGestures(L, app, noTimer());
        float[] q = L.landLeftRect(0, 0);
        float qx = (q[0] + q[2]) / 2;
        float qy = (q[1] + q[3]) / 2;
        g.down(qx, qy);
        g.up(qx, qy);
        check("横屏点 q → 插入 q", "q".equals(h.committed.toString()));

        Host h2 = new Host();
        KbdApp app2 = newApp(h2);
        app2.chinese = false;
        app2.setLandscape(true);
        KbdGestures g2 = new KbdGestures(L, app2, noTimer());
        float[] space = L.landSpaceRect();
        float sx = (space[0] + space[2]) / 2;
        float sy = (space[1] + space[3]) / 2;
        g2.down(sx, sy);
        g2.up(sx, sy);
        check("横屏点空格 → 插入空格", " ".equals(h2.committed.toString()));
    }

    private static KbdGestures.Timer noTimer() {
        return new KbdGestures.Timer() {
            public void armLongPress() {
            }

            public void armRepeat() {
            }

            public void cancel() {
            }
        };
    }

    // 内部命令：\hp 面板 + sc/tc/dk/lt
    private static void commands() {
        Host h = new Host();
        KbdApp app = newApp(h);

        app.escape = true;
        app.escapeBuffer = "\\hp";
        app.executeEscape();
        check("\\hp → 命令面板", app.commands && !app.escape);
        check("面板 5 条 sc/tc/dk/lt/au", app.candidates.size() == 5);

        app.tapKey("enter");
        check("面板可展开", app.expanded);
        app.tapKey("enter");
        check("再按确认收起", !app.expanded);

        app.commitCandidate(2);
        check("点 dk → 暗皮肤", app.theme == KbdApp.THEME_DARK && !app.commands);
        check("dk 覆盖系统亮色", app.isDark(false));
        check("dk 已持久化", h.savedTheme == KbdApp.THEME_DARK
                && h.savedVariant == KbdApp.VARIANT_SIMPLE);
        app.escape = true;
        app.escapeBuffer = "\\lt";
        app.executeEscape();
        check("\\lt → 亮皮肤", app.theme == KbdApp.THEME_LIGHT && !app.isDark(true));
        app.escape = true;
        app.escapeBuffer = "\\tc";
        app.executeEscape();
        check("\\tc → 繁体", app.variant == KbdApp.VARIANT_TRAD);
        check("tc 已持久化", h.savedVariant == KbdApp.VARIANT_TRAD);
        app.escape = true;
        app.escapeBuffer = "\\sc";
        app.executeEscape();
        check("\\sc → 简体", app.variant == KbdApp.VARIANT_SIMPLE);
        app.escape = true;
        app.escapeBuffer = "\\au";
        app.executeEscape();
        check("\\au → 自动主题", app.theme == KbdApp.THEME_AUTO
                && h.savedTheme == KbdApp.THEME_AUTO);
        check("自动跟随系统", app.isDark(true) && !app.isDark(false));

        app.escape = true;
        app.escapeBuffer = "\\hp";
        app.executeEscape();
        app.commitCandidate(1);
        check("点 tc → 繁体", app.variant == KbdApp.VARIANT_TRAD && !app.commands);

        app.escape = true;
        app.escapeBuffer = "\\hp";
        app.executeEscape();
        app.commitCandidate(4);
        check("点 au → 自动", app.theme == KbdApp.THEME_AUTO && !app.commands);

        app.escape = true;
        app.escapeBuffer = "\\hp";
        app.executeEscape();
        app.chinese = false;
        app.tapKey("a");
        check("面板上按字母 → 关面板并输入",
                !app.commands && "a".equals(h.committed.toString()));
    }

    public static void main(String[] args) {
        modifiers();
        modifierCombos();
        asciiCombos();
        fQuotes();
        language();
        symbolsLayer();
        escapeMode();
        enterKey();
        spaceKey();
        backspaceKey();
        emitChar();
        letters();
        navAndRepeat();
        scroll();
        candidateWidths();
        landscapeMode();
        landscapeGestures();
        commands();
        longPressRelease();
        tapSymbol();
        commitBlocks();

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // 2.1
    private static void modifiers() {
        Host h = new Host();
        KbdApp app = newApp(h);

        app.tapKey("shift");
        check("shift 点按 → active", app.mods.get("shift").active);

        app.tapKey("shift");
        check("shift 再点 → inactive", !app.mods.get("shift").active);

        app.longKey("shift");
        check("shift 长按 → locked", app.mods.get("shift").active
                && app.mods.get("shift").locked);

        app.tapKey("shift");
        check("locked 时点按 → 全部清除", !app.mods.get("shift").active
                && !app.mods.get("shift").locked);

        app.tapKey("ctrl");
        app.clearTempMods();
        check("clearTempMods 清临时修饰", !app.mods.get("ctrl").active);

        app.lockModifier("alt");
        app.clearTempMods();
        check("clearTempMods 保留锁定", app.mods.get("alt").active
                && app.mods.get("alt").locked);

        check("功能行顺序 ctrl / alt / shift（shift 离开屏幕左缘）",
                "ctrl".equals(KbdData.PORTRAIT_ROWS[3][0])
                        && "alt".equals(KbdData.PORTRAIT_ROWS[3][1])
                        && "shift".equals(KbdData.PORTRAIT_ROWS[3][2]));
    }

    // 2.2
    private static void language() {
        Host h = new Host();
        KbdApp app = composingApp(h);
        app.tapKey("lang");
        check("组词中点中英 → 不变", app.chinese);

        Host h2 = new Host();
        KbdApp idle = newApp(h2);
        idle.tapKey("lang");
        check("空闲点中英 → 切英文", !idle.chinese);

        idle.symbols = true;
        idle.tapKey("lang");
        check("符号层点中英 → 退出符号层", !idle.symbols);
    }

    // 2.8 / D4 前置
    private static void symbolsLayer() {
        Host h = new Host();
        KbdApp app = composingApp(h);
        app.enterSymbols();
        check("组词中进符号层 → 拦截", !app.symbols);

        Host h2 = new Host();
        KbdApp idle = newApp(h2);
        idle.enterSymbols();
        check("空闲进符号层 → 生效", idle.symbols && idle.symbolRow == 0);

        idle.exitSymbols();
        check("退出符号层", !idle.symbols);

        idle.longKey("lang");
        check("长按中英 → 进符号层", idle.symbols);
        idle.longKey("lang");
        check("长按中英 → 退符号层", !idle.symbols);
    }

    // 2.11
    private static void escapeMode() {
        Host h = new Host();
        KbdApp app = composingApp(h);
        app.longKey("space");
        check("组词中长按空格 → 拦截", !app.escape);

        Host h2 = new Host();
        KbdApp idle = newApp(h2);
        idle.longKey("space");
        check("长按空格 → 转义", idle.escape && "\\".equals(idle.escapeBuffer));

        idle.letter("n");
        check("转义中输入字母 → 追加", "\\n".equals(idle.escapeBuffer));
        check("转义缓冲同步到 composing", "\\n".equals(h2.composing));

        idle.appendEscape("……");
        check("转义接受多字符标点", "\\n……".equals(idle.escapeBuffer));

        idle.emitChar("Tab");
        check("转义中 Tab 被拒", "\\n……".equals(idle.escapeBuffer));

        int before = idle.escapeBuffer.length();
        idle.tapKey("backspace");
        check("转义中退格 → 缓冲变短", idle.escapeBuffer.length() == before - 1
                && idle.escape);

        Host h3 = new Host();
        KbdApp ex = newApp(h3);
        ex.longKey("space");
        ex.letter("n");
        ex.tapKey("space");
        check("\\n 空格 → 插入换行", "\n".equals(h3.committed.toString()) && !ex.escape);
        check("执行后结束 composing", h3.composing.isEmpty());

        Host h4 = new Host();
        KbdApp u = newApp(h4);
        u.longKey("space");
        u.letter("u");
        for (String digit : new String[]{"4", "f", "6", "0"}) {
            if (digit.length() == 1 && Character.isDigit(digit.charAt(0))) {
                u.emitChar(digit); // 数字需要上滑输入
            } else {
                u.letter(digit);
            }
        }
        u.tapKey("space");
        check("\\u4f60 空格 → 插入你", "你".equals(h4.committed.toString()));

        Host h5 = new Host();
        KbdApp bs = newApp(h5);
        bs.longKey("space");
        bs.appendEscape("\\");
        bs.tapKey("space");
        check("\\\\ 空格 → 插入反斜杠", "\\".equals(h5.committed.toString()));

        Host h6 = new Host();
        KbdApp bad = newApp(h6);
        bad.longKey("space");
        bad.letter("z");
        bad.tapKey("space");
        check("未知转义 → 不插入且退出", h6.committed.length() == 0 && !bad.escape);

        Host h7 = new Host();
        KbdApp exit = newApp(h7);
        exit.longKey("space");
        exit.longKey("space");
        check("转义中长按空格 → 退出", !exit.escape);
    }

    // 2.5
    private static void enterKey() {
        Host h = new Host();
        KbdApp app = newApp(h);
        app.longKey("space");
        app.tapKey("enter");
        check("转义中点确认 → 无效", h.committed.length() == 0);

        Host h2 = new Host();
        KbdApp comp = composingApp(h2);
        comp.tapKey("enter");
        check("组词中点确认 → 展开", comp.expanded);
        comp.tapKey("enter");
        check("再点确认 → 返回", !comp.expanded);

        Host h3 = new Host();
        KbdApp idle = newApp(h3);
        idle.tapKey("enter");
        check("空闲点确认 → 换行", "\n".equals(h3.committed.toString()));
    }

    // 2.3
    private static void spaceKey() {
        Host h = new Host();
        KbdApp app = newApp(h);
        app.tapKey("space");
        check("空闲空格 → 插入空格", " ".equals(h.committed.toString()));

        Host h2 = new Host();
        KbdApp comp = composingApp(h2);
        comp.tapKey("space");
        check("组词空格 → 不直接插入空格", h2.committed.length() == 0);
    }

    // 2.4
    private static void backspaceKey() {
        Host h = new Host();
        KbdApp app = newApp(h);
        app.tapKey("backspace");
        check("空闲退格 → 发 KEY_DEL",
                h.keys.size() == 1 && h.keys.get(0) == KbdApp.KEY_DEL);

        app.mods.get("shift").active = true;
        app.tapKey("backspace");
        check("退格后清临时修饰", !app.mods.get("shift").active);
    }

    // 2.10
    private static void emitChar() {
        Host h = new Host();
        KbdApp app = newApp(h);
        app.emitChar("1");
        check("字母层上滑字符 → 插入", "1".equals(h.committed.toString()));

        Host h2 = new Host();
        KbdApp tab = newApp(h2);
        tab.emitChar("Tab");
        check("Tab → 插入制表", "\t".equals(h2.committed.toString()));

        Host h3 = new Host();
        KbdApp esc = newApp(h3);
        esc.emitChar("Esc");
        check("Esc → 发送 KEYCODE_ESCAPE", h3.keys.size() == 1
                && h3.keys.get(0) == KbdApp.KEY_ESCAPE && h3.metas.get(0) == 0);

        Host h4 = new Host();
        KbdApp comp = composingApp(h4);
        comp.emitChar("1");
        check("组词中上滑 → 拦截", h4.committed.length() == 0);

        Host h5 = new Host();
        KbdApp sym = newApp(h5);
        sym.symbols = true;
        sym.emitChar("，");
        check("符号层上滑 → 插入", "，".equals(h5.committed.toString()));
    }

    // 2.7
    private static void letters() {
        Host h = new Host();
        KbdApp app = newApp(h);
        app.mods.get("shift").active = true;
        app.letter("a");
        check("中文+Shift → 大写直上屏", "A".equals(h.committed.toString())
                && !app.mods.get("shift").active);

        Host h2 = new Host();
        KbdApp ctrl = newApp(h2);
        ctrl.mods.get("ctrl").active = true;
        ctrl.letter("a");
        check("Ctrl+字母 → 不输入且清修饰",
                h2.committed.length() == 0 && !ctrl.mods.get("ctrl").active);
    }

    // 2.6 / 2.9
    private static void navAndRepeat() {
        Host h = new Host();
        KbdApp comp = composingApp(h);
        comp.nav("left");
        check("组词中方向键 → 不发键", h.keys.isEmpty());

        comp.clearTempMods();
        Host h2 = new Host();
        KbdApp idle = newApp(h2);
        idle.nav("left");
        check("空闲左方向 → 发 DPAD_LEFT",
                h2.keys.size() == 1 && h2.keys.get(0) == KbdApp.KEY_DPAD_LEFT);

        idle.repeatKey("arrowUp");
        check("上方向键不连发", h2.keys.size() == 1);

        idle.repeatKey("arrowLeft");
        check("左方向键可连发", h2.keys.size() == 2);
    }

    private static void scroll() {
        KbdApp app = newApp(new Host());
        app.symbols = true;
        app.scrollSymbols(-1);
        check("符号回退一行 = 末行", app.symbolRow == app.symbolRowCount() - 1);
        app.scrollSymbols(1);
        check("符号前进一行回到 0", app.symbolRow == 0);

        KbdApp comp = newApp(new Host());
        comp.candidates = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            comp.candidates.add("候" + i);
        }
        comp.expanded = true;
        check("每行 5 个（2 键宽 × 5 = 10 键）",
                comp.barRow().items.size() == 5 && comp.barRow().keys == 10);
        check("展开：常驻行从第 0 个开始", comp.barRow().items.get(0).index == 0);
        check("展开：面板首格 = 第 6 个", comp.panelRow(0).items.get(0).index == 5);

        comp.scrollCandidates(1);
        check("滚一行：常驻行跟着移动", comp.barRow().items.get(0).index == 5);
        check("滚一行：面板接着常驻行", comp.panelRow(0).items.get(0).index == 10);

        comp.scrollCandidates(100);
        check("候选滚到末行（常驻行计入可见 10 行）",
                comp.candidateRow == KbdLayout.candScrollMax(
                        KbdLayout.candidateRows(comp.candidates)));
        check("末行末格 = 列表最后一个",
                comp.panelRow(8) != null && comp.panelRow(8).items.get(4).index == 79);
        comp.scrollCandidates(-100);
        check("候选回到首行", comp.candidateRow == 0);
    }

    // 2.12
    private static void tapSymbol() {
        Host h = new Host();
        KbdApp app = newApp(h);
        app.mods.get("alt").active = true;
        app.tapSymbol("，");
        check("符号点按 → 插入并清修饰", "，".equals(h.committed.toString())
                && !app.mods.get("alt").active);
    }

    // 2.13 / 2.14（无引擎时）
    private static void commitBlocks() {
        Host h = new Host();
        KbdApp app = composingApp(h);
        app.commitCandidate(0);
        check("无引擎时选词 → 不崩溃、不插入", h.committed.length() == 0);
    }
}
