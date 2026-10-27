package com.akhilmulpuri.maximafutura;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.util.Random;

/**
 * cmatrix-style digital rain, about 15 frames a second, only between start() and stop().
 * Glyphs are rendered once into an atlas; each frame copies cells from it, grouped by brightness,
 * so there is no text layout per frame and the GPU can batch the copies.
 */
public class MatrixRain extends View implements Choreographer.FrameCallback {
    private static final long FRAME_NS = 66_000_000L;
    private static final String KANA = "ｦｧｨｩｪｫｬｭｮｯｰｱｲ"
            + "ｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃ"
            + "ﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔ"
            + "ﾕﾖﾗﾘﾙﾚﾛﾜﾝ";
    private static final String ASCII = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ:.=*+-<>";
    private static final int SHADES = 10;   // 0 = head, 1.. = trail, brightest to faintest

    private final Random rnd = new Random();
    private final float dp;
    private final Paint[] shade = new Paint[SHADES];
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private String glyphs;
    private Bitmap atlas;
    private int cw, ch, cols, rows;
    private float[] y, speed;
    private int[] len;
    private byte[][] grid;
    private boolean running;
    private long last;

    public MatrixRain(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < SHADES; i++) {
            int color;
            if (i == 0) {
                color = 0xFFE1FFEB;
            } else {
                float k = (float) Math.pow(1 - (i - 1) / (float) (SHADES - 1), 1.5);
                color = 0xFF000000 | ((int) (12 + 68 * k) << 16) | ((int) (40 + 215 * k) << 8) | (int) (24 + 126 * k);
            }
            shade[i] = new Paint(Paint.FILTER_BITMAP_FLAG);
            shade[i].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cw = Math.round(18 * dp);
        ch = Math.round(19 * dp);
        cols = w / cw + 1;
        rows = h / ch + 2;
        buildAtlas();
        y = new float[cols];
        speed = new float[cols];
        len = new int[cols];
        grid = new byte[cols][rows];
        for (int c = 0; c < cols; c++) {
            reset(c, true);
            for (int r = 0; r < rows; r++) grid[c][r] = (byte) rnd.nextInt(glyphs.length());
        }
    }

    /** One row of white glyphs on transparent; half-width katakana if the TV's fonts have them. */
    private void buildAtlas() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(15 * dp);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFFFFFF);
        glyphs = p.hasGlyph("ｱ") ? KANA + "0123456789" : ASCII;
        if (atlas != null) atlas.recycle();
        atlas = Bitmap.createBitmap(cw * glyphs.length(), ch, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(atlas);
        Paint.FontMetrics fm = p.getFontMetrics();
        float base = ch / 2f - (fm.ascent + fm.descent) / 2;
        for (int i = 0; i < glyphs.length(); i++) c.drawText(glyphs, i, i + 1, i * cw + cw / 2f, base, p);
        atlas.prepareToDraw();
    }

    private void reset(int c, boolean anywhere) {
        y[c] = anywhere ? rnd.nextInt(rows * 2) - rows : -rnd.nextInt(rows * 2);   // gaps between drops
        speed[c] = 0.25f + rnd.nextFloat() * 0.6f;
        len[c] = 6 + rnd.nextInt(14);
    }

    void start() {
        running = true;
        last = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    public void doFrame(long now) {
        if (!running) return;
        if (now - last >= FRAME_NS && grid != null) {
            last = now;
            for (int c = 0; c < cols; c++) {
                y[c] += speed[c];
                if (y[c] - len[c] > rows) reset(c, false);
                if (rnd.nextInt(6) == 0) grid[c][rnd.nextInt(rows)] = (byte) rnd.nextInt(glyphs.length());
            }
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (grid == null) return;
        // Brightness level by level, so consecutive copies share a paint and batch together.
        for (int s = 0; s < SHADES; s++) {
            Paint p = shade[s];
            for (int c = 0; c < cols; c++) {
                int headRow = (int) y[c], n = len[c];
                int k0, k1;   // trail offsets behind the head that fall in this level
                if (s == 0) {
                    k0 = 0;
                    k1 = 0;
                } else {
                    k0 = 1 + (s - 1) * (n - 1) / (SHADES - 1);
                    k1 = s * (n - 1) / (SHADES - 1);
                }
                for (int k = k0; k <= k1; k++) {
                    int r = headRow - k;
                    if (r < 0 || r >= rows) continue;
                    int g = grid[c][r];
                    src.set(g * cw, 0, g * cw + cw, ch);
                    dst.set(c * cw, r * ch, c * cw + cw, r * ch + ch);
                    canvas.drawBitmap(atlas, src, dst, p);
                }
            }
        }
    }
}
