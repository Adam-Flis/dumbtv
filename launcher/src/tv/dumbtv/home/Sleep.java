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
    static final int[] MINUTES = {0, 15, 30, 45, 60, 90, 120};

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
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7, new Intent(c, Sleep.class), PendingIntent.FLAG_UPDATE_CURRENT);
        if (am != null) am.cancel(pi);
        if (minutes <= 0) {
            cfg.set(AT, null);
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        cfg.set(AT, String.valueOf(at));
        if (am != null) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    static String label(int minutes) {
        if (minutes == 0) return "Off";
        if (minutes < 60) return minutes + " minutes";
        if (minutes == 60) return "1 hour";
        return minutes % 60 == 0 ? minutes / 60 + " hours" : minutes / 60 + "½ hours";
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        Config.get(c).set(AT, null);
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isAdminActive(admin(c))) dpm.lockNow();
    }
}
