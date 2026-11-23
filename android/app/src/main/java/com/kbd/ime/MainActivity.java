package com.kbd.ime;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * KBD 入口：manpage 风格的完整帮助（方案、命令、手势、环境、诊断、支持）。
 * 字体统一 Unifont，字号只用 16 / 32 / 48；配色跟随键盘皮肤。
 */
public final class MainActivity extends Activity {

    private static final int DARK_BG = 0xFF000000;
    private static final int DARK_TEXT = 0xFFFFFFFF;
    private static final int DARK_SUB = 0xFF555555;

    private static final int LIGHT_BG = 0xFFF7F7F7;
    private static final int LIGHT_TEXT = 0xFF111111;
    private static final int LIGHT_SUB = 0xFF9E9E9E;

    private int bg;
    private int text;
    private int sub;
    private Typeface mono;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("kbd", MODE_PRIVATE);
        int theme = prefs.getInt("theme", KbdApp.THEME_AUTO);
        boolean dark = theme == KbdApp.THEME_DARK
                || (theme == KbdApp.THEME_AUTO && KbdUi.isNight(this));
        applyPalette(dark);

        mono = KbdUi.unifont(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, dp(24), pad, dp(32));
        scroll.addView(root);

        addHeader(root);
        addSection(root, "NAME", "kbd - 面向中文的 Android 输入法");
        addSection(root, "SYNOPSIS",
                "kbd [方案] [皮肤] [简繁]\n"
                        + "长按空格进入 \\，输入命令后按空格执行。");
        addSection(root, "DESCRIPTION",
                "KBD 按屏幕宽高自适应布局，竖屏与横屏共用一套 libime 拼音引擎。"
                        + "默认全拼，另提供 5 种 26 键双拼。\n\n"
                        + "键宽、字号、缝隙与外边距按整数像素推进，720×1604 与 1604×720 "
                        + "精确对齐基准设计。");
        addSection(root, "COMMANDS", commands());
        addSection(root, "GESTURES",
                "上滑 / 下滑      输入键上 / 下副字符\n"
                        + "长按修饰键       锁定 Ctrl / Alt / Shift\n"
                        + "长按删除         连发\n"
                        + "符号层上下滑     翻页\n"
                        + "候选区滚轮       滚动一行");
        addSection(root, "ENVIRONMENT",
                "方案、皮肤、简繁、底部安全区保存在应用私有偏好中；"
                        + "\\sb=auto 跟随系统底部 inset。");
        addSection(root, "FILES", "/data/data/com.kbd.ime/shared_prefs/kbd.xml");
        addDiagnostics(root);
        addSection(root, "SEE ALSO",
                "https://github.com/one18mb/kbd\n"
                        + "https://one18mb.github.io/kbd/");
        addSection(root, "LICENSE",
                "GPL-3.0-or-later。源代码与许可证见仓库。");
        addSupport(root);

