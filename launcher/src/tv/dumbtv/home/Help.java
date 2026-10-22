package tv.dumbtv.home;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Plain-language help for whoever watches this TV: a remote-button guide, and the checks
 * behind Options › Help (is the Home button ours, is the sleep timer allowed, is the TV online).
 */
final class Help {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Help() {}

    /**
     * Whether the Home button brings this screen back: the usage-access Keeper on Fire OS 6, the
     * accessibility Guard on Fire OS 7 and 8. Both are switched on from a computer at install.
     */
    static boolean homeHeld(Context c) {
        try {
            if (android.os.Build.VERSION.SDK_INT < 26) {
                AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
                return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
            }
            String on = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return on != null && on.contains(c.getPackageName() + "/");
        } catch (RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation") // NetworkInfo is the simple check that works back to Android 7.1
    static boolean online(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
        return n != null && n.isConnected();
    }

    /** What each remote button does here, as key caps. */
    static void remote(Activity a) {
        Dialog d = Panel.dialog(a);
        LinearLayout panel = Panel.box(a);
        Popup.title(panel, "Your remote");
        LinearLayout keys = new LinearLayout(a);
        keys.setOrientation(LinearLayout.VERTICAL);
        Ui.hintLines(keys,
                "▲ ▼ ◀ ▶", "Move around the screen",
                "OK", "Open the app or button you are on",
                "☰ Menu", "Move, rename or hide the app you are on",
                "Back", "Go back one step, or close a menu",
                "Home", "Come back to this home screen",
                "⏯ Play", "Pause or play in most apps");
        panel.addView(keys);
        TextView note = Ui.text(a, 15, Ui.MUTED, Ui.MEDIUM);
        note.setLineSpacing(0, 1.3f);
        note.setText("Amazon's home screen: Options \u203A More \u203A Amazon home screen.");
        note.setPadding(0, dp(a, 16), 0, 0);
        panel.addView(note, new LinearLayout.LayoutParams(dp(a, 600), WRAP));
        // Back or OK closes it; nothing inside needs focus
        d.setOnKeyListener((x, code, e) -> {
            if (e.getAction() == android.view.KeyEvent.ACTION_UP
                    && (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER)) {
                d.dismiss();
                return true;
            }
            return false;
        });
        Panel.show(a, d, panel, 700);
    }

    /**
     * The sleep timer cannot turn the TV off yet. Shared by the top bar and Options › Help so
     * the wording and the command live in one place.
     */
    static void sleepSetup(Activity a) {
        Popup.message(a, "Sleep timer needs a one-time setup",
                "Fire TV only lets this app turn the TV off after one step from a computer:\n\n"
                        + Sleep.ADMIN_COMMAND
                        + "\n\nUntil then, Fire TV's own sleep timer works (All Fire TV settings).",
                new Popup.Action("Open Fire TV settings", () -> {
                    try {
                        a.startActivity(FireSettings.all(a).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException e) {
                        Ui.notice(a, "Could not open that", -1);
                    }
                }),
                new Popup.Action("Cancel", null));
    }

    /** A plain message with one button, for "this needs setting up" explanations. */
    static void explain(Activity a, String title, String message) {
        Popup.message(a, title, message, new Popup.Action("OK", null));
    }

    private static int dp(Context c, float v) {
        return Ui.dp(c, v);
    }
}
