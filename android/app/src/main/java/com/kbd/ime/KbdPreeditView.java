package com.kbd.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * 拼音浮层，对应 webdemo 的 .floating-preedit：
 * 键盘外、文字宽的 32px 块，Unifont 32px、候选条同色底、左右 2px 内边距、无圆角；
 * 与键盘上沿隔 4px，用同色补满，看起来和候选行连成一块。
 * 只绘制，不持有状态。
 */
final class KbdPreeditView extends View {

    private final Paint fill = new Paint();
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String content = "";
    private int pad;
    private int chipH;
    private int gap;

    KbdPreeditView(Context context) {
        super(context);
        ink.setTypeface(KbdUi.unifont(context));
    }

    void configure(boolean dark, KbdLayout layout) {
        fill.setColor(dark ? 0xFF000000 : 0xFFF7F7F7);
        ink.setColor(dark ? 0xFFFFFFFF : 0xFF111111);
        ink.setTextSize(layout.preeditFont);
        pad = layout.preeditPad;
        chipH = layout.preeditH;
        gap = layout.preeditGap;
    }

    void setContent(String text) {
        content = text == null ? "" : text;
        invalidate();
    }

    /** 浮层宽度 = 文字宽 + 两侧 2px 内边距。 */
    int widthFor(String text) {
        return Math.round(ink.measureText(text)) + 2 * pad;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        fill.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, w, chipH, fill);
        canvas.drawRect(0, chipH, w, chipH + gap, fill);

        String label = ellipsize(content, w - 2 * pad);
        Paint.FontMetrics fm = ink.getFontMetrics();
        float baseline = (chipH - (fm.ascent + fm.descent)) / 2f;
        canvas.drawText(label, pad, baseline, ink);
    }

    private String ellipsize(String text, int maxWidth) {
        if (maxWidth <= 0 || ink.measureText(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        float avail = maxWidth - ink.measureText(ellipsis);
        int end = text.length();
        while (end > 0 && ink.measureText(text.substring(0, end)) > avail) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
