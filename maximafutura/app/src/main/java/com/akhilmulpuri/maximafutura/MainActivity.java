package com.akhilmulpuri.maximafutura;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Maxima Futura: a minimal green HUD home for TVs. */
public class MainActivity extends Activity {
    private static final long DAY = 24L * 60 * 60 * 1000;

    static final class App {
        final String pkg, label;
        final ComponentName cn;

        App(String pkg, String label, ComponentName cn) {
            this.pkg = pkg;
            this.label = label;
            this.cn = cn;
        }
    }

    private float dp;
    private TextView date, target, meta, tabNet, tabSettings, tabInputs;
    private LinearLayout rail;
    private HorizontalScrollView railScroll;
    private WifiIcon wifi;
    private View fade;
    private SharedPreferences launches;

    private final List<App> apps = new ArrayList<>();
    private final List<View> tiles = new ArrayList<>();
    private final List<View> tabs = new ArrayList<>();
    private String lastPkg;
    private int lastTab = -1;
    private boolean resumed;
    private AppOrder order;
    private App moving;
    private ObjectAnimator pulse;

    private ConnectivityManager cm;
    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
            runOnUiThread(() -> showNet(caps));
        }

        @Override
        public void onLost(Network n) {
            runOnUiThread(() -> showNet(null));
        }
    };

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateDate();
            refreshMeta();
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            endMove();
            loadApps();
            buildRail();
            restoreFocus();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        dp = getResources().getDisplayMetrics().density;
        launches = getSharedPreferences("launches", MODE_PRIVATE);
        order = new AppOrder(this);

        date = findViewById(R.id.date);
        target = findViewById(R.id.target);
        meta = findViewById(R.id.meta);
        tabNet = findViewById(R.id.tabNet);
        tabSettings = findViewById(R.id.tabSettings);
        tabInputs = findViewById(R.id.tabInputs);
        rail = findViewById(R.id.rail);
        railScroll = findViewById(R.id.railScroll);

        // The right-edge fade only while there is more rail to scroll to.
        fade = findViewById(R.id.fade);
        railScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> updateFade());

        wifi = new WifiIcon(12 * dp, 1.6f * dp);
        tabNet.setCompoundDrawables(wifi, null, null, null);
        tabNet.setCompoundDrawablePadding((int) (8 * dp));
        for (TextView t : new TextView[]{tabNet, tabSettings, tabInputs}) styleTab(t);
        tabNet.setOnClickListener(v -> {
            if (!open(new Intent(Settings.ACTION_WIFI_SETTINGS))) open(new Intent(Settings.ACTION_SETTINGS));
        });
        tabSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        tabInputs.setOnClickListener(v -> showInputs());
        tabInputs.setVisibility(passthroughInputs().isEmpty() ? View.GONE : View.VISIBLE);
        for (View v : new View[]{tabNet, tabSettings, tabInputs}) if (v.getVisibility() == View.VISIBLE) tabs.add(v);

        showNet(null);
        cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(netCallback);
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);

        loadApps();
        buildRail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        updateDate();
        restoreFocus();
        refreshMeta();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // The system hands initial focus to the first focusable view; put it back on the rail.
        if (hasFocus) {
            View f = getCurrentFocus();
            if (f == null || tabs.contains(f) && lastTab < 0) restoreFocus();
        }
    }

    @Override
    protected void onPause() {
        endMove();
        resumed = false;
        unregisterReceiver(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        if (cm != null) cm.unregisterNetworkCallback(netCallback);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home while already home: back to the first tile.
        if (resumed && !tiles.isEmpty()) {
            railScroll.smoothScrollTo(0, 0);
            tiles.get(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
    }

    // ---- apps ----------------------------------------------------------------------------------------

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            String pkg = ai.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
            apps.add(new App(pkg, String.valueOf(ri.loadLabel(pm)).trim(), new ComponentName(pkg, ai.name)));
        }
        // A-Z, then the user's own order on top.
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        order.apply(apps, a -> a.pkg);
    }

    private void buildRail() {
        rail.removeAllViews();
        tiles.clear();
        for (App a : apps) rail.addView(tile(a));
    }

    private View tile(App app) {
        TextView t = new TextView(this, null, 0, R.style.Ox);
        t.setText(app.label.toUpperCase(Locale.ROOT));
        t.setFontVariationSettings("'wght' 500");
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLetterSpacing(0.14f);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        int padH = (int) (16 * dp);
        t.setPadding(padH, 0, padH, 0);
        t.setFocusable(true);
        t.setClickable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused},
                new FrostDrawable(FrostDrawable.CUT, 10 * dp, Hud.FROST_FOCUS, Hud.GREEN, 1.5f * dp, true, dp));
        bg.addState(new int[]{}, new FrostDrawable(FrostDrawable.CUT, 10 * dp, Hud.FROST, Hud.EDGE, dp, false, dp));
        t.setBackground(bg);
        t.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFFE6FFF0, 0xFF9CC8B0}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (150 * dp), (int) (76 * dp));
        lp.rightMargin = (int) (16 * dp);
                t.setLayoutParams(lp);
        t.setOnClickListener(v -> launch(app));
        t.setOnLongClickListener(v -> {
            showMenu(v, app);
            return true;
        });
        t.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(160).start();
            if (has) select(app);
        });
        tiles.add(t);
        return t;
    }

    private void select(App app) {
        boolean changed = !app.pkg.equals(lastPkg);
        lastPkg = app.pkg;
        target.setText(app.label);
        meta.setText(metaFor(app));
        updateFade();
        if (changed) {
            target.setAlpha(0.2f);
            target.setTranslationX(12 * dp);
            target.animate().alpha(1f).translationX(0).setDuration(220).start();
        }
    }

    /** Fade only while more rail lies to the right and the last tile isn't the focused one. */
    private void updateFade() {
        boolean lastFocused = !tiles.isEmpty() && tiles.get(tiles.size() - 1).isFocused();
        fade.animate().alpha(railScroll.canScrollHorizontally(1) && !lastFocused ? 1f : 0f).setDuration(150).start();
    }

    private String metaFor(App app) {
        long now = System.currentTimeMillis(), last = launches.getLong("last_" + app.pkg, 0);
        int n = 0;
        for (String t : launches.getString("hist_" + app.pkg, "").split(",")) {
            try {
                if (!t.isEmpty() && now - Long.parseLong(t) < 7 * DAY) n++;
            } catch (NumberFormatException ignored) {
            }
        }
        return "LAST OPEN " + (last == 0 ? "NEVER" : ago(now - last)) + "  ·  " + n + " LAUNCHES / 7D";
    }

    private void refreshMeta() {
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).isFocused()) meta.setText(metaFor(apps.get(i)));
    }

    private void launch(App app) {
        long now = System.currentTimeMillis();
        StringBuilder hist = new StringBuilder(String.valueOf(now));
        for (String t : launches.getString("hist_" + app.pkg, "").split(",")) {
            try {
                if (!t.isEmpty() && now - Long.parseLong(t) < 7 * DAY && hist.length() < 600) hist.append(',').append(t);
            } catch (NumberFormatException ignored) {
            }
        }
        launches.edit().putLong("last_" + app.pkg, now).putString("hist_" + app.pkg, hist.toString()).apply();
        open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(app.cn)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private boolean open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- focus: two rows, tabs and rail; one step per press, edges hold ------------------------------

    private void restoreFocus() {
        if (tiles.isEmpty()) return;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).pkg.equals(lastPkg)) {
                tiles.get(i).requestFocus();
                return;
            }
        }
        View f = getCurrentFocus();
        if (f == null || !tiles.contains(f) && (!tabs.contains(f) || lastTab < 0)) tiles.get(0).requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving != null && moveKey(e)) return true;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (k != KeyEvent.KEYCODE_DPAD_UP && k != KeyEvent.KEYCODE_DPAD_DOWN
                && k != KeyEvent.KEYCODE_DPAD_LEFT && k != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return super.dispatchKeyEvent(e);
        }
        View cur = getCurrentFocus();
        int t = tiles.indexOf(cur), h = tabs.indexOf(cur);
        if (t < 0 && h < 0) return super.dispatchKeyEvent(e);
        View next = null;
        if (t >= 0) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && t > 0) next = tiles.get(t - 1);
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT && t < tiles.size() - 1) next = tiles.get(t + 1);
            if (k == KeyEvent.KEYCODE_DPAD_UP && !tabs.isEmpty())
                next = tabs.get(lastTab >= 0 && lastTab < tabs.size() ? lastTab : 0);
        } else {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && h > 0) next = tabs.get(h - 1);
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT && h < tabs.size() - 1) next = tabs.get(h + 1);
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && !tiles.isEmpty()) {
                lastTab = h;
                int i = 0;
                for (int j = 0; j < apps.size(); j++) if (apps.get(j).pkg.equals(lastPkg)) i = j;
                next = tiles.get(i);
            }
        }
        if (next != null) next.requestFocus();
        return true;
    }

    // ---- hold menu: move / uninstall ----------------------------------------------------------------

    private void showMenu(View anchor, App app) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * dp);
        list.setPadding(pad, pad, pad, pad);
        list.setBackground(new FrostDrawable(FrostDrawable.CUT, 10 * dp, 0xF0050C08, Hud.EDGE, dp, false, dp));
        PopupWindow pop = new PopupWindow(list, (int) (170 * dp), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setBackgroundDrawable(new ColorDrawable(0));
        menuRow(list, pop, "MOVE", () -> startMove(app));
        if (AppOrder.removable(this, app.pkg)) menuRow(list, pop, "UNINSTALL", () -> AppOrder.uninstall(this, app.pkg));
        // The rail sits at the bottom, so open above the tile.
        list.measure(View.MeasureSpec.makeMeasureSpec(pop.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        pop.showAsDropDown(anchor, (int) (-10 * dp), -(anchor.getHeight() + list.getMeasuredHeight() + (int) (14 * dp)));
        list.getChildAt(0).requestFocus();
    }

    private void menuRow(LinearLayout list, PopupWindow pop, String text, Runnable action) {
        TextView row = new TextView(this, null, 0, R.style.Tab);
        row.setText(text);
        styleTab(row);
        row.setOnClickListener(v -> {
            pop.dismiss();
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (28 * dp));
        lp.bottomMargin = (int) (6 * dp);
        list.addView(row, lp);
    }

    private void startMove(App app) {
        moving = app;
        int i = apps.indexOf(app);
        if (i < 0) return;
        tiles.get(i).requestFocus();
        pulse = AppOrder.pulse(tiles.get(i));
        meta.setText("LEFT / RIGHT TO MOVE  ·  OK WHEN DONE");
    }

    private void endMove() {
        if (moving == null) return;
        if (pulse != null) pulse.cancel();
        int i = apps.indexOf(moving);
        if (i >= 0) tiles.get(i).setAlpha(1f);
        moving = null;
        refreshMeta();
    }

    /** While moving, Left/Right swap the tile with its neighbour; OK or Back drops it. */
    private boolean moveKey(KeyEvent e) {
        int k = e.getKeyCode(), step;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT: step = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: step = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (e.getAction() == KeyEvent.ACTION_DOWN) endMove();
                return true;
            default:
                return false;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int i = apps.indexOf(moving), j = i + step;
        if (i < 0 || j < 0 || j >= apps.size()) return true;
        // Move the neighbour, not the focused tile, so focus never drops.
        View nb = tiles.get(j), t = tiles.get(i);
        rail.removeView(nb);
        rail.addView(nb, i);
        Collections.swap(tiles, i, j);
        Collections.swap(apps, i, j);
        order.save(apps, a -> a.pkg);
        // Removing a child resets the rail's scroll; after layout, bring the moved tile back into view.
        int keep = railScroll.getScrollX();
        railScroll.post(() -> {
            railScroll.scrollTo(keep, 0);
            ensureVisible(t);
            updateFade();
        });
        return true;
    }

    private void ensureVisible(View t) {
        int pad = (int) (48 * dp), x = railScroll.getScrollX(), w = railScroll.getWidth();
        int left = t.getLeft() + rail.getLeft(), right = t.getRight() + rail.getLeft();
        if (right + pad > x + w) railScroll.smoothScrollTo(right + pad - w, 0);
        else if (left - pad < x) railScroll.smoothScrollTo(Math.max(0, left - pad), 0);
    }

    // ---- top bar -------------------------------------------------------------------------------------

    private void styleTab(TextView t) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused},
                new FrostDrawable(FrostDrawable.HEX, 10 * dp, Hud.FROST_FOCUS, Hud.GREEN, 1.5f * dp, true, dp));
        bg.addState(new int[]{}, new FrostDrawable(FrostDrawable.HEX, 10 * dp, Hud.FROST, Hud.EDGE, dp, false, dp));
        t.setBackground(bg);
        t.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{Hud.GREEN, Hud.TEXT}));
    }

    private void updateDate() {
        date.setText(new SimpleDateFormat("EEEE  ·  dd MMM yyyy", Locale.getDefault()).format(System.currentTimeMillis()));
    }

    @SuppressWarnings("deprecation")
    private void showNet(NetworkCapabilities caps) {
        if (caps == null) {
            tabNet.setText("OFFLINE");
            wifi.set(0, false);
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            tabNet.setText("ETHERNET");
            wifi.set(3, true);
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            String s = wi == null ? null : wi.getSSID();
            if (s == null || s.contains("unknown ssid")) s = "WI-FI";
            else if (s.length() > 1 && s.startsWith("\"")) s = s.substring(1, s.length() - 1);
            tabNet.setText(s.length() > 24 ? s.substring(0, 23) + "…" : s);
            wifi.set(wi == null ? 3 : Math.max(1, WifiManager.calculateSignalLevel(wi.getRssi(), 4)), false);
        } else {
            tabNet.setText("ONLINE");
            wifi.set(3, false);
        }
    }

    private static String ago(long ms) {
        long m = ms / 60000;
        if (m < 1) return "JUST NOW";
        if (m < 60) return m + "M AGO";
        long h = m / 60;
        return h < 24 ? h + "H AGO" : (h / 24) + "D AGO";
    }

    // ---- inputs --------------------------------------------------------------------------------------

    private List<TvInputInfo> passthroughInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo i : tim.getTvInputList()) {
            boolean arc = i.getType() != TvInputInfo.TYPE_HDMI && i.getId().matches("(?i).*[./]ARC[A-Za-z]*Service.*");
            if (i.isPassthroughInput() && !arc) out.add(i);
        }
        return out;
    }

    private void showInputs() {
        List<TvInputInfo> inputs = passthroughInputs();
        if (inputs.isEmpty()) return;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * dp);
        list.setPadding(pad, pad, pad, pad);
        list.setBackground(new FrostDrawable(FrostDrawable.CUT, 10 * dp, 0xF0050C08, Hud.EDGE, dp, false, dp));
        PopupWindow pop = new PopupWindow(list, (int) (190 * dp), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setBackgroundDrawable(new ColorDrawable(0));
        for (TvInputInfo in : inputs) {
            CharSequence l = in.loadLabel(this);
            TextView row = new TextView(this, null, 0, R.style.Tab);
            row.setText(l == null ? in.getId() : l);
            styleTab(row);
            String id = in.getId();
            row.setOnClickListener(v -> {
                pop.dismiss();
                open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (28 * dp));
            lp.bottomMargin = (int) (6 * dp);
            list.addView(row, lp);
        }
        pop.showAsDropDown(tabInputs, 0, (int) (8 * dp));
        list.getChildAt(0).requestFocus();
    }
}
