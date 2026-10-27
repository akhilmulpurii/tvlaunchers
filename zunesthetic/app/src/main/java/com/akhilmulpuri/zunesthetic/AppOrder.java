package com.akhilmulpuri.zunesthetic;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** User's app order (A-Z until they move something), plus the hold-menu helpers. */
final class AppOrder {
    private final SharedPreferences prefs;

    AppOrder(Context c) {
        prefs = c.getSharedPreferences("order", Context.MODE_PRIVATE);
    }

    /** Expects an A-Z list. Saved order goes first; apps not in it (new installs) follow, still A-Z. */
    <T> void apply(List<T> apps, Function<T, String> pkg) {
        String s = prefs.getString("list", "");
        if (s.isEmpty()) return;
        List<String> saved = Arrays.asList(s.split(","));
        apps.sort(Comparator.comparingInt(a -> {
            int i = saved.indexOf(pkg.apply(a));
            return i < 0 ? Integer.MAX_VALUE : i;
        }));
    }

    <T> void save(List<T> apps, Function<T, String> pkg) {
        StringBuilder b = new StringBuilder();
        for (T a : apps) b.append(b.length() == 0 ? "" : ",").append(pkg.apply(a));
        prefs.edit().putString("list", b.toString()).apply();
    }

    /** System apps can't be uninstalled, only user-installed ones. */
    static boolean removable(Context c, String pkg) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static void uninstall(Context c, String pkg) {
        try {
            c.startActivity(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
    }

    /** Slow breathing on the item being moved. */
    static ObjectAnimator pulse(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.35f);
        a.setDuration(650);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.start();
        return a;
    }
}
