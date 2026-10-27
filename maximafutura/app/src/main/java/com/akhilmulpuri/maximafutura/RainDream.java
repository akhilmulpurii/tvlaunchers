package com.akhilmulpuri.maximafutura;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;

/** Screensaver: matrix rain behind a frosted clock plate. Any key wakes. */
public class RainDream extends DreamService {
    private Screen screen;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setContentView(R.layout.dream);
        screen = new Screen(this, findViewById(android.R.id.content));
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        screen.start();
    }

    @Override
    public void onDreamingStopped() {
        screen.stop();
        super.onDreamingStopped();
    }

    /** Shared by the dream and the debug preview. */
    static final class Screen {
        private final Context ctx;
        private final MatrixRain rain;
        private final View plate;
        private final TextView date;
        private final Random rnd = new Random();
        private final BroadcastReceiver tick = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                update();
                drift();
            }
        };

        Screen(Context ctx, View root) {
            this.ctx = ctx;
            rain = root.findViewById(R.id.rain);
            plate = root.findViewById(R.id.plate);
            date = root.findViewById(R.id.dreamDate);
            float dp = ctx.getResources().getDisplayMetrics().density;
            plate.setBackground(new FrostDrawable(FrostDrawable.HEX, 30 * dp, 0xE6030A06, 0xAA50FF96, dp, false, dp));
        }

        void start() {
            update();
            IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            ctx.registerReceiver(tick, f);
            rain.start();
        }

        void stop() {
            rain.stop();
            ctx.unregisterReceiver(tick);
        }

        private void update() {
            date.setText(new SimpleDateFormat("EEEE  ·  dd MMM yyyy", Locale.getDefault())
                    .format(System.currentTimeMillis()));
        }

        private void drift() {
            View root = (View) plate.getParent();
            plate.animate().translationX((rnd.nextFloat() * 2 - 1) * root.getWidth() * 0.08f)
                    .translationY((rnd.nextFloat() * 2 - 1) * root.getHeight() * 0.08f).setDuration(2000).start();
        }
    }
}
