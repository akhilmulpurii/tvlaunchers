package com.akhilmulpuri.nerdlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Accent colour per app. Known apps come from a fixed map (brand colours, lifted where needed to read on the
 * dark background). Anything else is sampled once from its banner and cached until the app updates.
 */
final class AppColors {
    static final int NEUTRAL = 0xFFA9ADB5;
    private static final Map<String, Integer> KNOWN = new HashMap<>();

    static {
        // Video
        put(0xFFFF3040, "com.google.android.youtube.tv", "com.amazon.firetv.youtube", "com.google.android.youtube.tvkids");
        put(0xFFE50914, "com.netflix.ninja", "com.netflix.mediaclient");
        put(0xFF1FA8E8, "com.amazon.amazonvideo.livingroom", "com.amazon.firebat", "com.amazon.avod");
        put(0xFF1CE783, "com.hulu.plus", "com.hulu.livingroomplus");
        put(0xFF4D7CFF, "com.disney.disneyplus");
        put(0xFF5B7BFF, "com.wbd.stream", "com.hbo.hbonow", "com.hbo.max.android.tv");
        put(0xFFE8E8EA, "com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv");
        put(0xFFF2F2F2, "com.peacock.peacockfiretv", "com.peacocktv.peacockandroid");
        put(0xFFFFE14D, "com.tubitv.ott", "com.tubitv");
        put(0xFF4A90FF, "com.cbs.ott", "com.cbs.app", "com.paramount.android.pplus");
        put(0xFFF47521, "com.crunchyroll.crunchyroid");
        put(0xFFA970FF, "tv.twitch.android.app", "tv.twitch.android.viewer");
        put(0xFF2F80ED, "in.startv.hotstar", "in.startv.hotstar.dplus.tv");
        put(0xFFFF6B00, "ahaflix.tv");
        put(0xFFA64DFF, "com.zee5.amazon", "com.graymatrix.did");
        put(0xFF3A8DFF, "com.sonyliv", "com.sonyliv.androidtv");
        put(0xFF2DBE60, "com.cricbuzz.android", "com.cricbuzz.android.tv");
        put(0xFFFF4D5E, "app.cricfy.tv", "com.cricfy.tv", "com.cricfytv.sports");
        put(0xFF29B6F6, "com.mxtech.videoplayer.television");
        // Media servers and players
        put(0xFFE5A00D, "com.plexapp.android", "com.edde746.plezy");
        put(0xFFAA5CC3, "org.jellyfin.androidtv", "com.github.damontecres.wholphin");
        put(0xFF17B2E7, "org.xbmc.kodi");
        put(0xFFFF8800, "org.videolan.vlc");
        put(0xFF8BC34A, "com.brouken.player");
        // Music
        put(0xFF1DB954, "com.spotify.tv.android", "com.spotify.music");
        put(0xFFFF4E45, "com.google.android.youtube.tvmusic");
        put(0xFF25D1DA, "com.amazon.bueller.music");
        // System
        put(0xFF34A853, "com.android.vending");
        put(0xFF9AA0A6, "com.android.tv.settings", "com.google.android.tv.settings", "com.amazon.tv.settings.v2");
    }

    private static void put(int color, String... pkgs) {
        for (String p : pkgs) KNOWN.put(p, color);
    }

    private final PackageManager pm;
    private final SharedPreferences cache;

    AppColors(Context ctx) {
        pm = ctx.getPackageManager();
        cache = ctx.getSharedPreferences("colors", Context.MODE_PRIVATE);
    }

    /** The banner is only loaded when the colour has to be sampled (unknown app, not cached yet). */
    int forApp(String pkg, Supplier<Drawable> banner) {
        Integer known = KNOWN.get(pkg);
        if (known != null) return known;
        long stamp;
        try {
            stamp = pm.getPackageInfo(pkg, 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return NEUTRAL;
        }
        String key = pkg + "@" + stamp;
        int cached = cache.getInt(key, 0);
        if (cached != 0) return cached;
        Drawable d;
        try {
            d = banner.get();
        } catch (RuntimeException e) {
            d = null;
        }
        int c = sample(d);
        cache.edit().putInt(key, c).apply();
        return c;
    }

    /** Most saturated pixel of a 12x12 render, lifted to read on #14161B; greys fall back to NEUTRAL. */
    private static int sample(Drawable d) {
        if (d == null) return NEUTRAL;
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
        copy.setBounds(0, 0, n, n);
        copy.draw(new Canvas(bmp));
        float[] hsv = new float[3];
        float best = -1f;
        int pick = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * (0.35f + hsv[2]);
                if (score > best) {
                    best = score;
                    pick = p;
                }
            }
        }
        bmp.recycle();
        if (pick == 0) return NEUTRAL;
        Color.colorToHSV(pick, hsv);
        if (hsv[1] < 0.22f) return NEUTRAL;
        hsv[1] = Math.min(hsv[1], 0.85f);
        hsv[2] = Math.max(hsv[2], 0.88f);
        return Color.HSVToColor(hsv);
    }
}
