package com.akhilmulpuri.maximafutura;

import android.graphics.Path;
import android.graphics.RectF;

/** Palette and shape helpers. */
final class Hud {
    static final int BG = 0xFF040806;
    static final int GRID = 0x28143C28;
    static final int GREEN = 0xFF50FF96;
    static final int GREEN_DIM = 0xFF28784F;
    static final int TEXT = 0xFFD8F5E4;
    static final int LABEL = 0xFF6E9680;
    static final int FROST = 0x0F96FFC8;          // translucent fill
    static final int FROST_FOCUS = 0x2E50FF96;
    static final int EDGE = 0x4278FFAA;           // resting outline
    static final int SHEEN = 0x30E6FFF0;          // top highlight line
    static final int PLATE = 0xFF06110B;          // tile background

    /** Rectangle with cut corners. Bits: 1 top-left, 2 top-right, 4 bottom-right, 8 bottom-left. */
    static Path cut(RectF r, float c, int corners) {
        float tl = (corners & 1) != 0 ? c : 0, tr = (corners & 2) != 0 ? c : 0;
        float br = (corners & 4) != 0 ? c : 0, bl = (corners & 8) != 0 ? c : 0;
        Path p = new Path();
        p.moveTo(r.left + tl, r.top);
        p.lineTo(r.right - tr, r.top);
        p.lineTo(r.right, r.top + tr);
        p.lineTo(r.right, r.bottom - br);
        p.lineTo(r.right - br, r.bottom);
        p.lineTo(r.left + bl, r.bottom);
        p.lineTo(r.left, r.bottom - bl);
        p.lineTo(r.left, r.top + tl);
        p.close();
        return p;
    }

    /** Elongated hexagon: pointed left and right ends. */
    static Path hex(RectF r, float c) {
        float m = r.centerY();
        Path p = new Path();
        p.moveTo(r.left + c, r.top);
        p.lineTo(r.right - c, r.top);
        p.lineTo(r.right, m);
        p.lineTo(r.right - c, r.bottom);
        p.lineTo(r.left + c, r.bottom);
        p.lineTo(r.left, m);
        p.close();
        return p;
    }

    private Hud() {
    }
}
