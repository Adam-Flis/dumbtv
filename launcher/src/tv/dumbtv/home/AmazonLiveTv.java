package tv.dumbtv.home;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Opens the antenna in Amazon's own Live TV (its guide with logos and previews, options, caption
 * styles, recordings), which other apps may not start directly (TUNE_CHANNEL and SHOW_EPG are
 * Amazon-only).
 *
 * The one open door is Amazon's InputChooserActivity: the screen Fire TV shows at power-on when
 * Settings › Display › "Power on to" says Live TV. Started while the screen is on, it reads that
 * choice (secure setting next_input_start) and, for "tuner", opens Live TV on the last channel.
 * So this sets the choice to "tuner" for a moment, starts it, and puts the viewer's own choice
 * back: after 3 s, and again whenever the home screen or the TV starts, in case anything stopped
 * this app in between. Needs WRITE_SECURE_SETTINGS (install.sh), like SystemSaver.
 */
final class AmazonLiveTv {
    private static final String PKG = "com.amazon.tv.inputpreference.service";
    private static final String CHOOSER = "com.amazon.tv.inputpreference.player.InputChooserActivity";
    private static final String NEXT = "next_input_start", TUNER = "tuner";
    /** home.conf: the viewer's own power-on choice while it is borrowed. */
    private static final String SAVED = "antenna.power_on";
    private static final long PUT_BACK_MS = 3000;

    private AmazonLiveTv() {}

    /** Whether this TV has the door and the app may use it. */
    static boolean available(Context c) {
        if (!SystemSaver.managed(c)) return false;
        Intent i = new Intent().setClassName(PKG, CHOOSER);
        return c.getPackageManager().resolveActivity(i, 0) != null;
    }

    /** Opens Amazon's Live TV on the last channel; false when it could not (then use InputScreen). */
    static boolean open(Activity a) {
        ContentResolver cr = a.getContentResolver();
        Config cfg = Config.get(a);
        try {
            String own = Settings.Secure.getString(cr, NEXT);
            if (!TUNER.equals(own) && cfg.get(SAVED, "").isEmpty()) cfg.set(SAVED, own == null ? "home" : own);
            Settings.Secure.putString(cr, NEXT, TUNER);
            a.startActivity(new Intent().setClassName(PKG, CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            android.util.Log.w("dumbtv", "Amazon Live TV: " + e);
            putBack(a);
            return false;
        }
        Context app = a.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> putBack(app), PUT_BACK_MS);
        return true;
    }

    /** Gives the viewer's own power-on choice back, if it is still borrowed. */
    static void putBack(Context c) {
        Config cfg = Config.get(c);
        String own = cfg.get(SAVED, "");
        if (own.isEmpty()) return;
        try {
            Settings.Secure.putString(c.getContentResolver(), NEXT, own);
            cfg.set(SAVED, null);
        } catch (RuntimeException e) {
            android.util.Log.w("dumbtv", "power-on choice: " + e);
        }
    }
}
