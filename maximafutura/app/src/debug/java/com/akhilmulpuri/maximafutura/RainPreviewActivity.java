package com.akhilmulpuri.maximafutura;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** Debug builds only: shows the screensaver over adb. Any key closes it. */
public class RainPreviewActivity extends Activity {
    private RainDream.Screen screen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.dream);
        screen = new RainDream.Screen(this, findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        screen.start();
    }

    @Override
    protected void onPause() {
        screen.stop();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }
}
