package com.akhilmulpuri.maximafutura;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Frosted HUD shape: translucent fill, thin outline, a sheen along the top edge, optional glow.
 * Shape is a hexagon (tabs, plates) or a cut-corner rectangle (tiles). Can mask outside the shape.
 */
final class FrostDrawable extends Drawable {
    static final int HEX = 0, CUT = 1;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mask = new Paint();
    private final int shape;
    private final float c, w, inset;
    private final boolean glowing;
    private int maskColor;
    private Path path;
    private float sheenL, sheenR, sheenY;

    FrostDrawable(int shape, float cut, int fillColor, int strokeColor, float strokeW, boolean glowing, float dp) {
        this.shape = shape;
        this.c = cut;
        this.w = strokeW;
        this.glowing = glowing;
        this.inset = strokeW / 2;   // glow spills outside; parents don't clip
        fill.setColor(fillColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeW);
        stroke.setColor(strokeColor);
        sheen.setStrokeWidth(dp);
        sheen.setColor(Hud.SHEEN);
        glow.setStyle(Paint.Style.STROKE);
        glow.setColor((strokeColor & 0x00FFFFFF) | 0x22000000);
    }

    FrostDrawable mask(int color) {
        maskColor = color;
        mask.setColor(color);
        return this;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        RectF r = new RectF(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        path = shape == HEX ? Hud.hex(r, c) : Hud.cut(r, c, 1 | 4);
        sheenL = r.left + c + w * 2;
        sheenR = r.right - (shape == HEX ? c : 0) - w * 2;
        sheenY = r.top + w * 1.5f;
    }

    @Override
    public void draw(Canvas cv) {
        if (path == null) return;
        if (maskColor != 0) {
            cv.save();
            cv.clipOutPath(path);
            cv.drawRect(getBounds(), mask);
            cv.restore();
        }
        if (glowing) {
            for (int i = 3; i >= 1; i--) {
                glow.setStrokeWidth(w * (1 + i * 2.5f));
                cv.drawPath(path, glow);
            }
        }
        cv.drawPath(path, fill);
        cv.drawLine(sheenL, sheenY, sheenR, sheenY, sheen);
        cv.drawPath(path, stroke);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
