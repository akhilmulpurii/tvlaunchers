package com.akhilmulpuri.maximafutura;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Static backdrop: green vignette, faint grid, and a fade on the right for the scrolling rail. */
public class HudFrameView extends View {
    private final float dp;
    private final Paint vignette = new Paint(Paint.DITHER_FLAG);
    private final Paint grid = new Paint();

    public HudFrameView(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        grid.setColor(Hud.GRID);
        setLayerType(LAYER_TYPE_HARDWARE, null);   // static: drawn once, then composited as one texture
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        vignette.setShader(new RadialGradient(w * 0.5f, h * 0.45f, Math.max(w, h) * 0.62f,
                new int[]{0xFF0A1C12, 0xFF07120C, Hud.BG}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, vignette);
        float step = 24 * dp;
        for (float x = 0; x < w; x += step) c.drawLine(x, 0, x, h, grid);
        for (float y = 0; y < h; y += step) c.drawLine(0, y, w, y, grid);
    }

    /** Right-edge fade laid over the rail so it reads as scrollable. */
    public static class Fade extends View {
        private final Paint p = new Paint(Paint.DITHER_FLAG);

        public Fade(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            p.setShader(new LinearGradient(0, 0, w, 0, 0x00050A07, 0xFF050A07, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), p);
        }
    }
}
