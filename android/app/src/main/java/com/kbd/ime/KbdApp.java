package com.kbd.ime;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用层，对应 webdemo/js/app.js。
 * 唯一的状态持有者；所有动作都从这里发起，渲染与手势层不得直接改状态。
 */
final class KbdApp {

    interface Host {
        void commitText(String text);

        /** 显示拼音/转义浮层（键盘上方气泡，不写入应用输入框）。 */
        void setComposing(String text);

        void finishComposing();

        /** 发送带修饰态的按键（无则传 0）。 */
        void sendKey(int keyCode, int metaState);

        /** 简体 → 繁体（平台实现；无可用转换时原样返回）。 */
        String toTraditional(String text);

        /** 命令效果已改变（主题/简繁），用于持久化。 */
        void onSettingsChanged(int theme, int variant);

        /** 符号面板当前行变化，用于持久化。 */
        void onSymbolRowChanged(int row);

        /** 底部安全区覆盖值变化（-1 = 自动），用于持久化。 */
        void onBottomSafeChanged(int b);

        /** 输入方案变化，用于持久化。 */
        void onSchemeChanged(String scheme);

        /** 几何参数变化，需要重建布局。 */
        void onLayoutChanged();

        void hideKeyboard();

        void invalidate();
    }

    static final int KEY_DEL = 67;
    static final int KEY_DPAD_UP = 19;
    static final int KEY_DPAD_DOWN = 20;
    static final int KEY_DPAD_LEFT = 21;
    static final int KEY_DPAD_RIGHT = 22;
    static final int KEY_A = 29;              // KeyEvent.KEYCODE_A
    static final int KEY_ESCAPE = 111;        // KeyEvent.KEYCODE_ESCAPE
    static final int KEY_SPACE = 62;          // KeyEvent.KEYCODE_SPACE
    static final int KEY_TAB = 61;            // KeyEvent.KEYCODE_TAB

    static final int META_SHIFT = 0x00000001; // KeyEvent.META_SHIFT_ON
    static final int META_ALT = 0x00000002;   // KeyEvent.META_ALT_ON
    static final int META_CTRL = 0x00001000;  // KeyEvent.META_CTRL_ON

    // 标准键盘：非 Shift / Shift 两层字符，均按物理键位一一对应
    private static final String ASCII_BASE =
            "`1234567890-=qwertyuiop[]\\asdfghjkl;'zxcvbnm,./";
    private static final String ASCII_SHIFT = "~!@#$%^&*()_+{}|:\"<>?";
    private static final String ASCII_SHIFT_BASE = "`1234567890-=[]\\;',./";

    static final int CANDIDATE_LIMIT = 128;
    static final int MAX_COMPOSING = 40;
    static final int MAX_ESCAPE = 24;   // 需容纳 \zh=zhongwenzhixing

    static final int THEME_AUTO = 0;
    static final int THEME_DARK = 1;
    static final int THEME_LIGHT = 2;
    static final int VARIANT_SIMPLE = 0;
    static final int VARIANT_TRAD = 1;

    static final class Mod {
        boolean active;
        boolean locked;
    }

    final Host host;
    LibimeEngine engine;
    KbdLayout layout;    // 由输入视图设置；几何与模式从布局读取

    /** 系统底部安全区参考值（px）。 */
    int systemBottomSafe = 32;
    /** -1 = 跟随系统，>=0 = \sb 覆盖值。 */
    int bottomSafeOverride = -1;

    boolean chinese = true;
    boolean symbols;
    int symbolRow;
    boolean landscape;   // 由输入视图按 KbdLayout 设置

    int theme = THEME_AUTO;
    int variant = VARIANT_SIMPLE;

    boolean escape;
    String escapeBuffer = "\\";

    final Map<String, Mod> mods = new LinkedHashMap<>();

    String preedit = "";
    List<String> candidates = new ArrayList<>();
    boolean expanded;
    int candidateRow;

    String status = "";

