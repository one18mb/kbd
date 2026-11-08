package com.kbd.ime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/**
 * Android 适配层：Canvas 渲染 + 触摸转发。
 * 不含任何业务规则；几何、手势、状态分别由 KbdLayout / KbdGestures / KbdApp 负责。
 */
final class KbdInputView extends View implements KbdRenderer.Sink, KbdGestures.Timer {

    private final KbdApp app;
    private final KbdRenderer renderer = new KbdRenderer();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Paint rectPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint measurePaint = new Paint();
    private Typeface typeface = Typeface.MONOSPACE;

    private KbdLayout layout;
    private KbdGestures gestures;
    private Canvas canvasRef;
    private boolean dirty = true;

    private final Runnable longPressRun = new Runnable() {
        @Override
        public void run() {
            gestures.longPressDue();
            invalidate();
        }
    };

    private final Runnable repeatRun = new Runnable() {
        @Override
        public void run() {
            gestures.repeatDue();
            invalidate();
            handler.postDelayed(this, KbdGestures.REPEAT_MS);
        }
    };

    KbdInputView(Context context, KbdApp app) {
        super(context);
        this.app = app;
        typeface = KbdUi.unifont(context);
        setFocusable(true);
        setClickable(true);
        applyPalette();
    }

    private void applyPalette() {
        renderer.setDark(KbdUi.isNight(getContext()));
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyPalette();
        refreshOrientation();
    }

    /** 屏幕方向可能变化：下次测量重建布局。 */
    void refreshOrientation() {
        dirty = true;
        requestLayout();
        invalidate();
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int widthHint(int spec) {
        int w = MeasureSpec.getSize(spec);
        return w > 0 ? w : getResources().getDisplayMetrics().widthPixels;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = widthHint(widthMeasureSpec);
        rebuild(width);
        setMeasuredDimension(width, layout.totalH);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild(w);
    }

    private void rebuild(int width) {
        boolean landscape = isLandscape();
        if (!dirty && layout != null && layout.width == width
                && layout.landscape == landscape) {
            return;
        }
        layout = new KbdLayout(width, landscape);
        gestures = new KbdGestures(layout, app, this);
        app.setLandscape(landscape);
        dirty = false;
    }

    KbdLayout layout() {
        return layout;
    }

    // ---------- 绘制 ----------

    @Override
    protected void onDraw(Canvas canvas) {
        if (layout == null) {
            return;
        }
        canvasRef = canvas;
        renderer.setDark(app.isDark(KbdUi.isNight(getContext())));
        renderer.render(this, layout, app, gestures.pressed());
        canvasRef = null;
    }

    @Override
    public void fillRect(float l, float t, float r, float b, int argb) {
        if (canvasRef == null) {
            return;
        }
        rectPaint.setColor(argb);
        rectPaint.setStyle(Paint.Style.FILL);
        canvasRef.drawRect(l, t, r, b, rectPaint);
    }

    @Override
    public void text(String text, float cx, float cy, float size, int argb,
                     boolean centered, float maxWidth) {
        if (canvasRef == null || text == null || text.isEmpty()) {
            return;
        }
        textPaint.setTypeface(typeface);
        textPaint.setTextSize(size);
        textPaint.setColor(argb);
        textPaint.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
        String label = ellipsize(text, size, maxWidth);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        canvasRef.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
    }

    private float textWidth(String text, float size) {
        measurePaint.setTypeface(typeface);
        measurePaint.setTextSize(size);
        return measurePaint.measureText(text);
    }

    private String ellipsize(String text, float size, float maxWidth) {
        if (maxWidth <= 0 || textWidth(text, size) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        float avail = maxWidth - textWidth(ellipsis, size);
        int end = text.length();
        while (end > 0 && textWidth(text.substring(0, end), size) > avail) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    // ---------- 触摸 ----------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestures == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!gestures.down(event.getX(), event.getY())) {
                    return false;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                gestures.move(event.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                gestures.up(event.getX(), event.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                gestures.cancel();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    // ---------- KbdGestures.Timer ----------

    @Override
    public void armLongPress() {
        handler.postDelayed(longPressRun, KbdGestures.LONG_PRESS_MS);
    }

    @Override
    public void armRepeat() {
        handler.postDelayed(repeatRun, KbdGestures.REPEAT_MS);
    }

    @Override
    public void cancel() {
        handler.removeCallbacks(longPressRun);
        handler.removeCallbacks(repeatRun);
    }
}
