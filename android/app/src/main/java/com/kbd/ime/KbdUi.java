package com.kbd.ime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;

/** Android 外观适配：夜间判定与 Unifont 加载（键盘、拼音浮层、IME 共用）。 */
final class KbdUi {

    private KbdUi() {
    }

    static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static Typeface unifont(Context context) {
        try {
            return Typeface.createFromAsset(context.getAssets(), "fonts/unifont.otf");
        } catch (Throwable t) {
            return Typeface.MONOSPACE;
        }
    }
}