    /** 中文 f 键引号循环：true = 下一次插入左引号。 */
    boolean quoteDoubleOpen = true;
    boolean quoteSingleOpen = true;

    /** 编辑器里当前是否有我们的 composing 文本。 */
    boolean composingShown;

    KbdApp(Host host) {
        this.host = host;
        mods.put("shift", new Mod());
        mods.put("ctrl", new Mod());
        mods.put("alt", new Mod());
    }

    // ---------- 引擎 ----------

    void setEngine(LibimeEngine engine) {
        this.engine = engine;
        status = "";
        refreshFromEngine();
    }

    // ---------- 几何输入 ----------

    void setLayout(KbdLayout layout) {
        this.layout = layout;
    }

    int bottomSafe() {
        return bottomSafeOverride >= 0 ? bottomSafeOverride : systemBottomSafe;
    }

    void restoreBottomSafe(int value) {
        bottomSafeOverride = value;
    }

    void setSystemBottomSafe(int value) {
        int next = Math.max(0, value);
        if (systemBottomSafe == next) {
            return;
        }
        systemBottomSafe = next;
        if (bottomSafeOverride < 0) {
            host.onLayoutChanged();
        }
    }

    void setBottomSafe(int value) {
        bottomSafeOverride = Math.max(0, Math.min(200, value));
        host.onBottomSafeChanged(bottomSafeOverride);
        host.onLayoutChanged();
    }

    void setBottomSafeAuto() {
        bottomSafeOverride = -1;
        host.onBottomSafeChanged(-1);
        host.onLayoutChanged();
    }

    void setStatus(String message) {
        status = message;
        host.invalidate();
    }

    void setScheme(String scheme) {
        if (!KbdData.SCHEMES.containsKey(scheme)) {
            return;
        }
        if (engine != null) {
            engine.setScheme(scheme);
        }
        host.onSchemeChanged(scheme);
        refreshFromEngine();
        host.invalidate();
    }

    private void refreshFromEngine() {
        if (engine == null || !engine.isReady()) {
            preedit = "";
            candidates = new ArrayList<>();
            expanded = false;
            return;
        }
        EngineBridge.State state = engine.state(CANDIDATE_LIMIT);
        preedit = state == null ? "" : state.preedit;
        candidates = new ArrayList<>();
        if (state != null) {
            for (String c : state.candidates) {
                candidates.add(outText(c));
            }
        }
        if (candidates.isEmpty()) {
            expanded = false;
            candidateRow = 0;
        } else if (expanded) {
            candidateRow = Math.min(candidateRow, scrollCandidateMax());
        }
        if (escape) {
            host.setComposing(escapeBuffer);
            composingShown = true;
        } else if (!preedit.isEmpty()) {
            host.setComposing(displayPreedit());
            composingShown = true;
        } else if (composingShown) {
            host.finishComposing();
            composingShown = false;
        }
    }

    /** 切换输入框：清空组词，避免把上一处的拼音带过来。 */
    void restartComposition() {
        if (engine != null && engine.isReady()) {
            engine.reset();
        }
        preedit = "";
        candidates = new ArrayList<>();
        expanded = false;
        candidateRow = 0;
        escape = false;
        escapeBuffer = "\\";
        if (composingShown) {
            host.finishComposing();
            composingShown = false;
        }
        host.invalidate();
    }

    boolean composing() {
        return !preedit.isEmpty();
    }

    String displayPreedit() {
        return preedit.replace(' ', '\'');
    }

    // ---------- 候选 ----------

    /** 常驻候选行：与展开面板同属一个序列，滚动时随 candidateRow 一起移动。 */
    KbdLayout.CandRow barRow() {
        if (layout == null) {
            return null;
        }
        List<KbdLayout.CandRow> rows = layout.candidateRows(candidates);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(Math.min(candidateRow, rows.size() - 1));
    }

