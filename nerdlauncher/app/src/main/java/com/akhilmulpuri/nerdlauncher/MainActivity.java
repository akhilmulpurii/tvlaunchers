package com.akhilmulpuri.nerdlauncher;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityManager;
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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Nerd Launcher: a text-only TV home in the style of an instrument dashboard. */
public class MainActivity extends Activity {

    // Nerd Font glyphs (Material Design Icons range).
    private static final String IC_WIFI = glyph(0xF05A9), IC_WIFI_OFF = glyph(0xF05AA), IC_ETH = glyph(0xF0200),
            IC_COG = glyph(0xF0493), IC_HDMI = glyph(0xF0868), IC_DOT = glyph(0xF0765);
    private static final long DAY = 24L * 60 * 60 * 1000;

    static final class App {
        final String pkg, label;
        final ComponentName cn;
        final int color;

        App(String pkg, String label, ComponentName cn, int color) {
            this.pkg = pkg;
            this.label = label;
            this.cn = cn;
            this.color = color;
        }
    }

    private float dp;
    private int cText, cMid, cDim, cYellow, cLine, cBtn, cBtnFocus;
    private int columns;

    private TextView date, dateLong, week, badge, info, btnNet, btnSettings, btnInputs;
    private LinearLayout appsBox;
    private ScrollView scroll;
    private TextView statSelected, statLast, statOpened, statAndroid;

    private AppColors colors;
    private SharedPreferences launches;
    private final List<App> apps = new ArrayList<>();
    /** Focus grid: row 0 is the header, row 1 the dock, then the rest. */
    private final List<List<View>> rows = new ArrayList<>();
    private final Map<View, int[]> where = new HashMap<>();
    private final Map<View, App> appOf = new HashMap<>();
    private String lastPkg;
    private int lastDockCol, lastHeaderCol = -1;
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
            updateClockTexts();
            updateInfo();
            refreshSelected();
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            endMove();
            loadApps();
            buildButtons();
            restoreFocus();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        dp = getResources().getDisplayMetrics().density;
        cText = getColor(R.color.text);
        cMid = getColor(R.color.mid);
        cDim = getColor(R.color.dim);
        cYellow = getColor(R.color.yellow);
        cLine = getColor(R.color.line);
        cBtn = getColor(R.color.btn);
        cBtnFocus = getColor(R.color.btn_focus);
        columns = getResources().getInteger(R.integer.columns);
        colors = new AppColors(this);
        launches = getSharedPreferences("launches", MODE_PRIVATE);
        order = new AppOrder(this);

        date = findViewById(R.id.date);
        dateLong = findViewById(R.id.dateLong);
        week = findViewById(R.id.week);
        badge = findViewById(R.id.badge);
        info = findViewById(R.id.info);
        btnNet = findViewById(R.id.btnNet);
        btnSettings = findViewById(R.id.btnSettings);
        btnInputs = findViewById(R.id.btnInputs);
        appsBox = findViewById(R.id.apps);
        scroll = findViewById(R.id.scroll);

        styleHeaderButton(btnNet);
        styleHeaderButton(btnSettings);
        styleHeaderButton(btnInputs);
        btnSettings.setText(IC_COG + " SETTINGS");
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        btnNet.setOnClickListener(v -> {
            if (!open(new Intent(Settings.ACTION_WIFI_SETTINGS))) open(new Intent(Settings.ACTION_SETTINGS));
        });
        btnInputs.setText(IC_HDMI + " INPUTS");
        btnInputs.setOnClickListener(v -> showInputs());
        btnInputs.setVisibility(passthroughInputs().isEmpty() ? View.GONE : View.VISIBLE);

