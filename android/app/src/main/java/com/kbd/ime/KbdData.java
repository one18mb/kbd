package com.kbd.ime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据层，对应 webdemo/js/data.js。
 * 只有静态数据，无状态、无逻辑。
 */
final class KbdData {

    enum Kind { LETTER, MODIFIER, LANG, SPACE, BACKSPACE, ENTER, NAV }

    static final class Label {
        final String en;
        final String zh;

        Label(String en, String zh) {
            this.en = en;
            this.zh = zh;
        }

        String pick(boolean chinese) {
            return chinese ? zh : en;
        }
    }

    static final class Key {
        final String id;
        Kind kind = Kind.LETTER;
        String mod;
        String dir;
        Label main;
        Label up;
        Label down;
        boolean wide;
        boolean repeat;
        String longAction;

        Key(String id) {
            this.id = id;
        }
    }

    static final class EscapeCommand {
        final String label;
        final String type;
        final String value;

        EscapeCommand(String label, String type, String value) {
            this.label = label;
            this.type = type;
            this.value = value;
        }
    }

    /** 中文标点映射，对齐 PC 搜狗“中文标点”。 */
    private static final Map<String, String> ZH_PUNCT = new LinkedHashMap<>();

    static {
        ZH_PUNCT.put("!", "！");
        ZH_PUNCT.put("@", "@");
        ZH_PUNCT.put("#", "#");
        ZH_PUNCT.put("$", "￥");
        ZH_PUNCT.put("%", "%");
        ZH_PUNCT.put("^", "……");
        ZH_PUNCT.put("&", "&");
        ZH_PUNCT.put("*", "×");
        ZH_PUNCT.put("(", "（");
        ZH_PUNCT.put(")", "）");
        ZH_PUNCT.put("~", "～");
        ZH_PUNCT.put("`", "`");
        ZH_PUNCT.put(":", "：");
        ZH_PUNCT.put(";", "；");
        ZH_PUNCT.put("\"", "“");
        ZH_PUNCT.put("'", "‘");
        ZH_PUNCT.put("|", "|");
        ZH_PUNCT.put("\\", "、");
        ZH_PUNCT.put("+", "+");
        ZH_PUNCT.put("=", "=");
        ZH_PUNCT.put("_", "——");
        ZH_PUNCT.put("-", "-");
        ZH_PUNCT.put("{", "{");
        ZH_PUNCT.put("}", "}");
        ZH_PUNCT.put("[", "【");
        ZH_PUNCT.put("]", "】");
        ZH_PUNCT.put("<", "《");
        ZH_PUNCT.put(">", "》");
        ZH_PUNCT.put("?", "？");
        ZH_PUNCT.put(",", "，");
        ZH_PUNCT.put(".", "。");
        ZH_PUNCT.put("/", "/");
    }

    static final Map<String, Key> KEYS = new LinkedHashMap<>();
    static final Map<String, EscapeCommand> ESCAPE_COMMANDS = new LinkedHashMap<>();
    static final String[][] PORTRAIT_ROWS;
    static final List<String> SYMBOLS;

    /** 输入方案：只保留 26 键双拼；微软/紫光/加加使用非字母键，已移除。 */
    static final Map<String, String> SCHEMES = new LinkedHashMap<>();

    static {
        SCHEMES.put("quanpin", "全拼");
        SCHEMES.put("xiaohe", "小鹤双拼");
        SCHEMES.put("ziranma", "自然码");
        SCHEMES.put("abc", "智能ABC");
        SCHEMES.put("zhongwenzhixing", "中文之星");
        SCHEMES.put("gb", "国标双拼");
    }

    static String schemeLabel(String code) {
        String label = SCHEMES.get(code);
        return label == null ? code : label;
    }

    static String normalizeScheme(String code) {
        return SCHEMES.containsKey(code) ? code : "quanpin";
    }

    // 横屏：左右外列 + 左右字母簇（对应 webdemo landscape）
    static final String[] LANDSCAPE_OUTER_LEFT = {"shift", "alt", "ctrl"};
    static final String[] LANDSCAPE_OUTER_RIGHT = {"backspace", "lang", "enter"};
    static final String[][] LANDSCAPE_LEFT = {
            {"q", "w", "e", "r", "t"},
            {"arrowUp", "a", "s", "d", "f"},
            {"arrowDown", "z", "x", "c", "v"}
    };
    static final String[][] LANDSCAPE_RIGHT = {
            {"y", "u", "i", "o", "p"},
            {"g", "h", "j", "k", "l"},
            {"b", "n", "m", "arrowLeft", "arrowRight"}
    };

    private static void add(Key key) {
        KEYS.put(key.id, key);
    }