    /** 展开面板：第 row 行（0 = 常驻行的下一行）。 */
    KbdLayout.CandRow panelRow(int row) {
        if (layout == null) {
            return null;
        }
        List<KbdLayout.CandRow> rows = layout.candidateRows(candidates);
        int index = candidateRow + 1 + row;
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    void commitCandidate(int index) {
        if (index >= 0 && index < candidates.size()) {
            commitIndex(index);
        }
    }

    // ---------- 内部命令 ----------

    boolean isDark(boolean systemNight) {
        if (theme == THEME_DARK) {
            return true;
        }
        if (theme == THEME_LIGHT) {
            return false;
        }
        return systemNight;
    }

    private String outText(String text) {
        return variant == VARIANT_TRAD ? host.toTraditional(text) : text;
    }

    private void runAction(String action) {
        switch (action) {
            case "simple":
                variant = VARIANT_SIMPLE;
                refreshFromEngine();
                host.onSettingsChanged(theme, variant);
                host.invalidate();
                break;
            case "trad":
                variant = VARIANT_TRAD;
                refreshFromEngine();
                host.onSettingsChanged(theme, variant);
                host.invalidate();
                break;
            case "dark":
                theme = THEME_DARK;
                host.onSettingsChanged(theme, variant);
                host.invalidate();
                break;
            case "light":
                theme = THEME_LIGHT;
                host.onSettingsChanged(theme, variant);
                host.invalidate();
                break;
            case "auto":
                theme = THEME_AUTO;
                host.onSettingsChanged(theme, variant);
                host.invalidate();
                break;
            default:
                break;
        }
    }

    private void commitIndex(int index) {
        if (engine == null || !engine.isReady()) {
            return;
        }
        if (composingShown) {
            host.finishComposing();
            composingShown = false;
        }
        String text = engine.select(index);
        if (text != null && !text.isEmpty()) {
            host.commitText(outText(text));
        }
        refreshFromEngine();
        if (!composing()) {
            expanded = false;
            candidateRow = 0;
        }
        clearTempMods();
        host.invalidate();
    }

    void commitFirstCandidate() {
        if (!candidates.isEmpty()) {
            commitIndex(0);
        }
    }

    /** 非候选上屏：同时计入引擎上文（候选由 engine.select 自行记账）。 */
    private void commit(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (engine != null && engine.isReady()) {
            engine.recordText(text);
        }
        host.commitText(text);
    }

    // ---------- 修饰键 ----------

    void toggleModifier(String name) {
        Mod mod = mods.get(name);
        if (mod == null) {
            return;
        }
        if (mod.locked) {
            mod.active = false;
            mod.locked = false;
        } else {
            mod.active = !mod.active;
            mod.locked = false;
        }
        host.invalidate();
    }

    void lockModifier(String name) {
        Mod mod = mods.get(name);
        if (mod == null) {
            return;
        }
        mod.active = true;
        mod.locked = true;
        host.invalidate();
    }

    void clearTempMods() {
        boolean changed = false;
        for (Mod mod : mods.values()) {
            if (mod.active && !mod.locked) {
                mod.active = false;
                changed = true;
            }
        }
        if (changed) {
            host.invalidate();
        }
    }

    // ---------- 点按 ----------

    void tapKey(String id) {
        KbdData.Key def = KbdData.KEYS.get(id);
        if (def == null) {
            return;
        }
        switch (def.kind) {
            case MODIFIER:
                toggleModifier(def.mod);
                break;
            case LANG:
                shortLang();
                break;
            case SPACE:
                space();
                break;
            case BACKSPACE:
                backspace();
                break;
            case ENTER:
                enter();
                break;
            case NAV:
                nav(def.dir);
                break;
            default:
                letter(id);
                break;
        }
    }

    private void space() {
        if (escape) {
            executeEscape();
            return;
        }
        if (composing()) {
            commitFirstCandidate();
            clearTempMods();
            return;
        }
        commit(" ");
        clearTempMods();
        host.invalidate();
    }

    private void backspace() {
        if (escape) {
            if (escapeBuffer.length() > 1) {
                escapeBuffer = escapeBuffer.substring(0, escapeBuffer.length() - 1);
            }
            host.setComposing(escapeBuffer);
            composingShown = true;
            clearTempMods();
            host.invalidate();
            return;
        }
        if (composing() && engine != null && engine.isReady()) {
            engine.backspace();
            refreshFromEngine();
        } else {
            if (engine != null && engine.isReady()) {
                engine.recordBackspace();
            }
            host.sendKey(KEY_DEL, metaState());
        }
        clearTempMods();
        host.invalidate();
    }

    private void enter() {
        if (escape) {
            return;
        }
        if (composing()) {
            expanded = !expanded;
            if (!expanded) {
                candidateRow = 0;
            }
            clearTempMods();
            host.invalidate();
            return;
        }
        commit("\n");
        clearTempMods();
        host.invalidate();
    }

    void nav(String dir) {
        if (composing() || escape) {
            clearTempMods();
            return;
        }
        int meta = metaState();
        switch (dir) {
            case "left":
                host.sendKey(KEY_DPAD_LEFT, meta);
                break;
            case "right":
                host.sendKey(KEY_DPAD_RIGHT, meta);
                break;
            case "up":
                host.sendKey(KEY_DPAD_UP, meta);
                break;
            default:
                host.sendKey(KEY_DPAD_DOWN, meta);
                break;
        }
        clearTempMods();
    }

    /** ctrl/alt 的修饰态；组词中不发送组合键（保持原型语义）。 */
    private int metaState() {
        int meta = 0;
        if (mods.get("ctrl").active) {
            meta |= META_CTRL;
        }
        if (mods.get("alt").active) {
            meta |= META_ALT;
        }
        return meta;
    }

    /** 上/下滑标签 → 物理按键 {keyCode, 附加修饰}；无对应键返回 null（如 ……、×、¥）。 */
    static int[] asciiKey(String label) {
        if ("Tab".equals(label)) {
            return new int[]{KEY_TAB, 0};
        }
        if ("Esc".equals(label)) {
            return new int[]{KEY_ESCAPE, 0};
        }
        String text = Normalizer.normalize(label, Normalizer.Form.NFKC);
        if (text.length() != 1) {
            return null;
        }
        char c = text.charAt(0);
        if (c > 0x7F) {
            return null;
        }
        int index = ASCII_BASE.indexOf(c);
        if (index >= 0) {
            return new int[]{baseKeyCode(c), 0};
        }
        index = ASCII_SHIFT.indexOf(c);
        if (index >= 0) {
            return new int[]{baseKeyCode(ASCII_SHIFT_BASE.charAt(index)), META_SHIFT};
        }
        return null;
    }

    private static int baseKeyCode(char c) {
        switch (c) {
            case ' ':
                return KEY_SPACE;
            case '`':
                return 68;
            case '-':
                return 69;
            case '=':
                return 70;
            case '[':
                return 71;
            case ']':
                return 72;
            case '\\':
                return 73;
            case ';':
                return 74;
            case '\'':
                return 75;
            case ',':
                return 55;
            case '.':
                return 56;
            case '/':
                return 76;
            default:
                if (c >= '0' && c <= '9') {
                    return 7 + (c - '0');
                }
                if (c >= 'a' && c <= 'z') {
                    return KEY_A + (c - 'a');
                }
                return 0;
        }
    }

    String nextDoubleQuote() {
        return quoteDoubleOpen ? "“" : "”";
    }

    String nextSingleQuote() {
        return quoteSingleOpen ? "‘" : "’";
    }

    /** 插入后切换到下一个引号。 */
    private void cycleQuote(String label) {
        if ("“".equals(label)) {
            quoteDoubleOpen = false;
        } else if ("”".equals(label)) {
            quoteDoubleOpen = true;
        } else if ("‘".equals(label)) {
            quoteSingleOpen = false;
        } else if ("’".equals(label)) {
            quoteSingleOpen = true;
        }
    }

    void letter(String id) {
        KbdData.Key def = KbdData.KEYS.get(id);
        if (def == null) {
            return;
        }
        if (escape) {
            appendEscape(id);
            if (mods.get("shift").active) {
                clearTempMods();
            }
            return;
        }
        if (symbols) {
            emitChar(id);
            return;
        }
        boolean shift = mods.get("shift").active;
        boolean ctrl = mods.get("ctrl").active;
        boolean alt = mods.get("alt").active;

        if (ctrl || alt) {
            int meta = metaState();
            if (meta != 0 && !composing() && id.length() == 1
                    && id.charAt(0) >= 'a' && id.charAt(0) <= 'z') {
                host.sendKey(KEY_A + (id.charAt(0) - 'a'), meta);
            }
            clearTempMods();
            host.invalidate();
            return;
        }
        if (chinese) {
            if (composing()) {
                if (shift) {
                    clearTempMods();
                } else {
                    appendPinyin(id);
                }
                return;
            }
            if (shift) {
                commit(id.toUpperCase());
                clearTempMods();
                host.invalidate();
                return;
            }
            appendPinyin(id);
            return;
        }
        emitChar(shift ? id.toUpperCase() : id);
    }

    private void appendPinyin(String id) {
        if (preedit.length() >= MAX_COMPOSING) {
            return;
        }
        if (engine == null || !engine.isReady()) {
            return;
        }
        engine.input(Character.toLowerCase(id.charAt(0)));
        refreshFromEngine();
        host.invalidate();
    }

    // ---------- 滑动与符号 ----------

    /** 上/下滑：目标标签为字符时走这里。 */
    void emitChar(String label) {
        if (label == null || label.isEmpty()) {
            clearTempMods();
            return;
        }
        if (escape) {
            if (label.length() == 1) {
                appendEscape(label);
                return;
            }
            if ("Tab".equals(label) || "Esc".equals(label)) {
                return;
            }
            appendEscape(label);
            return;
        }
        if (symbols) {
            commit(label);
            host.invalidate();
            return;
        }
        if (composing()) {
            clearTempMods();
            host.invalidate();
            return;
        }
        int meta = metaState();
        boolean tabEsc = "Tab".equals(label) || "Esc".equals(label);
        if (tabEsc && mods.get("shift").active) {
            meta |= META_SHIFT;
        }
        if (meta != 0) {
            int[] ascii = asciiKey(label);
            if (ascii != null) {
                host.sendKey(ascii[0], meta | ascii[1]);
                clearTempMods();
                host.invalidate();
                return;
            }
        }
        if ("Tab".equals(label)) {
            commit("\t");
        } else if ("Esc".equals(label)) {
            host.sendKey(KEY_ESCAPE, 0);
        } else {
            commit(label);
            cycleQuote(label);
        }
        clearTempMods();
        host.invalidate();
    }

    /** 符号层点按。 */
    void tapSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return;
        }
        if (escape) {
            appendEscape(symbol);
            return;
        }
        commit(symbol);
        clearTempMods();
        host.invalidate();
    }

    void emptySlide(String id) {
        clearTempMods();
    }

    // ---------- 语言与层 ----------

    void shortLang() {
        if (symbols) {
            exitSymbols();
            return;
        }
        if (composing()) {
            return;
        }
        chinese = !chinese;
        clearTempMods();
        host.invalidate();
    }

    void enterSymbols() {
        if (composing()) {
            return;
        }
        symbols = true;
        expanded = false;
        clearTempMods();
        host.invalidate();
    }

    void exitSymbols() {
        symbols = false;
        clearTempMods();
        host.invalidate();
    }

    int symbolCols() {
        if (layout != null) {
            return layout.symbolCols;
        }
        return landscape ? 16 : 10;
    }

    int symbolRowCount() {
        return Math.max(1, (KbdData.SYMBOLS.size() + symbolCols() - 1) / symbolCols());
    }

    String symbolAt(int row, int col) {
        int rows = symbolRowCount();
        int r = ((symbolRow + row) % rows + rows) % rows;
        int index = r * symbolCols() + col;
        return index < KbdData.SYMBOLS.size() ? KbdData.SYMBOLS.get(index) : "";
    }

    // ---------- 长按 ----------

    void longKey(String id) {
        KbdData.Key def = KbdData.KEYS.get(id);
        if (def == null) {
            return;
        }
        switch (def.kind) {
            case MODIFIER:
                lockModifier(def.mod);
                break;
            case SPACE:
                longSpace();
                break;
            case LANG:
                if (symbols) {
                    exitSymbols();
                } else {
                    enterSymbols();
                }
                break;
            case ENTER:
                if (!composing()) {
                    host.hideKeyboard();
                }
                break;
            case BACKSPACE:
            case NAV:
                repeatKey(id);
                break;
            default:
                break;
        }
    }

    void repeatKey(String id) {
        KbdData.Key def = KbdData.KEYS.get(id);
        if (def == null) {
            return;
        }
        if (def.kind == KbdData.Kind.BACKSPACE) {
            backspace();
            return;
        }
        if (def.kind == KbdData.Kind.NAV) {
            if (composing() || escape) {
                return;
            }
            if ("left".equals(def.dir) || "right".equals(def.dir)) {
                nav(def.dir);
            }
        }
    }

    private void longSpace() {
        if (escape) {
            exitEscape();
            return;
        }
        if (composing()) {
            return;
        }
        enterEscape();
    }

    // ---------- 转义 ----------

    void enterEscape() {
        escape = true;
        escapeBuffer = "\\";
        clearTempMods();
        host.setComposing(escapeBuffer);
        composingShown = true;
        host.invalidate();
    }

    void exitEscape() {
        escape = false;
        escapeBuffer = "\\";
        if (composingShown && !composing()) {
            host.finishComposing();
            composingShown = false;
        }
        host.invalidate();
    }

    void appendEscape(String ch) {
        if (escapeBuffer.length() >= MAX_ESCAPE) {
            return;
        }
        escapeBuffer += ch;
        host.setComposing(escapeBuffer);
        composingShown = true;
        host.invalidate();
    }

    static final class Preview {
        boolean valid;
        String text;
        KbdData.EscapeCommand command;
        String value;
        Integer bottomSafe;
        boolean bottomAuto;
        String scheme;
        String action;
    }

    Preview escapePreview() {
        Preview out = new Preview();
        String body = escapeBuffer.length() > 1 ? escapeBuffer.substring(1) : "";
        body = Normalizer.normalize(body, Normalizer.Form.NFKC);
        if (body.isEmpty()) {
            out.text = "n 换行   t 制表   u Unicode   sk=dark|light|auto   st=s|t   sb=数字   zh=方案";
            return out;
        }
        if ("sb=auto".equals(body)) {
            out.valid = true;
            out.bottomAuto = true;
            out.text = "→ 底部安全区 自动";
            return out;
        }
        Matcher sb = SB_NUM.matcher(body);
        if (sb.matches()) {
            int value = Integer.parseInt(sb.group(1));
            out.valid = true;
            out.bottomSafe = Math.max(0, Math.min(200, value));
            out.text = "→ 底部安全区 " + out.bottomSafe;
            return out;
        }
        Matcher zh = ZH_SCHEME.matcher(body);
        if (zh.matches()) {
            String code = zh.group(1).toLowerCase(java.util.Locale.ROOT);
            String label = KbdData.SCHEMES.get(code);
            if (label != null) {
                out.valid = true;
                out.scheme = code;
                out.text = "→ 输入方案 " + label;
            } else {
                out.text = "→ 未知方案 " + code;
            }
            return out;
        }
        Matcher sk = SK_MODE.matcher(body);
        if (sk.matches()) {
            String mode = sk.group(1);
            out.valid = true;
            out.action = mode;
            out.text = "→ 皮肤 " + ("dark".equals(mode) ? "暗色"
                    : "light".equals(mode) ? "亮色" : "自动");
            return out;
        }
        Matcher st = ST_MODE.matcher(body);
        if (st.matches()) {
            boolean trad = "t".equals(st.group(1));
            out.valid = true;
            out.action = trad ? "trad" : "simple";
            out.text = "→ " + (trad ? "繁体" : "简体");
            return out;
        }
        KbdData.EscapeCommand cmd = KbdData.ESCAPE_COMMANDS.get(body);
        if (cmd != null) {
            out.valid = true;
            out.command = cmd;
            out.text = "→ " + cmd.label;
            return out;
        }
        Integer code = parseUnicode(body);
        if (code != null) {
            if (code <= 0x10FFFF && !(code >= 0xD800 && code <= 0xDFFF)) {
                out.valid = true;
                out.value = new String(Character.toChars(code));
                out.text = "→ " + out.value;
            }
            return out;
        }
        if ("\\".equals(body)) {
            out.valid = true;
            out.value = "\\";
            out.text = "→ \\";
        }
        return out;
    }

    private static final Pattern SB_NUM = Pattern.compile("^sb=([0-9]{1,3})$");
    private static final Pattern ZH_SCHEME = Pattern.compile("^zh=([A-Za-z]+)$");
    private static final Pattern SK_MODE = Pattern.compile("^sk=(dark|light|auto)$");
    private static final Pattern ST_MODE = Pattern.compile("^st=([st])$");
    private static final Pattern UNICODE_4 = Pattern.compile("^u([0-9a-fA-F]{4})$");
    private static final Pattern UNICODE_BRACE = Pattern.compile("^u\\{([0-9a-fA-F]{1,6})\\}$");

    private static Integer parseUnicode(String body) {
        Matcher m4 = UNICODE_4.matcher(body);
        if (m4.matches()) {
            return Integer.parseInt(m4.group(1), 16);
        }
        Matcher mb = UNICODE_BRACE.matcher(body);
        if (mb.matches()) {
            return Integer.parseInt(mb.group(1), 16);
        }
        return null;
    }

    void executeEscape() {
        Preview info = escapePreview();
        exitEscape();
        if (!info.valid) {
            return;
        }
        if (info.bottomAuto) {
            setBottomSafeAuto();
            return;
        }
        if (info.bottomSafe != null) {
            setBottomSafe(info.bottomSafe);
            return;
        }
        if (info.scheme != null) {
            setScheme(info.scheme);
            return;
        }
        if (info.action != null) {
            runAction(info.action);
            return;
        }
        if (info.command != null) {
            if ("text".equals(info.command.type)) {
                commit(outText(info.command.value));
            } else if ("action".equals(info.command.type)) {
                runAction(info.command.value);
            }
            return;
        }
        if (info.value != null) {
            commit(info.value);
        }
    }

    // ---------- 滚动（原型为翻页，此处按行） ----------

    void scrollSymbols(int rows) {
        int n = symbolRowCount();
        symbolRow = ((symbolRow + rows) % n + n) % n;
        host.onSymbolRowChanged(symbolRow);
        host.invalidate();
    }

    void scrollCandidates(int rows) {
        candidateRow = Math.max(0, Math.min(scrollCandidateMax(), candidateRow + rows));
        host.invalidate();
    }

    /** 横屏：中列按 6 键一行流式装箱（与竖屏同一规则）；竖屏：10 键一行。 */
    private int scrollCandidateMax() {
        if (layout == null) {
            return 0;
        }
        if (landscape) {
            return layout.candPanelScrollMax(
                    layout.candidateRows(candidates, layout.landRowKeys),
                    layout.kbH);
        }
        return layout.candScrollMax(layout.candidateRows(candidates));
    }

    void setLandscape(boolean value) {
        if (landscape == value) {
            return;
        }
        landscape = value;
        candidateRow = 0;
        // 符号行跨方向保留，行数不足时收敛到末行
        symbolRow = Math.min(symbolRow, symbolRowCount() - 1);
    }
}
