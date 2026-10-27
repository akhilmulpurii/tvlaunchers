package com.akhilmulpuri.zunesthetic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.ViewGroup;
import android.view.Choreographer;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;

/** Screensaver: drifting wallpaper, giant faint words scrolling past, lock-screen clock. */
public class ZuneDream extends DreamService {
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

    /** Shared by the dream and the debug preview. Motion runs on one ~30 fps frame loop. */
    static final class Screen implements Choreographer.FrameCallback {
        private static final long FRAME_NS = 33_000_000L;
        private final Context ctx;
        private final View root, photo, clock;
        private final TextView[] words = new TextView[2];
        private final float[] wFrom = new float[2], wTo = new float[2];
        private final long[] wStart = new long[2], wDur = new long[2];
        private final float[] speed = {26, 44};   // dp per second
        private final float[] pFrom = new float[3], pTo = new float[3];   // scale, x, y
        private long pStart, last;
        private final TextView date;
        private final Random rnd = new Random();
        private final float dp;
        private boolean running, zoomIn;
        private final BroadcastReceiver tick = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                update();
                nudge();
            }
        };

        Screen(Context ctx, View root) {
            this.ctx = ctx;
            this.root = root;
            dp = ctx.getResources().getDisplayMetrics().density;
            photo = root.findViewById(R.id.dreamPhoto);
            clock = root.findViewById(R.id.dreamClock);
            words[0] = root.findViewById(R.id.wordBig);
            words[1] = root.findViewById(R.id.wordSmall);
            date = root.findViewById(R.id.dreamDate);
            // Dim baked into the photo's texture instead of a full-screen overlay.
            ((ImageView) photo).setColorFilter(0x66000000, PorterDuff.Mode.SRC_ATOP);
            for (View v : new View[]{photo, words[0], words[1]}) v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        void start() {
            running = true;
            update();
            IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            ctx.registerReceiver(tick, f);
            root.post(() -> {
                long now = System.nanoTime();
                pTo[0] = photo.getScaleX();
                nextPan(now);
                nextWord(0, now);
                nextWord(1, now);
                last = 0;
                Choreographer.getInstance().postFrameCallback(this);
            });
        }

        void stop() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
            ctx.unregisterReceiver(tick);
        }

        @Override
        public void doFrame(long now) {
            if (!running) return;
            if (now - last >= FRAME_NS) {
                last = now;
                float t = (now - pStart) / 45e9f;
                if (t >= 1) {
                    nextPan(now);
                    t = 0;
                }
                float s = lerp(pFrom[0], pTo[0], t);
                photo.setScaleX(s);
                photo.setScaleY(s);
                photo.setTranslationX(lerp(pFrom[1], pTo[1], t));
                photo.setTranslationY(lerp(pFrom[2], pTo[2], t));
                for (int i = 0; i < 2; i++) {
                    float u = (now - wStart[i]) / (float) wDur[i];
                    if (u >= 1) {
                        nextWord(i, now);
                        u = 0;
                    }
                    words[i].setTranslationX(lerp(wFrom[i], wTo[i], u));
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private String fmt(String p) {
            return new SimpleDateFormat(p, Locale.getDefault()).format(System.currentTimeMillis())
                    .toLowerCase(Locale.getDefault());
        }

        private void update() {
            date.setText(fmt("EEEE, d MMMM"));
        }

        /** Next leg of the slow zoom and pan, alternating in and out. */
        private void nextPan(long now) {
            System.arraycopy(pTo, 0, pFrom, 0, 3);
            if (pStart == 0) {
                pFrom[1] = photo.getTranslationX();
                pFrom[2] = photo.getTranslationY();
            }
            zoomIn = !zoomIn;
            float s = zoomIn ? 1.16f : 1.04f, range = (s - 1) / 2;
            pTo[0] = s;
            pTo[1] = (rnd.nextFloat() * 2 - 1) * range * root.getWidth();
            pTo[2] = (rnd.nextFloat() * 2 - 1) * range * root.getHeight();
            pStart = now;
        }

        /** Picks a word and sends it across: big one right to left, small one left to right. */
        private void nextWord(int i, long now) {
            TextView v = words[i];
            String[] pool = i == 0
                    ? new String[]{fmt("EEEE"), fmt("MMMM")}
                    : new String[]{fmt("MMMM"), fmt("EEEE")};
            v.setText(pool[rnd.nextInt(pool.length)]);
            int w = (int) Math.ceil(v.getPaint().measureText(v.getText().toString())) + v.getPaddingLeft() + v.getPaddingRight();
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.width = w;
            v.setLayoutParams(lp);
            wFrom[i] = i == 0 ? root.getWidth() : -w;
            wTo[i] = i == 0 ? -w : root.getWidth();
            wStart[i] = now;
            wDur[i] = (long) (Math.abs(wTo[i] - wFrom[i]) / (speed[i] * dp) * 1e9);
            v.setTranslationX(wFrom[i]);
        }

        /** Shifts the clock a little each minute so nothing sits still on the panel. */
        private void nudge() {
            clock.animate()
                    .translationX(rnd.nextFloat() * 40 * dp)
                    .translationY(-rnd.nextFloat() * 30 * dp)
                    .setDuration(1500).start();
        }
    }
}