    private static Label slide(String label) {
        if (label == null) {
            return null;
        }
        return new Label(label, ZH_PUNCT.getOrDefault(label, label));
    }

    private static Key letter(String id, Label up, Label down) {
        Key key = new Key(id);
        key.main = new Label(id, id);
        key.up = up;
        key.down = down;
        return key;
    }

    private static Key nav(String id, String dir) {
        Key key = new Key(id);
        key.kind = Kind.NAV;
        key.dir = dir;
        String arrow = dir.equals("up") ? "↑" : dir.equals("down") ? "↓"
                : dir.equals("left") ? "←" : "→";
        key.main = new Label(arrow, arrow);
        key.repeat = true;
        return key;
    }

    static {
        String[][] row1 = {
                {"q", "1", "!"}, {"w", "2", "@"}, {"e", "3", "#"}, {"r", "4", "$"}, {"t", "5", "%"},
                {"y", "6", "^"}, {"u", "7", "&"}, {"i", "8", "*"}, {"o", "9", "("}, {"p", "0", ")"}
        };
        for (String[] k : row1) {
            add(letter(k[0], slide(k[1]), slide(k[2])));
        }

        add(letter("a", new Label("Tab", "Tab"), null));
        add(letter("s", slide("~"), slide("`")));
        add(letter("d", slide(":"), slide(";")));
        add(letter("f", slide("\""), slide("'")));
        add(letter("g", slide("|"), slide("\\")));
        add(letter("h", slide("+"), slide("=")));
        add(letter("j", slide("_"), slide("-")));
        add(letter("k", slide("{"), slide("[")));
        add(letter("l", slide("}"), slide("]")));
        add(letter("z", slide("Esc"), null));
        add(letter("x", slide("<"), null));
        add(letter("c", slide(">"), null));
        add(letter("v", slide("?"), null));
        add(letter("b", slide(","), null));
        add(letter("n", slide("."), null));
        add(letter("m", slide("/"), null));

        add(nav("arrowUp", "up"));
        add(nav("arrowDown", "down"));
        add(nav("arrowLeft", "left"));
        add(nav("arrowRight", "right"));

        Key shift = new Key("shift");
        shift.kind = Kind.MODIFIER;
        shift.mod = "shift";
        shift.main = new Label("Shft", "Shft");
        shift.longAction = "lock";
        add(shift);

        Key ctrl = new Key("ctrl");
        ctrl.kind = Kind.MODIFIER;
        ctrl.mod = "ctrl";
        ctrl.main = new Label("Ctrl", "Ctrl");
        ctrl.longAction = "lock";
        add(ctrl);

        Key alt = new Key("alt");
        alt.kind = Kind.MODIFIER;
        alt.mod = "alt";
        alt.main = new Label("Alt", "Alt");
        alt.longAction = "lock";
        add(alt);

        Key lang = new Key("lang");
        lang.kind = Kind.LANG;
        lang.main = new Label("英", "中");
        lang.longAction = "symbol";
        add(lang);

        Key space = new Key("space");
        space.kind = Kind.SPACE;
        space.main = new Label("空格", "空格");
        space.longAction = "escape";
        space.wide = true;
        add(space);

        Key backspace = new Key("backspace");
        backspace.kind = Kind.BACKSPACE;
        backspace.main = new Label("删除", "删除");
        backspace.longAction = "repeat";
        backspace.repeat = true;
        backspace.wide = true;
        add(backspace);

        Key enter = new Key("enter");
        enter.kind = Kind.ENTER;
        enter.main = new Label("确认", "确认");
        enter.longAction = "hide";
        enter.wide = true;
        add(enter);

        PORTRAIT_ROWS = new String[][]{
                {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
                {"arrowUp", "a", "s", "d", "f", "g", "h", "j", "k", "l"},
                {"arrowDown", "z", "x", "c", "v", "b", "n", "m", "arrowLeft", "arrowRight"},
                {"ctrl", "alt", "shift", "lang", "space", "backspace", "enter"}
        };

        ESCAPE_COMMANDS.put("n", new EscapeCommand("换行", "text", "\n"));
        ESCAPE_COMMANDS.put("t", new EscapeCommand("制表", "text", "\t"));

        SYMBOLS = buildSymbols();
    }

    private static List<String> buildSymbols() {
        String[] raw = {
                "，", "。", "、", "；", "：", "？", "！", "“", "”", "‘", "’", "（", "）", "【", "】", "《", "》", "〈", "〉",
                "…", "—", "～", "·", "・", "「", "」", "『", "』", "〔", "〕", "〖", "〗", "〘", "〙", "〚", "〛",
                "＋", "－", "×", "÷", "＝", "≠", "≈", "≤", "≥", "±", "∓", "∑", "∏", "∫", "∮", "√", "∛", "∜",
                "∞", "∝", "∠", "⊥", "∥", "△", "▲", "▽", "▼", "○", "●", "□", "■", "◇", "◆", "※", "§", "¶", "†", "‡",
                "←", "→", "↑", "↓", "↔", "↕", "⇐", "⇒", "⇑", "⇓", "⇔", "↖", "↗", "↘", "↙", "↩", "↪", "⇧", "⇩", "⟵", "⟶", "⟷",
                "￥", "$", "€", "£", "¥", "₩", "₽", "₹", "₿", "℃", "℉", "°", "′", "″", "‰", "‱", "№", "℅", "℗", "™", "©", "®",
                "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩", "⑪", "⑫", "⑬", "⑭", "⑮", "⑯", "⑰", "⑱", "⑲", "⑳",
                "Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ", "Ⅺ", "Ⅻ", "ⅰ", "ⅱ", "ⅲ", "ⅳ", "ⅴ", "ⅵ", "ⅶ", "ⅷ", "ⅸ", "ⅹ",
                "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ", "ν", "ξ", "ο", "π", "ρ", "σ", "τ", "υ",
                "φ", "χ", "ψ", "ω", "Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η", "Θ", "Ι", "Κ", "Λ", "Μ", "Ν", "Ξ", "Ο", "Π",
                "Ρ", "Σ", "Τ", "Υ", "Φ", "Χ", "Ψ", "Ω",
                "⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹", "⁺", "⁻", "⁼", "⁽", "⁾", "ⁿ", "ⁱ",
                "₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉", "₊", "₋", "₌", "₍", "₎",
                "½", "⅓", "⅔", "¼", "¾", "⅛", "⅜", "⅝", "⅞", "⅟", "％", "＠", "＃", "＆", "＊", "＋", "－", "＝", "／", "＼",
                "─", "━", "│", "┃", "┌", "┐", "└", "┘", "├", "┤", "┬", "┴", "┼", "╭", "╮", "╯", "╰",
                "╔", "╗", "╚", "╝", "╠", "╣", "╦", "╩", "╬", "═", "║", "╒", "╓", "╕", "╖", "╘", "╙", "╛", "╜",
                "★", "☆", "☀", "☁", "☂", "☃", "☎", "☏", "☑", "☒", "☓", "☕", "☘", "☠", "☢", "☣", "☮", "☯", "☸", "☹", "☺", "☻", "☼",
                "♠", "♥", "♦", "♣", "♤", "♡", "♢", "♧", "♪", "♫", "♬", "♭", "♮", "♯", "♀", "♂", "⚥", "⚧",
                "✓", "✔", "✗", "✘", "✚", "✛", "✜", "✝", "✞", "✟", "✠", "✡", "✢", "✣", "✤", "✥", "✦", "✧", "✨", "✩", "✪", "✫", "✬", "✭", "✮", "✯", "✰",
                "⚠", "⚡", "⚙", "⚖", "⚔", "⚰", "⚱", "⚽", "⚾", "⛳", "⛵", "⛺", "⛽", "✈", "✉", "✂", "✃", "✄", "✆", "✇",
                "♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛", "♜", "♝", "♞", "♟",
                "ⓐ", "ⓑ", "ⓒ", "ⓓ", "ⓔ", "ⓕ", "ⓖ", "ⓗ", "ⓘ", "ⓙ", "ⓚ", "ⓛ", "ⓜ", "ⓝ", "ⓞ", "ⓟ", "ⓠ", "ⓡ", "ⓢ", "ⓣ", "ⓤ", "ⓥ", "ⓦ", "ⓧ", "ⓨ", "ⓩ",
                "Ⓐ", "Ⓑ", "Ⓒ", "Ⓓ", "Ⓔ", "Ⓕ", "Ⓖ", "Ⓗ", "Ⓘ", "Ⓙ", "Ⓚ", "Ⓛ", "Ⓜ", "Ⓝ", "Ⓞ", "Ⓟ", "Ⓠ", "Ⓡ", "Ⓢ", "Ⓣ", "Ⓤ", "Ⓥ", "Ⓦ", "Ⓧ", "Ⓨ", "Ⓩ"
        };

        Set<String> enterable = new LinkedHashSet<>();
        for (Key key : KEYS.values()) {
            if (key.up != null) {
                enterable.add(key.up.en);
                enterable.add(key.up.zh);
            }
            if (key.down != null) {
                enterable.add(key.down.en);
                enterable.add(key.down.zh);
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (enterable.contains(s) || seen.contains(s)) {
                continue;
            }
            seen.add(s);
            out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    private KbdData() {
    }
}
