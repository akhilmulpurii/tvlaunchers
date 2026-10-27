package com.akhilmulpuri.zunesthetic;

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
import android.graphics.drawable.ColorDrawable;
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
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
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

/** Zunesthetic: a Zune / Windows Phone style TV home. One big vertical list, no icons. */
public class MainActivity extends Activity {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final float REST = 0.42f, CONTROL_REST = 0.36f;

    /** A row in the list: a control or an app. */
    private final class Row {
        final String key, label;
        final boolean control;
        final Runnable action;
        View view;
        TextView title, caption;

        Row(String key, String label, boolean control, Runnable action) {
            this.key = key;
            this.label = label;
            this.control = control;
            this.action = action;
        }
    }

    private float dp;
    private AnchorScrollView scroll;
    private LinearLayout list;
    private TextView date;
    private SharedPreferences launches;
    private final List<Row> rows = new ArrayList<>();
    private Row wifiRow;
    private String netCaption = "";
    private String lastKey;
    private AppOrder order;
    private Row moving;
    private ObjectAnimator pulse;
    private boolean resumed, enterOnResume = true;

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
            refreshCaption();
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            endMove();
            build();
            restoreFocus(false);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        dp = getResources().getDisplayMetrics().density;
        launches = getSharedPreferences("launches", MODE_PRIVATE);
        order = new AppOrder(this);
        scroll = findViewById(R.id.scroll);
        list = findViewById(R.id.list);
        date = findViewById(R.id.date);
        findViewById(R.id.backdrop).setLayerType(View.LAYER_TYPE_HARDWARE, null);

        cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(netCallback);
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        build();
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
        // Lay out first so the anchor is known, then focus and play the entrance.
        scroll.post(() -> {
            restoreFocus(false);
            refreshCaption();
            if (enterOnResume) turnstile();
            enterOnResume = false;
        });
    }

    @Override
    protected void onPause() {
        endMove();
        resumed = false;
        enterOnResume = true;
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
        // Home while already home: back to the first app.
        if (resumed) {
            for (Row r : rows) {
                if (!r.control) {
                    r.view.requestFocus();
                    return;
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
    }

    // ---- list ----------------------------------------------------------------------------------------

    private void build() {
        list.removeAllViews();
        rows.clear();
        boolean inputs = !passthroughInputs().isEmpty();
        if (inputs) rows.add(new Row("@inputs", "inputs", true, this::showInputs));
        wifiRow = new Row("@wifi", "wi-fi", true, () -> {
            if (!open(new Intent(Settings.ACTION_WIFI_SETTINGS))) open(new Intent(Settings.ACTION_SETTINGS));
        });
        rows.add(wifiRow);
        rows.add(new Row("@settings", "settings", true, () -> open(new Intent(Settings.ACTION_SETTINGS))));

        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> seen = new HashSet<>();
        List<Row> apps = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            String pkg = ai.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
            String label = String.valueOf(ri.loadLabel(pm)).trim();
            ComponentName cn = new ComponentName(pkg, ai.name);
            apps.add(new Row(pkg, label.toLowerCase(Locale.getDefault()), false, () -> launch(pkg, cn)));
        }
        apps.sort((a, b) -> a.label.compareTo(b.label));
        order.apply(apps, r -> r.key);

        for (Row r : rows) list.addView(item(r));
        View divider = new View(this);
        divider.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int) (220 * dp), Math.max(1, (int) dp));
        dlp.setMargins((int) (4 * dp), (int) (16 * dp), 0, (int) (22 * dp));
        list.addView(divider, dlp);
        for (Row r : apps) {
            rows.add(r);
            list.addView(item(r));
        }
        if (wifiRow.caption != null) wifiRow.caption.setText(netCaption);
    }

    private View item(Row r) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(null);
        v.setDefaultFocusHighlightEnabled(false);

        TextView title = new TextView(this, null, 0, R.style.Light);
        title.setText(r.label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        title.setLetterSpacing(-0.02f);
        title.setTextColor(r.control ? getColor(R.color.control) : 0xFFFFFFFF);
        v.addView(title);

        TextView caption = new TextView(this, null, 0, R.style.Caption);
        caption.setVisibility(View.GONE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins((int) (3 * dp), 0, 0, (int) (6 * dp));
        v.addView(caption, clp);

        v.setAlpha(r.control ? CONTROL_REST : REST);
        v.setPadding(0, (int) (2 * dp), 0, (int) (4 * dp));
        v.setOnClickListener(x -> r.action.run());
        if (!r.control) {
            v.setOnLongClickListener(x -> {
                showMenu(r);
                return true;
            });
        }
        v.setOnFocusChangeListener((x, has) -> {
            v.animate().alpha(has ? 1f : (r.control ? CONTROL_REST : REST)).setDuration(140).start();
            if (!has) caption.setVisibility(View.GONE);
            if (has) {
                lastKey = r.key;
                String text = captionFor(r);
                caption.setText(text);
                caption.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                // After the caption's layout change, glide the item to the anchor.
                v.post(() -> scroll.glideTo(v, true));
            }
        });
        r.view = v;
        r.title = title;
        r.caption = caption;
        return v;
    }

    private String captionFor(Row r) {
        if (r == moving) return "up / down to move, ok when done";
        switch (r.key) {
            case "@wifi":
                return netCaption;
            case "@settings":
                return "display, sound, network & more";
            case "@inputs":
                return "hdmi & sources";
            default:
                long now = System.currentTimeMillis(), last = launches.getLong("last_" + r.key, 0);
                int n = 0;
                for (String t : launches.getString("hist_" + r.key, "").split(",")) {
                    try {
                        if (!t.isEmpty() && now - Long.parseLong(t) < 7 * DAY) n++;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (last == 0) return "";   // never opened: no caption
                return "opened " + ago(now - last) + (n > 1 ? "  ·  " + n + " times this week" : "");
        }
    }

    private void refreshCaption() {
        for (Row r : rows) {
            if (r.view == null || !r.view.isFocused()) continue;
            String text = captionFor(r);
            r.caption.setText(text);
            r.caption.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /** Metro-style entrance: rows near the screen slide in from the right, one after another. */
    private void turnstile() {
        int top = scroll.getScrollY() - scroll.getHeight(), bottom = scroll.getScrollY() + scroll.getHeight();
        int i = 0;
        for (int c = 0; c < list.getChildCount(); c++) {
            View v = list.getChildAt(c);
            if (v.getBottom() < top || v.getTop() > bottom) continue;
            v.setTranslationX(70 * dp);
            v.animate().translationX(0).setStartDelay(i * 28L).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            i++;
        }
    }

    private void restoreFocus(boolean animate) {
        if (rows.isEmpty()) return;
        Row target = null;
        for (Row r : rows) if (r.key.equals(lastKey)) target = r;
        if (target == null) for (Row r : rows) if (!r.control && target == null) target = r;
        if (target == null) target = rows.get(0);
        target.view.requestFocus();
        scroll.glideTo(target.view, animate);
    }

    /** Up and Down move one row; nothing wraps, Left and Right do nothing. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving != null && moveKey(e)) return true;
        int k = e.getKeyCode();
        if (k != KeyEvent.KEYCODE_DPAD_UP && k != KeyEvent.KEYCODE_DPAD_DOWN
                && k != KeyEvent.KEYCODE_DPAD_LEFT && k != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return super.dispatchKeyEvent(e);
        }
        View cur = getCurrentFocus();
        int idx = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).view == cur) idx = i;
        if (idx < 0) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == KeyEvent.KEYCODE_DPAD_UP && idx > 0) rows.get(idx - 1).view.requestFocus();
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && idx < rows.size() - 1) rows.get(idx + 1).view.requestFocus();
        }
        return true;
    }

    // ---- hold menu: move / uninstall ----------------------------------------------------------------

    /** Same dark side sheet as inputs: the app's name, then its actions. */
    private void showMenu(Row r) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (28 * dp);
        sheet.setPadding(pad, pad, pad, pad);
        sheet.setBackgroundColor(0xE6000000);
        PopupWindow pop = new PopupWindow(sheet, (int) (420 * dp), ViewGroup.LayoutParams.MATCH_PARENT, true);
        pop.setBackgroundDrawable(new ColorDrawable(0));
        TextView name = new TextView(this, null, 0, R.style.Caption);
        name.setText(r.label);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.setMargins((int) (3 * dp), 0, 0, (int) (10 * dp));
        sheet.addView(name, nlp);
        sheetItem(sheet, pop, "move", () -> startMove(r));
        if (AppOrder.removable(this, r.key)) sheetItem(sheet, pop, "uninstall", () -> AppOrder.uninstall(this, r.key));
        pop.showAtLocation(list, android.view.Gravity.END, 0, 0);
        sheet.getChildAt(1).requestFocus();
    }

    private void sheetItem(LinearLayout sheet, PopupWindow pop, String text, Runnable action) {
        TextView t = new TextView(this, null, 0, R.style.Light);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        t.setFocusable(true);
        t.setClickable(true);
        t.setAlpha(REST);
        t.setOnFocusChangeListener((x, has) -> x.setAlpha(has ? 1f : REST));
        t.setOnClickListener(x -> {
            pop.dismiss();
            action.run();
        });
        sheet.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (58 * dp)));
    }

    private void startMove(Row r) {
        moving = r;
        r.view.requestFocus();
        refreshCaption();
        pulse = AppOrder.pulse(r.title);
    }

    private void endMove() {
        if (moving == null) return;
        if (pulse != null) pulse.cancel();
        moving.title.setAlpha(1f);
        moving = null;
        refreshCaption();
    }

    /** While moving, Up/Down swap the app with its neighbour (apps only, never past the divider). */
    private boolean moveKey(KeyEvent e) {
        int k = e.getKeyCode(), step;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: step = -1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: step = 1; break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (e.getAction() == KeyEvent.ACTION_DOWN) endMove();
                return true;
            default:
                return false;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int i = rows.indexOf(moving), j = i + step;
        if (i < 0 || j < 0 || j >= rows.size() || rows.get(j).control) return true;
        // Move the neighbour, not the focused row, so focus never drops. +1 skips the divider.
        View nb = rows.get(j).view;
        list.removeView(nb);
        list.addView(nb, i + 1);
        Collections.swap(rows, i, j);
        List<Row> apps = new ArrayList<>();
        for (Row r : rows) if (!r.control) apps.add(r);
        order.save(apps, r -> r.key);
        View v = moving.view;
        v.post(() -> scroll.glideTo(v, true));
        return true;
    }

    // ---- actions -------------------------------------------------------------------------------------

    private void launch(String pkg, ComponentName cn) {
        long now = System.currentTimeMillis();
        StringBuilder hist = new StringBuilder(String.valueOf(now));
        for (String t : launches.getString("hist_" + pkg, "").split(",")) {
            try {
                if (!t.isEmpty() && now - Long.parseLong(t) < 7 * DAY && hist.length() < 600) hist.append(',').append(t);
            } catch (NumberFormatException ignored) {
            }
        }
        launches.edit().putLong("last_" + pkg, now).putString("hist_" + pkg, hist.toString()).apply();
        open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(cn)
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

    // ---- readouts ------------------------------------------------------------------------------------

    private void updateDate() {
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(System.currentTimeMillis())
                .toLowerCase(Locale.getDefault()));
    }

    @SuppressWarnings("deprecation")
    private void showNet(NetworkCapabilities caps) {
        if (caps == null) {
            netCaption = "not connected";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            netCaption = "ethernet  ·  connected";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            String s = wi == null ? null : wi.getSSID();
            if (s == null || s.contains("unknown ssid")) s = "wi-fi";
            else if (s.length() > 1 && s.startsWith("\"")) s = s.substring(1, s.length() - 1);
            netCaption = s + "  ·  connected";
        } else {
            netCaption = "connected";
        }
        if (wifiRow != null && wifiRow.caption != null) wifiRow.caption.setText(netCaption);
    }

    private static String ago(long ms) {
        long m = ms / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        long h = m / 60;
        if (h < 24) return h + (h == 1 ? " hour ago" : " hours ago");
        long d = h / 24;
        return d == 1 ? "yesterday" : d + " days ago";
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

    /** Inputs as their own small list, same type, over a dark sheet. */
    private void showInputs() {
        List<TvInputInfo> inputs = passthroughInputs();
        if (inputs.isEmpty()) return;
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (28 * dp);
        sheet.setPadding(pad, pad, pad, pad);
        sheet.setBackgroundColor(0xE6000000);
        PopupWindow pop = new PopupWindow(sheet, (int) (420 * dp), ViewGroup.LayoutParams.MATCH_PARENT, true);
        pop.setBackgroundDrawable(new ColorDrawable(0));
        for (TvInputInfo in : inputs) {
            CharSequence l = in.loadLabel(this);
            TextView t = new TextView(this, null, 0, R.style.Light);
            t.setText(String.valueOf(l == null ? in.getId() : l).toLowerCase(Locale.getDefault()));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
            t.setFocusable(true);
            t.setClickable(true);
            t.setAlpha(REST);
            t.setOnFocusChangeListener((x, has) -> x.setAlpha(has ? 1f : REST));
            String id = in.getId();
            t.setOnClickListener(x -> {
                pop.dismiss();
                open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)));
            });
            sheet.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (58 * dp)));
        }
        pop.showAtLocation(list, android.view.Gravity.END, 0, 0);
        sheet.getChildAt(0).requestFocus();
    }
}