        buildStats();
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
        buildButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        updateClockTexts();
        updateInfo();
        restoreFocus();
        refreshSelected();
    }

    /** Re-reads the focused app's launch stats (after returning from it, and each minute). */
    private void refreshSelected() {
        App a = appOf.get(getCurrentFocus());
        if (a != null) select(a);
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
        // Home pressed while already home: back to the first dock button.
        if (resumed && rows.size() > 1 && !rows.get(1).isEmpty()) {
            scroll.smoothScrollTo(0, 0);
            rows.get(1).get(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
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
            String label = String.valueOf(ri.loadLabel(pm)).trim();
            int color = colors.forApp(pkg, () -> {
                Drawable b = ai.loadBanner(pm);
                return b != null ? b : ai.loadIcon(pm);
            });
            apps.add(new App(pkg, label, new ComponentName(pkg, ai.name), color));
        }
        // A-Z, then the user's own order on top. The first row is the dock.
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        order.apply(apps, a -> a.pkg);
    }

    private void buildButtons() {
        appsBox.removeAllViews();
        rows.clear();
        where.clear();
        appOf.clear();

        List<View> header = new ArrayList<>();
        for (View v : new View[]{btnInputs, btnNet, btnSettings}) if (v.getVisibility() == View.VISIBLE) header.add(v);
        rows.add(header);

        int dockCount = Math.min(columns, apps.size());
        for (int start = 0; start < apps.size(); start += (start == 0 ? dockCount : columns)) {
            boolean dock = start == 0;
            int end = Math.min(apps.size(), start + (dock ? dockCount : columns));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) ((dock ? 50 : 36) * dp));
            rlp.topMargin = dock ? 0 : (int) ((start == dockCount ? 16 : 10) * dp);
            appsBox.addView(row, rlp);
            List<View> focusRow = new ArrayList<>();
            for (int i = start; i < start + columns; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (i > start) lp.leftMargin = (int) (12 * dp);
                if (i >= end) {
                    row.addView(new View(this), lp);   // keeps widths equal on a short last row
                    continue;
                }
                TextView b = appButton(apps.get(i), dock);
                row.addView(b, lp);
                focusRow.add(b);
            }
            rows.add(focusRow);
        }
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) where.put(rows.get(r).get(c), new int[]{r, c});
        }
        info.setTag(apps.size());
        updateInfo();
    }

    private TextView appButton(App app, boolean dock) {
        TextView b = new TextView(this);
        b.setTypeface(getResources().getFont(R.font.nerd_mono));
        b.setAllCaps(true);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setIncludeFontPadding(false);
        b.setLetterSpacing(0.12f);
        b.setFocusable(true);
        b.setClickable(true);
        if (dock) {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            b.setGravity(Gravity.CENTER);
            b.setText(app.label.toUpperCase(Locale.ROOT));
            b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{app.color, cText}));
            b.setBackground(states(fill(cBtnFocus, app.color, 2), fill(cBtn, 0, 0)));
        } else {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setPadding((int) (12 * dp), 0, (int) (10 * dp), 0);
            SpannableStringBuilder s = new SpannableStringBuilder(IC_DOT + "  " + app.label.toUpperCase(Locale.ROOT));
            s.setSpan(new ForegroundColorSpan(app.color), 0, IC_DOT.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setText(s);
            b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{app.color, cMid}));
            b.setBackground(states(fill(cBtnFocus, app.color, 2), fill(Color.TRANSPARENT, cLine, 1)));
        }
        b.setOnClickListener(v -> launch(app));
        b.setOnLongClickListener(v -> {
            showMenu(v, app);
            return true;
        });
        b.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(120).start();
            if (has) select(app);
        });
        appOf.put(b, app);
        return b;
    }

    private void launch(App app) {
        long now = System.currentTimeMillis();
        StringBuilder hist = new StringBuilder(String.valueOf(now));
        for (String t : launches.getString("hist_" + app.pkg, "").split(",")) {
            if (t.isEmpty()) continue;
            try {
                if (now - Long.parseLong(t) < 7 * DAY && hist.length() < 600) hist.append(',').append(t);
            } catch (NumberFormatException ignored) {
            }
        }
        launches.edit().putLong("last_" + app.pkg, now).putString("hist_" + app.pkg, hist.toString())
                .putString("last_pkg", app.pkg).apply();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(app.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        open(i);
    }

    private boolean open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- focus ---------------------------------------------------------------------------------------

    private void restoreFocus() {
        if (rows.size() < 2) return;
        for (Map.Entry<View, App> e : appOf.entrySet()) {
            if (e.getValue().pkg.equals(lastPkg)) {
                e.getKey().requestFocus();
                return;
            }
        }
        View f = getCurrentFocus();
        if (f == null || !where.containsKey(f)) {
            List<View> dock = rows.get(1);
            if (!dock.isEmpty()) dock.get(Math.min(lastDockCol, dock.size() - 1)).requestFocus();
        }
    }

    private void select(App app) {
        lastPkg = app.pkg;
        int[] rc = null;
        for (Map.Entry<View, App> e : appOf.entrySet()) if (e.getValue() == app) rc = where.get(e.getKey());
        if (rc != null && rc[0] == 1) lastDockCol = rc[1];
        statSelected.setText(app.label);
        statSelected.setTextColor(app.color);
        long last = launches.getLong("last_" + app.pkg, 0);
        statLast.setText(last == 0 ? "NEVER" : ago(System.currentTimeMillis() - last));
        int n = 0;
        long now = System.currentTimeMillis();
        for (String t : launches.getString("hist_" + app.pkg, "").split(",")) {
            try {
                if (!t.isEmpty() && now - Long.parseLong(t) < 7 * DAY) n++;
            } catch (NumberFormatException ignored) {
            }
        }
        statOpened.setText(n + "x / 7D");
    }

    /** D-pad moves exactly one row or column; edges hold instead of wrapping or falling through. */
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
        int[] rc = cur == null ? null : where.get(cur);
        if (rc == null) return super.dispatchKeyEvent(e);   // popups and anything else: default handling
        int r = rc[0], c = rc[1];
        View target = null;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (c > 0) target = rows.get(r).get(c - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (c < rows.get(r).size() - 1) target = rows.get(r).get(c + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (r == 1) {
                    List<View> h = rows.get(0);
                    if (!h.isEmpty()) target = h.get(lastHeaderCol >= 0 && lastHeaderCol < h.size() ? lastHeaderCol : h.size() - 1);
                } else if (r > 1) {
                    List<View> up = rows.get(r - 1);
                    target = up.get(Math.min(c, up.size() - 1));
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (r == 0) {
                    lastHeaderCol = c;
                    List<View> dock = rows.size() > 1 ? rows.get(1) : null;
                    if (dock != null && !dock.isEmpty()) target = dock.get(Math.min(lastDockCol, dock.size() - 1));
                } else if (r + 1 < rows.size() && !rows.get(r + 1).isEmpty()) {
                    List<View> down = rows.get(r + 1);
                    target = down.get(Math.min(c, down.size() - 1));
                }
                break;
        }
        if (target != null) {
            if (r == 0) lastHeaderCol = c;
            target.requestFocus();
            if (where.get(target)[0] == 1) scroll.smoothScrollTo(0, 0);
        }
        return true;
    }

    // ---- hold menu: move / uninstall ----------------------------------------------------------------

    private void showMenu(View anchor, App app) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(fill(cBtn, cLine, 1));
        list.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        PopupWindow pop = new PopupWindow(list, Math.max(anchor.getWidth(), (int) (180 * dp)), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        menuRow(list, pop, "MOVE", () -> startMove(app));
        if (AppOrder.removable(this, app.pkg)) menuRow(list, pop, "UNINSTALL", () -> AppOrder.uninstall(this, app.pkg));
        pop.showAsDropDown(anchor, 0, (int) (6 * dp));
        list.getChildAt(0).requestFocus();
    }

    private void menuRow(LinearLayout list, PopupWindow pop, String text, Runnable action) {
        TextView row = new TextView(this, null, 0, R.style.Nerd_Small);
        row.setText(text);
        row.setLetterSpacing(0.12f);
        row.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), (int) (10 * dp));
        row.setFocusable(true);
        styleHeaderButton(row);
        row.setOnClickListener(v -> {
            pop.dismiss();
            action.run();
        });
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void startMove(App app) {
        moving = app;
        lastPkg = app.pkg;
        restoreFocus();
        View f = getCurrentFocus();
        if (f != null) pulse = AppOrder.pulse(f);
        statLast.setText("D-PAD MOVES");
        statOpened.setText("OK = DONE");
    }

    private void endMove() {
        if (moving == null) return;
        if (pulse != null) pulse.cancel();
        View f = getCurrentFocus();
        if (f != null) f.setAlpha(1f);
        App a = moving;
        moving = null;
        select(a);
    }

    /** While moving, arrows shift the app one place (or one row); OK or Back drops it. */
    private boolean moveKey(KeyEvent e) {
        int k = e.getKeyCode();
        int step = 0;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT: step = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: step = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: step = -columns; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: step = columns; break;
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
        apps.add(j, apps.remove(i));
        order.save(apps, a -> a.pkg);
        if (pulse != null) pulse.cancel();
        buildButtons();
        restoreFocus();
        View f = getCurrentFocus();
        if (f != null) {
            pulse = AppOrder.pulse(f);
            int[] rc = where.get(f);
            if (rc != null && rc[0] == 1) scroll.smoothScrollTo(0, 0);
        }
        statLast.setText("D-PAD MOVES");
        statOpened.setText("OK = DONE");
        return true;
    }

    // ---- header, stats, info -------------------------------------------------------------------------

    private void styleHeaderButton(TextView b) {
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{cYellow, cMid}));
        b.setBackground(states(fill(cBtnFocus, 0, 0), new ColorDrawable(Color.TRANSPARENT)));
        b.setClickable(true);
    }

    private void showNet(NetworkCapabilities caps) {
        String text;
        if (caps == null) {
            text = IC_WIFI_OFF + " OFFLINE";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            text = IC_ETH + " ETHERNET";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            text = IC_WIFI + " " + ssid();
        } else {
            text = IC_WIFI + " ONLINE";
        }
        btnNet.setText(text);
    }

    @SuppressWarnings("deprecation")
    private String ssid() {
        try {
            WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            String s = wi == null ? null : wi.getSSID();
            if (s == null || s.contains("unknown ssid")) return "WI-FI";
            if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
            return s.length() > 18 ? s.substring(0, 17) + "…" : s;
        } catch (RuntimeException e) {
            return "WI-FI";
        }
    }

    private void buildStats() {
        TableLayout t = findViewById(R.id.stats);
        statSelected = statRow(t, "SELECTED");
        statLast = statRow(t, "LAST OPEN");
        statOpened = statRow(t, "OPENED");
        statAndroid = statRow(t, "ANDROID");
        statAndroid.setText(Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT);
        statSelected.setText("—");
        statLast.setText("—");
        statOpened.setText("—");
    }

    private TextView statRow(TableLayout t, String key) {
        TableRow row = new TableRow(this);
        row.setPadding(0, 0, 0, (int) (12 * dp));
        TextView k = new TextView(this, null, 0, R.style.Nerd_Key);
        k.setText(key);
        TextView v = new TextView(this, null, 0, R.style.Nerd_Value);
        row.addView(k);
        row.addView(v);
        t.addView(row);
        return v;
    }

    private void updateClockTexts() {
        Locale l = Locale.getDefault();
        long now = System.currentTimeMillis();
        date.setText(new SimpleDateFormat("EEE dd MMM", l).format(now));
        dateLong.setText(new SimpleDateFormat("dd MMM yyyy", l).format(now));
        Calendar cal = Calendar.getInstance();
        week.setText(String.format(l, "WK %02d · DAY %03d", cal.get(Calendar.WEEK_OF_YEAR), cal.get(Calendar.DAY_OF_YEAR)));
        long up = SystemClock.elapsedRealtime() / 60000;   // minutes
        long d = up / (60 * 24), h = (up / 60) % 24, m = up % 60;
        badge.setText("UPTIME " + (d > 0 ? d + "D " + h + "H" : h > 0 ? h + "H " + m + "M" : m + "M"));
    }

    private void updateInfo() {
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        SpannableStringBuilder s = new SpannableStringBuilder();
        pair(s, "RAM: ", mi.availMem / (1024 * 1024) + " MB FREE");
        s.append("   /   ");
        pair(s, "STORAGE: ", size(fs.getAvailableBytes()) + " FREE");
        s.append("   /   ");
        pair(s, "APPS: ", String.valueOf(apps.size()));
        info.setText(s);
    }

    private void pair(SpannableStringBuilder s, String key, String value) {
        s.append(key);
        int start = s.length();
        s.append(value);
        s.setSpan(new ForegroundColorSpan(cYellow), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String size(long bytes) {
        double gb = bytes / (1024.0 * 1024 * 1024);
        return gb >= 1 ? String.format(Locale.ROOT, "%.1f GB", gb) : (bytes / (1024 * 1024)) + " MB";
    }

    private static String ago(long ms) {
        long m = ms / 60000;
        if (m < 1) return "JUST NOW";
        if (m < 60) return m + "M AGO";
        long h = m / 60;
        if (h < 24) return h + "H AGO";
        return (h / 24) + "D AGO";
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
        list.setBackground(fill(cBtn, cLine, 1));
        list.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        PopupWindow pop = new PopupWindow(list, (int) (220 * dp), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        for (TvInputInfo in : inputs) {
            CharSequence label = in.loadLabel(this);
            TextView row = new TextView(this, null, 0, R.style.Nerd_Small);
            row.setText(IC_HDMI + "  " + (label == null ? in.getId() : label));
            row.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), (int) (10 * dp));
            row.setFocusable(true);
            row.setClickable(true);
            styleHeaderButton(row);
            String id = in.getId();
            row.setOnClickListener(v -> {
                pop.dismiss();
                open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)));
            });
            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        pop.showAsDropDown(btnInputs, 0, (int) (6 * dp));
        list.getChildAt(0).requestFocus();
    }

    // ---- drawing helpers -----------------------------------------------------------------------------

    private GradientDrawable fill(int color, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        if (strokeDp > 0) g.setStroke((int) (strokeDp * dp), stroke);
        return g;
    }

    private static StateListDrawable states(Drawable focused, Drawable normal) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    private static String glyph(int cp) {
        return new String(Character.toChars(cp));
    }
}
