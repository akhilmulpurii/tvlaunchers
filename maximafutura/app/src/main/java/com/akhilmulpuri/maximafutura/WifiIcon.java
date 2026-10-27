package com.akhilmulpuri.maximafutura;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Three arcs and a dot; lit arcs follow the signal level (0-3), the rest are dim. */
final class WifiIcon extends Drawable {
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float size;
    private int level = 3;
    private boolean wired;

    WifiIcon(float size, float stroke) {
        this.size = size;
        for (Paint p : new Paint[]{on, off}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        on.setColor(Hud.GREEN);
        off.setColor(Hud.GREEN_DIM);
        setBounds(0, 0, (int) size, (int) size);
    }

    void set(int level, boolean wired) {
        this.level = level;
        this.wired = wired;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        float cx = size / 2, cy = size * 0.85f;
        if (wired) {   // a simple plug-like square for ethernet
            c.drawRect(size * 0.25f, size * 0.3f, size * 0.75f, size * 0.8f, on);
            return;
        }
        for (int i = 1; i <= 3; i++) {
            float r = size * 0.24f * i;
            c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 225, 90, false, i <= level ? on : off);
        }
        on.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy - size * 0.02f, size * 0.07f, level > 0 ? on : off);
        on.setStyle(Paint.Style.STROKE);
    }

    @Override public int getIntrinsicWidth() { return (int) size; }
    @Override public int getIntrinsicHeight() { return (int) size; }
    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
