package com.akhilmulpuri.nerdlauncher;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/** The screensaver's content (layout/dream.xml): readouts, blinking cursor, drift each minute. */
final class DreamView {
    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View block, cursor;
    private final TextView badge, date, info;
    private final int yellow;

    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            cursor.setVisibility(cursor.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            handler.postDelayed(this, 600);
        }
    };

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            update();
            drift();
        }
    };

    DreamView(Context ctx, View root) {
        this.ctx = ctx;
        block = root.findViewById(R.id.block);
        cursor = root.findViewById(R.id.cursor);
        badge = root.findViewById(R.id.badge);
        date = root.findViewById(R.id.date);
        info = root.findViewById(R.id.info);
        yellow = ctx.getColor(R.color.yellow);
    }

    void start() {
        update();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ctx.registerReceiver(tick, f);
        handler.post(blink);
    }

    void stop() {
        handler.removeCallbacks(blink);
        ctx.unregisterReceiver(tick);
    }

    private void update() {
        Locale l = Locale.getDefault();
        Calendar cal = Calendar.getInstance();
        date.setText(new SimpleDateFormat("EEE dd MMM yyyy", l).format(cal.getTime())
                + String.format(l, " · WK %02d", cal.get(Calendar.WEEK_OF_YEAR)));
        long up = SystemClock.elapsedRealtime() / 60000;
        long d = up / (60 * 24), h = (up / 60) % 24, m = up % 60;
        badge.setText("UPTIME " + (d > 0 ? d + "D " + h + "H" : h > 0 ? h + "H " + m + "M" : m + "M"));

        ActivityManager am = ctx.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        long free = new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
        SpannableStringBuilder s = new SpannableStringBuilder();
        pair(s, "RAM: ", mi.availMem / (1024 * 1024) + " MB FREE");
        s.append("   /   ");
        pair(s, "STORAGE: ", String.format(Locale.ROOT, "%.1f GB FREE", free / (1024.0 * 1024 * 1024)));
        info.setText(s);
    }

    private void pair(SpannableStringBuilder s, String key, String value) {
        s.append(key);
        int start = s.length();
        s.append(value);
        s.setSpan(new ForegroundColorSpan(yellow), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Moves the block up to ~6% of the screen in any direction (burn-in protection). */
    void drift() {
        View root = (View) block.getParent();
        float maxX = root.getWidth() * 0.06f, maxY = root.getHeight() * 0.06f;
        block.animate()
                .translationX((random.nextFloat() * 2 - 1) * maxX)
                .translationY((random.nextFloat() * 2 - 1) * maxY)
                .setDuration(1500).start();
    }
}
