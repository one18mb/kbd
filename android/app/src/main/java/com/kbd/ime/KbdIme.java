package com.kbd.ime;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.PopupWindow;

import java.io.File;

/**
 * 输入法生命周期与宿主桥接。
 *
 * 拼音不写入应用输入框，也不画在键盘里：由键盘上方的浮层显示（机制同系统输入法），
 * 样式按 webdemo .floating-preedit（Unifont 32px、候选条同色、无圆角、间隔 4px），
 * 键盘高度恒为 496。
 */
public final class KbdIme extends InputMethodService implements KbdApp.Host {

    private final LibimeEngine engine = new LibimeEngine();
    private final Handler main = new Handler(Looper.getMainLooper());

    private KbdApp app;
    private KbdInputView view;

    private PopupWindow preedit;
    private KbdPreeditView preeditView;
    private android.icu.text.Transliterator traditional;

    @Override
    public void onCreate() {
        super.onCreate();
        app = new KbdApp(this);
        android.content.SharedPreferences prefs = getSharedPreferences("kbd", MODE_PRIVATE);
        app.theme = prefs.getInt("theme", KbdApp.THEME_AUTO);
        app.variant = prefs.getInt("variant", KbdApp.VARIANT_SIMPLE);
        app.symbolRow = prefs.getInt("symbolRow", 0);
        app.restoreBottomSafe(prefs.getInt("bottomSafe", -1));
        loadEngine();
    }

    private void loadEngine() {
        new Thread(() -> {
            String error = null;
            try {
                String scheme = getSharedPreferences("kbd", MODE_PRIVATE)
                        .getString("scheme", "quanpin");
                engine.setScheme(scheme);
                File dir = EngineData.ensure(KbdIme.this);
                File user = EngineData.userDir(KbdIme.this);
                engine.load(dir.getAbsolutePath(), user.getAbsolutePath());
                engine.setScheme(getSharedPreferences("kbd", MODE_PRIVATE)
                        .getString("scheme", "quanpin"));
                if (!engine.isReady()) {
                    error = "引擎未就绪";
                }
            } catch (Throwable t) {
                error = String.valueOf(t);
            }
            final String message = error;
            main.post(() -> {
                if (message == null) {
                    app.setEngine(engine);
                } else {
                    app.setStatus(message);
                }
            });
        }, "kbd-engine-load").start();
    }

    @Override
    public View onCreateInputView() {
        if (view == null) {
            view = new KbdInputView(this, app);
        }
        app.setSystemBottomSafe(systemBottomInset());
        return view;
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        app.setSystemBottomSafe(systemBottomInset());
        app.restartComposition();
    }

    /** 系统底部安全区参考值（导航栏/手势条）。 */
    private int systemBottomInset() {
        try {
            android.view.WindowInsets insets = getWindow().getWindow()
                    .getDecorView().getRootWindowInsets();
            if (insets != null) {
                return insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.systemGestures()).bottom;
            }
        } catch (Throwable ignored) {
        }
        return app.systemBottomSafe;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        hideComposing();
        engine.saveUserDict();
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        hideComposing();
        engine.saveUserDict();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideComposing();
        app.setSystemBottomSafe(systemBottomInset());
        if (view != null) {
            view.refreshLayout();
        }
    }

    // ---------- KbdApp.Host ----------

    @Override
    public void commitText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && text != null && !text.isEmpty()) {
            ic.commitText(text, 1);
        }
    }

    @Override
    public void setComposing(String text) {
        if (text == null || text.isEmpty()) {
            hideComposing();
            return;
        }
        KbdInputView v = view;
        if (v == null || v.layout() == null || !v.isShown() || v.getWindowToken() == null) {
            return;
        }
        KbdLayout layout = v.layout();
        boolean night = app.isDark(KbdUi.isNight(this));

        if (preeditView == null) {
            preeditView = new KbdPreeditView(this);
            preedit = new PopupWindow(preeditView);
            preedit.setFocusable(false);
            preedit.setTouchable(false);
            preedit.setOutsideTouchable(false);
            preedit.setClippingEnabled(false);
            preedit.setBackgroundDrawable(null);
            preedit.setElevation(0);
            preedit.setAnimationStyle(0);
        }

        preeditView.configure(night, layout);
        preeditView.setContent(text);

        int width = Math.min(layout.preeditWidth, preeditView.widthFor(text));
        int height = layout.preeditH + layout.preeditGap;
        int x = layout.preeditLeft;
        // 浮层底边（含 4px 补满）贴键盘上沿：自输入视图左下角向上偏移
        int y = -(v.getHeight() + height);

        if (preedit.isShowing()) {
            preedit.update(v, x, y, width, height);
        } else {
            preedit.setWidth(width);
            preedit.setHeight(height);
            preedit.showAsDropDown(v, x, y);
        }
    }

    @Override
    public void finishComposing() {
        hideComposing();
    }

    private void hideComposing() {
        if (preedit != null && preedit.isShowing()) {
            preedit.dismiss();
        }
    }

    @Override
    public void sendKey(int keyCode, int metaState) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        ic.sendKeyEvent(new android.view.KeyEvent(now, now,
                android.view.KeyEvent.ACTION_DOWN, keyCode, 0, metaState));
        ic.sendKeyEvent(new android.view.KeyEvent(now, now,
                android.view.KeyEvent.ACTION_UP, keyCode, 0, metaState));
    }

    @Override
    public String toTraditional(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            if (traditional == null) {
                traditional = android.icu.text.Transliterator.getInstance("Hans-Hant");
            }
            return traditional.transliterate(text);
        } catch (Throwable t) {
            return text;
        }
    }

    @Override
    public void onSettingsChanged(int theme, int variant) {
        getSharedPreferences("kbd", MODE_PRIVATE).edit()
                .putInt("theme", theme)
                .putInt("variant", variant)
                .apply();
    }

    @Override
    public void onSymbolRowChanged(int row) {
        getSharedPreferences("kbd", MODE_PRIVATE).edit()
                .putInt("symbolRow", row)
                .apply();
    }

    @Override
    public void onBottomSafeChanged(int b) {
        getSharedPreferences("kbd", MODE_PRIVATE).edit()
                .putInt("bottomSafe", b)
                .apply();
    }

    @Override
    public void onSchemeChanged(String scheme) {
        getSharedPreferences("kbd", MODE_PRIVATE).edit()
                .putString("scheme", scheme)
                .apply();
    }

    @Override
    public void onLayoutChanged() {
        if (view != null) {
            view.refreshLayout();
        }
    }

    @Override
    public void hideKeyboard() {
        requestHideSelf(0);
    }

    @Override
    public void invalidate() {
        if (view != null) {
            view.invalidate();
        }
    }
}
