package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Picks an exact time with the remote: big hour / minute (/ AM-PM) wheels, up and down to change. */
final class TimeChooser {
    interface Done {
        void set(int minutesOfDay);
    }

    private TimeChooser() {}

    static void show(Activity a, String title, int minutes, boolean h24, Done done) {
        int accent = Config.get(a).accent();
        int[] v = {(minutes / 60) % 24, minutes % 60};

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(a, 24), Ui.dp(a, 12), Ui.dp(a, 24), Ui.dp(a, 4));
        LinearLayout wheels = new LinearLayout(a);
        wheels.setGravity(Gravity.CENTER);
        box.addView(wheels);

        TextView hour = wheel(a, wheels, accent);
        TextView colon = Ui.text(a, 44, Ui.MUTED, Ui.LIGHT);
        colon.setText(":");
        colon.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        wheels.addView(colon, 1);
        TextView minute = wheel(a, wheels, accent);
        TextView half = h24 ? null : wheel(a, wheels, accent);

        Runnable show = () -> {
            int h = v[0];
            hour.setText(h24 ? String.format(Locale.ROOT, "%02d", h) : String.valueOf(h % 12 == 0 ? 12 : h % 12));
            minute.setText(String.format(Locale.ROOT, "%02d", v[1]));
            if (half != null) half.setText(h < 12 ? "AM" : "PM");
        };
        show.run();

        LinearLayout keys = new LinearLayout(a);
        keys.setGravity(Gravity.CENTER_VERTICAL);
        keys.setPadding(0, Ui.dp(a, 20), 0, Ui.dp(a, 4));
        Ui.hints(keys, "▲ ▼", "Change", "◀ ▶", "Next", "OK", "Save");
        box.addView(keys);

        AlertDialog d = new AlertDialog.Builder(a, Ui.DIALOG)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", (x, w) -> done.set(v[0] * 60 + v[1]))
                .setNegativeButton("Cancel", null)
                .create();

        TextView[] all = half == null ? new TextView[] {hour, minute} : new TextView[] {hour, minute, half};
        for (TextView w : all) {
            w.setOnKeyListener((view, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                int by = code == KeyEvent.KEYCODE_DPAD_UP ? 1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? -1 : 0;
                if (by != 0) {
                    if (view == hour) v[0] = (v[0] + by + 24) % 24;
                    else if (view == minute) v[1] = by > 0 ? (v[1] / 5 * 5 + 5) % 60 : ((v[1] + 4) / 5 * 5 - 5 + 60) % 60;
                    else v[0] = (v[0] + 12) % 24;
                    view.playSoundEffect(by > 0 ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
                    show.run();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                    done.set(v[0] * 60 + v[1]);
                    d.dismiss();
                    return true;
                }
                return false;
            });
        }
        d.show();
        hour.requestFocus();
    }

    /** One column: ▲, the value, ▼. Returns the value, which takes focus. */
    private static TextView wheel(Activity a, LinearLayout wheels, int accent) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView up = Ui.text(a, 14, Ui.FAINT, Ui.MEDIUM);
        up.setText("▲");
        TextView value = Ui.text(a, 44, Ui.TEXT, Ui.LIGHT);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(Ui.dp(a, 104));
        value.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        value.setFocusable(true);
        Ui.focusStyle(value, accent, 0x14FFFFFF, Ui.dp(a, 16));
        TextView down = Ui.text(a, 14, Ui.FAINT, Ui.MEDIUM);
        down.setText("▼");
        col.addView(up);
        col.addView(value);
        col.addView(down);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(a, 6);
        lp.rightMargin = Ui.dp(a, 6);
        wheels.addView(col, lp);
        return value;
    }
}
