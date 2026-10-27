package com.akhilmulpuri.nerdlauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** Debug builds only: shows the screensaver so it can be checked over adb. Any key closes it. */
public class DreamPreviewActivity extends Activity {
    private DreamView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.dream);
        view = new DreamView(this, findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
    }

    @Override
    protected void onPause() {
        view.stop();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }
}
