package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
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
 * Plain-language help for whoever watches this TV: a one-time welcome card, a remote-button
 * guide, and the checks behind Options › Help (is the Home button ours, is the sleep timer
 * allowed, is the TV online).
 */
final class Help {
    /** home.conf key: the welcome card has been seen. */
    static final String TIPS_SEEN = "tips.seen";
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

    /** The welcome card: four short lines and one button. {@code done} runs when it closes. */
    static void welcome(Activity a, Runnable done) {
        int accent = Config.get(a).accent();
        Dialog d = Panel.dialog(a);
        LinearLayout panel = Panel.box(a);

        TextView head = Ui.text(a, 28, Ui.TEXT, Ui.LIGHT);
        head.setText("Welcome to your home screen");
        head.setGravity(Gravity.CENTER);
        panel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        TextView sub = Ui.text(a, 16, Ui.MUTED, Ui.MEDIUM);
        sub.setText("A few things worth knowing");
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(a, 8), 0, dp(a, 18));
        panel.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));

        tip(panel, Icons.STAR, "Your favorite apps are in the top row. Select one and press OK to open it.");
        tip(panel, Icons.PENCIL, "To move, rename or hide an app, select it and press ☰ Menu (or hold OK).");
        tip(panel, Icons.INPUT, "The round buttons at the top switch inputs, set a sleep timer and open Options.");
        tip(panel, Icons.HOUSE, "Lost? Press Home on the remote to come straight back here.");

        TextView ok = Ui.pill(a, "Got it", accent);
        ok.setMinWidth(dp(a, 180));
        ok.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(WRAP, WRAP);
        olp.gravity = Gravity.CENTER_HORIZONTAL;
        olp.topMargin = dp(a, 22);
        panel.addView(ok, olp);

        TextView later = Ui.text(a, 14, Ui.FAINT, Ui.MEDIUM);
        later.setText("See this again any time in Options › Help");
        later.setGravity(Gravity.CENTER);
        later.setPadding(0, dp(a, 14), 0, 0);
        panel.addView(later, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));

        d.setOnDismissListener(x -> {
            if (done != null) done.run();
        });
        Panel.show(a, d, panel, 760);
        ok.requestFocus();
    }

    /** What each remote button does here, as key caps. */
    static void remote(Activity a) {
        Dialog d = Panel.dialog(a);
        LinearLayout panel = Panel.box(a);
        TextView head = Ui.text(a, 24, Ui.TEXT, Ui.LIGHT);
        head.setText("Your remote");
        head.setPadding(0, 0, 0, dp(a, 14));
        panel.addView(head);
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
        note.setText("Amazon's own home screen is still there: Options › More › Open Amazon's home screen."
                + " Press Home to come back.");
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
        new AlertDialog.Builder(a, Ui.DIALOG)
                .setTitle("Sleep timer needs a one-time setup")
                .setMessage("Fire TV only lets this app turn the TV off after one step from a computer:\n\n"
                        + Sleep.ADMIN_COMMAND
                        + "\n\nUntil then, Fire TV's own sleep timer works (All Fire TV settings).")
                .setPositiveButton("Open Fire TV settings", (d, w) -> {
                    try {
                        a.startActivity(FireSettings.all(a).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException e) {
                        Ui.notice(a, "Could not open that", -1);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** A plain message with one button, for "this needs setting up" explanations. */
    static void explain(Activity a, String title, String message) {
        new AlertDialog.Builder(a, Ui.DIALOG)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private static void tip(LinearLayout panel, int icon, String text) {
        Context a = panel.getContext();
        LinearLayout line = new LinearLayout(a);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(a, 8), 0, dp(a, 8));
        View art = new View(a);
        art.setBackground(Icons.of(icon, dp(a, 26), Config.get(a).accent()));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(a, 26), dp(a, 26));
        alp.rightMargin = dp(a, 18);
        line.addView(art, alp);
        TextView t = Ui.text(a, 18, Ui.TEXT, Ui.MEDIUM);
        t.setLineSpacing(0, 1.2f);
        t.setText(text);
        line.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        panel.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
    }

    private static int dp(Context c, float v) {
        return Ui.dp(c, v);
    }
}
