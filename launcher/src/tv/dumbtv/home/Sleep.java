package tv.dumbtv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** The sleep timer: an alarm that turns the TV off, set from the top bar. */
public final class Sleep extends BroadcastReceiver {
    static final String AT = "sleep.at";
    /** The lengths the sleep menu offers (Custom… covers the rest). */
    static final int[] MINUTES = {15, 30, 45, 60, 90, 120};
    /** The one-time step that lets the app turn the TV off (Fire OS answers the UI with a dummy). */
    static final String ADMIN_COMMAND = "adb shell dpm set-active-admin tv.dumbtv.home/.Admin";

    static ComponentName admin(Context c) {
        return new ComponentName(c, Admin.class);
    }

    static boolean allowed(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(admin(c));
    }

    static void release(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isAdminActive(admin(c))) dpm.removeActiveAdmin(admin(c));
    }

    /** Minutes left, or 0 when no timer runs. */
    static int left(Config cfg) {
        long at = at(cfg);
        long ms = at - System.currentTimeMillis();
        return at == 0 || ms <= 0 ? 0 : (int) ((ms + 59_999) / 60_000);
    }

    private static long at(Config cfg) {
        try {
            return Long.parseLong(cfg.get(AT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Starts a timer of {@code minutes}, or cancels it with 0. */
    static void set(Context c, int minutes) {
        Config cfg = Config.get(c);
        cancelAlarm(c);
        if (minutes <= 0) {
            cfg.set(AT, null);
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        cfg.set(AT, String.valueOf(at));
        arm(c, at);
    }

    /**
     * An app update stops the app, and Android drops its alarms with it: a running timer is set
     * again for the same moment (BootReceiver, on MY_PACKAGE_REPLACED).
     */
    static void rearm(Context c) {
        Config cfg = Config.get(c);
        if (left(cfg) > 0) arm(c, at(cfg));
        else cfg.set(AT, null);
    }

    private static PendingIntent alarm(Context c) {
        return PendingIntent.getBroadcast(c, 7, new Intent(c, Sleep.class), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void cancelAlarm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarm(c));
    }

    private static void arm(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(c));
    }

    /** "45 min", "1 hour", "1 hr 20 min", "2 hours". */
    static String label(int minutes) {
        if (minutes <= 0) return "Off";
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + " min";
        if (m == 0) return h == 1 ? "1 hour" : h + " hours";
        return h + " hr " + m + " min";
    }

    /** The top-bar button's label: "Sleep timer", "Sleep in 45 min", "Sleep in 1:55". */
    static String chip(int left) {
        if (left <= 0) return "Sleep timer";
        if (left < 60) return "Sleep in " + left + " min";
        return String.format(java.util.Locale.ROOT, "Sleep in %d:%02d", left / 60, left % 60);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        Config.get(c).set(AT, null);
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isAdminActive(admin(c))) dpm.lockNow();
    }
}
