package tv.dumbtv.home;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Keeps Fire TV's own idle timers in step with Options › Screen saver, so the screen saver starts
 * after the same idle time on the home screen and in any app, and the TV sleeps after the same
 * "Sleep after saver" everywhere. It writes three Fire TV settings (once allowed with
 * {@code adb shell pm grant tv.dumbtv.home android.permission.WRITE_SECURE_SETTINGS}):
 * <ul>
 *   <li>screensaver_components / screensaver_enabled: our FrameDream is the screensaver;</li>
 *   <li>screen_off_timeout: idle time before the screensaver (or, with it off, before sleep);</li>
 *   <li>sleep_timeout: a backstop after which Fire TV sleeps even if a screensaver is up.</li>
 * </ul>
 * Without the grant nothing is written and the home screen runs its own screen saver as before.
 */
final class SystemSaver {
    static final String GRANT = "adb shell pm grant tv.dumbtv.home android.permission.WRITE_SECURE_SETTINGS";
    /** Idle time before the TV sleeps when no screen saver shows (screen saver off, or at night). */
    static final int NO_SAVER_SLEEP_MIN = 5;
    private static final String DREAM = "tv.dumbtv.home/.FrameDream";

    private SystemSaver() {}

    /** Whether the one-time grant was given, so Fire TV's timers follow Options. */
    static boolean managed(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Minutes of idle before the screen saver starts (or the TV sleeps, with it off). */
    static int startMinutes(Config cfg) {
        return cfg.frameOn() ? Integer.parseInt(cfg.get(Config.FRAME)) : NO_SAVER_SLEEP_MIN;
    }

    /** Writes Fire TV's timers from the current options; a no-op without the grant or when already right. */
    static void apply(Context c, Config cfg) {
        if (!managed(c)) return;
        ContentResolver cr = c.getContentResolver();
        try {
            int start = startMinutes(cfg);
            // the backstop: the screen saver's own "Sleep after", plus a little room
            int sleep = cfg.frameOn() ? start + Integer.parseInt(cfg.get(Config.FRAME_FOR)) + 5 : start;
            putSecure(cr, "screensaver_components", DREAM);
            putSecure(cr, "screensaver_enabled", "1");
            putSecure(cr, "screensaver_activate_on_sleep", "1");
            putSecure(cr, "sleep_timeout", String.valueOf(sleep * 60_000L));
            if (Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 0) != start * 60_000) {
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, start * 60_000);
            }
        } catch (RuntimeException e) {
            android.util.Log.w("dumbtv", "screen saver settings: " + e);
        }
    }

    private static void putSecure(ContentResolver cr, String key, String value) {
        if (!value.equals(Settings.Secure.getString(cr, key))) Settings.Secure.putString(cr, key, value);
    }
}