        setContentView(scroll);
    }

    private void applyPalette(boolean dark) {
        bg = dark ? DARK_BG : LIGHT_BG;
        text = dark ? DARK_TEXT : LIGHT_TEXT;
        sub = dark ? DARK_SUB : LIGHT_SUB;

        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(bg));
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);

        if (Build.VERSION.SDK_INT >= 23) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (!dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    // ---------- 页面结构 ----------

    private void addHeader(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row);

        TextView title = new TextView(this);
        title.setText("KBD(1)");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        title.setTypeface(mono);
        title.setTextColor(text);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(title, lp);

        TextView version = new TextView(this);
        version.setText(versionName());
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        version.setTypeface(mono);
        version.setTextColor(sub);
        version.setIncludeFontPadding(false);
        row.addView(version);

        TextView subtitle = new TextView(this);
        subtitle.setText("KBD 输入法手册");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setTypeface(mono);
        subtitle.setTextColor(sub);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle);

        View rule = new View(this);
        rule.setBackgroundColor(sub);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        root.addView(rule, rlp);
    }

    private void addSection(LinearLayout root, String heading, String body) {
        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        h.setTypeface(mono);
        h.setTextColor(text);
        h.setIncludeFontPadding(false);
        h.setPadding(0, dp(24), 0, dp(8));
        root.addView(h);

        TextView b = new TextView(this);
        b.setText("    " + body.replace("\n", "\n    "));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(mono);
        b.setTextColor(text);
        b.setLineSpacing(dp(6), 1f);
        b.setTextIsSelectable(true);
        root.addView(b);
    }

    private void addDiagnostics(LinearLayout root) {
        TextView h = new TextView(this);
        h.setText("DIAGNOSTICS");
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        h.setTypeface(mono);
        h.setTextColor(text);
        h.setIncludeFontPadding(false);
        h.setPadding(0, dp(24), 0, dp(8));
        root.addView(h);

        TextView b = new TextView(this);
        b.setText("    " + engineReport());
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(mono);
        b.setTextColor(sub);
        b.setLineSpacing(dp(4), 1f);
        b.setTextIsSelectable(true);
        root.addView(b);
    }

    private void addSupport(LinearLayout root) {
        TextView h = new TextView(this);
        h.setText("SUPPORT");
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        h.setTypeface(mono);
        h.setTextColor(text);
        h.setIncludeFontPadding(false);
        h.setPadding(0, dp(24), 0, dp(8));
        root.addView(h);

        TextView line = new TextView(this);
        line.setText("    赞赏支持请扫码（微信）。自愿赠与，与功能、授权无关。");
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        line.setTypeface(mono);
        line.setTextColor(text);
        line.setLineSpacing(dp(6), 1f);
        line.setPadding(0, 0, 0, dp(16));
        root.addView(line);

        TextView art = new TextView(this);
        art.setText(grantsArt());
        art.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        art.setTypeface(mono);
        art.setTextColor(0xFF000000);
        art.setBackgroundColor(0xFFFFFFFF);
        art.setIncludeFontPadding(false);
        art.setLineSpacing(0f, 1f);
        art.setPadding(dp(8), dp(8), dp(8), dp(8));
        art.setHorizontallyScrolling(true);
        art.setTextIsSelectable(false);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setBackgroundColor(bg);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(art);
        root.addView(hsv);
    }

    // ---------- 内容 ----------

    private static String commands() {
        StringBuilder out = new StringBuilder();
        for (String code : KbdData.SCHEMES.keySet()) {
            out.append("\\zh=").append(code).append("\n");
            out.append("    ").append(KbdData.SCHEMES.get(code)).append('\n');
        }
        out.append("\\sk=dark|light|auto\n");
        out.append("    暗色 / 亮色 / 跟随系统\n");
        out.append("\\st=s|t\n");
        out.append("    简体 / 繁体\n");
        out.append("\\sb=32|auto\n");
        out.append("    底部安全区固定值 / 跟随系统\n");
        out.append("\\n  \\t  \\u4f60  \\\\\n");
        out.append("    换行 / 制表 / Unicode / 反斜杠");
        return out.toString();
    }

    private String engineReport() {
        StringBuilder out = new StringBuilder();
        out.append("应用 ").append(versionName())
                .append(" (").append(versionCode()).append(")\n");
        try {
            out.append(LibimeEngine.selfTest());
        } catch (Throwable t) {
            out.append("引擎未加载：").append(t);
        }
        return out.toString();
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    private int versionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 赞赏码的字符版：由原图按 8px 网格二值化，▀/▄/█ 两行并一行。
     * 微信赞赏码是装饰性码制，这里保留其外观，不保证通用扫码器可识别。
     */
    private static String grantsArt() {
        return ""
                + "              ▄  ▄  ▄  ▄\n"
                + "            █ ▀█    █ ▄  █\n"
                + "        ▄█ ▄ ▄ █ █▄ ▀▄▄ ▀▀▄██▄\n"
                + "     ▄ █████▄ █  ▄██▀▄ ▄ ▄█████\n"
                + "       ██▀▀ ▀▀▀█████▄█▄▀█ ▄▀▀██▀▀\n"
                + "    ▀▀▄▄▄ ▄▄                ▀▀▄██▀\n"
                + "   █▄▄▄ ▀▀▀▄              ▄█▀▀▄▄▄▄▀\n"
                + "  ▄▄▄ ▄▄▀▀██              █▀▀▀█▄▄▄▄\n"
                + "      ▄▀▄███              ██▄▀█▄▄\n"
                + "  ▀▀ ▀▀ ████              ██▄▄▀▀▀▀▀\n"
                + "   ▄   ▀▄▄▀▀              ▀ ▄▄▀▀▀▀▄\n"
                + "    ▄▄▀ █▄▄ ▄▄▄▄▄▄▄▄▄▄▄▄▄▄▀▀  ▀▀▀▄\n"
                + "      ▄██▄ ▀▄█▄▀████▀██ ▄\n"
                + "     ▀ █████  █ █▄████▄█▄\n"
                + "        ▀██▀▄▄ ▄ ▀▀  ▀█ ▄\n"
                + "         ▀  █ ▄█ █    █  ▄\n"
                + "              ▀  ▀  ▀  ▀";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
