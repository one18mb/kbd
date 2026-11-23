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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * KBD 入口：manpage 风格的完整帮助（设置、用法、命令、手势、隐私、许可、支持）。
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
                "kbd\n"
                        + "无命令行参数。长按空格进入 \\，输入命令后按空格执行。");
        addSection(root, "DESCRIPTION",
                "KBD 是离线中文输入法。键盘按屏幕宽高自适应，竖屏与横屏共用"
                        + "一套 libime 拼音引擎；默认全拼，另提供小鹤、自然码、"
                        + "智能ABC、中文之星、国标 5 种 26 键双拼。");
        addSection(root, "SETUP",
                "1. 系统设置 → 语言与输入法 → 启用 KBD\n"
                        + "2. 将 KBD 设为当前输入法\n"
                        + "3. 在输入框中调出键盘\n"
                        + "如果键盘没有出现，回到第 1 步检查是否启用。");
        addSection(root, "USAGE",
                "中 / 英          切换中文、英文\n"
                        + "长按 中 / 英     打开符号层\n"
                        + "空格             组词时选首选，否则输入空格\n"
                        + "长按空格         进入 \\ 转义模式\n"
                        + "展开 / 返回      组词时展开或收起候选\n"
                        + "换行             点按换行，长按收起键盘\n"
                        + "删除             点按退格，长按连发\n"
                        + "候选             点按上屏；展开面板滚轮滚动");
        addSection(root, "COMMANDS", commands());
        addSection(root, "GESTURES",
                "上滑 / 下滑      输入键上 / 下副字符\n"
                        + "长按修饰键       锁定 Ctrl / Alt / Shift\n"
                        + "符号层上下滑     翻页");
        addSection(root, "PRIVACY",
                "不申请网络权限，不上传输入内容。方案、皮肤、简繁、底部安全区"
                        + "与自学习词库只保存在应用私有目录；卸载即清除。");
        addDiagnostics(root);
        addSection(root, "SEE ALSO",
                "源代码    https://github.com/one18mb/kbd\n"
                        + "问题反馈  https://github.com/one18mb/kbd/issues\n"
                        + "在线原型  https://one18mb.github.io/kbd/");
        addSection(root, "LICENSE",
                "GPL-3.0-or-later\n"
                        + "Copyright (C) 2026 one18mb and KBD contributors.\n"
                        + "第三方组件与字体许可见仓库 THIRD_PARTY_NOTICES.md。");
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
        line.setText("    赞赏支持：截屏保存后，用微信「扫一扫」从相册识别；"
                + "自愿赠与，与功能、授权无关。");
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        line.setTypeface(mono);
        line.setTextColor(text);
        line.setLineSpacing(dp(6), 1f);
        line.setPadding(0, 0, 0, dp(16));
        root.addView(line);

        ImageView qr = new ImageView(this);
        qr.setImageResource(R.drawable.grants_qr);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setBackgroundColor(0xFFFFFFFF);
        qr.setContentDescription("微信赞赏码");
        qr.setPadding(dp(8), dp(8), dp(8), dp(8));

        int size = Math.min(
                getResources().getDisplayMetrics().widthPixels - dp(64), dp(280));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(size, size);
        qlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(qr, qlp);
    }

    // ---------- 内容 ----------

    private static String commands() {
        return ""
                + "\\zh=quanpin            全拼（默认）\n"
                + "\\zh=xiaohe             小鹤双拼\n"
                + "\\zh=ziranma            自然码\n"
                + "\\zh=abc                智能ABC\n"
                + "\\zh=zhongwenzhixing    中文之星\n"
                + "\\zh=gb                 国标双拼\n"
                + "\n"
                + "\\sk=dark|light|auto\n"
                + "    暗色 / 亮色 / 跟随系统\n"
                + "\\st=s|t\n"
                + "    简体 / 繁体\n"
                + "\\sb=32|auto\n"
                + "    底部安全区固定值 / 跟随系统\n"
                + "\\n  \\t  \\u4f60  \\\\\n"
                + "    换行 / 制表 / Unicode / 反斜杠";
    }

    private String engineReport() {
        StringBuilder out = new StringBuilder();
        out.append("应用 ").append(versionName())
                .append(" (").append(versionCode()).append(")\n");
        try {
            out.append("引擎 ").append(LibimeEngine.version());
        } catch (Throwable t) {
            out.append("引擎未加载");
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
