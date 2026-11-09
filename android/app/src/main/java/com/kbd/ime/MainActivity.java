package com.kbd.ime;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * KBD 入口：显示完整帮助（输入方案、底部安全区、主题/简繁、转义、手势）。
 * 引擎自检放在页尾，方便确认 libime 内置双拼数量。
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 40, 48, 48);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("KBD 输入法");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        String scheme = getSharedPreferences("kbd", MODE_PRIVATE)
                .getString("scheme", "quanpin");
        TextView current = new TextView(this);
        current.setText("当前方案：" + KbdData.schemeLabel(scheme));
        current.setTextSize(16);
        current.setPadding(0, 8, 0, 20);
        root.addView(current);

        TextView help = new TextView(this);
        help.setText(helpText());
        help.setTextSize(15);
        help.setTypeface(Typeface.MONOSPACE);
        help.setLineSpacing(6f, 1f);
        help.setTextIsSelectable(true);
        help.setGravity(Gravity.START);
        root.addView(help);

        TextView engine = new TextView(this);
        engine.setTextSize(12);
        engine.setTypeface(Typeface.MONOSPACE);
        engine.setPadding(0, 28, 0, 0);
        try {
            engine.setText(LibimeEngine.selfTest());
        } catch (Throwable t) {
            engine.setText("引擎：未加载\n" + t);
        }
        root.addView(engine);

        setContentView(scroll);
    }

    private static String helpText() {
        StringBuilder out = new StringBuilder();
        out.append("输入方案（\\zh=）\n");
        for (String code : KbdData.SCHEMES.keySet()) {
            out.append(String.format(java.util.Locale.ROOT, "  %-22s %s\n",
                    "\\zh=" + code, KbdData.SCHEMES.get(code)));
        }
        out.append('\n');
        out.append("底部安全区\n");
        out.append("  \\sb=32                固定 32px\n");
        out.append("  \\sb=auto              跟随系统\n");
        out.append('\n');
        out.append("皮肤与简繁\n");
        out.append("  \\sk=dark             暗色\n");
        out.append("  \\sk=light            亮色\n");
        out.append("  \\sk=auto             跟随系统\n");
        out.append("  \\st=s                简体\n");
        out.append("  \\st=t                繁体\n");
        out.append('\n');
        out.append("转义\n");
        out.append("  长按空格进入 \\；确认执行，删除退格，Esc 退出\n");
        out.append("  \\n 换行    \\t 制表    \\u4f60 Unicode    \\\\ 反斜杠\n");
        out.append('\n');
        out.append("本页：在系统桌面点击 KBD 应用打开。\n");
        out.append('\n');
        out.append("手势\n");
        out.append("  上滑/下滑：键上副字符\n");
        out.append("  长按修饰键：锁定\n");
        out.append("  长按删除：连发\n");
        out.append("  符号层上下滑：翻页\n");
        return out.toString();
    }
}
