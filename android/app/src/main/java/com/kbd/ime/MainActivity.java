package com.kbd.ime;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * 开发用宿主：显示引擎自检，并跑一次 输入→候选→选词 演示。
 * 词典与模型随 APK 打包（assets/engine），首次启动解压到 filesDir/engine。
 */
public final class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("KBD 输入法 · 引擎自检");
        title.setTextSize(24);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setGravity(Gravity.START);
        status.setPadding(0, 24, 0, 0);
        root.addView(status);

        setContentView(root);

        StringBuilder report = new StringBuilder();
        try {
            report.append(LibimeEngine.selfTest()).append("\n\n");
        } catch (Throwable t) {
            report.append("引擎加载失败：").append(t).append('\n');
            status.setText(report);
            return;
        }
        status.setText(report);

        new Thread(() -> {
            String result = runPipelineDemo();
            runOnUiThread(() -> status.setText(report.append(result)));
        }).start();
    }

    private String runPipelineDemo() {
        StringBuilder out = new StringBuilder();
        try {
            File dir = EngineData.ensure(this);
            LibimeEngine engine = new LibimeEngine();
            engine.load(dir.getAbsolutePath());
            out.append("引擎已加载：").append(dir.getAbsolutePath()).append('\n');

            for (char c : "nihc".toCharArray()) {
                engine.input(c);
            }
            EngineBridge.State state = engine.state(18);
            out.append("输入 nihc → preedit=[").append(state.preedit).append("]\n");
            if (state.candidates.length > 0) {
                out.append("首选：").append(state.candidates[0]).append('\n');
                String committed = engine.select(0);
                out.append("选0上屏：[").append(committed).append("]\n");
            } else {
                out.append("没有候选\n");
            }
            engine.saveUserDict();
        } catch (Throwable t) {
            out.append("联调失败：").append(t).append('\n');
        }
        return out.toString();
    }
}
