package com.akhilmulpuri.nerdlauncher;

import android.service.dreams.DreamService;

/** Screensaver: big clock and a few readouts, nudged around every minute against burn-in. */
public class NerdDream extends DreamService {
    private DreamView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes
        setFullscreen(true);
        setContentView(R.layout.dream);
        view = new DreamView(this, findViewById(android.R.id.content));
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        super.onDreamingStopped();
    }
}
