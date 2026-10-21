package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Picks an exact time with the remote: hour / minute (/ AM-PM) columns. Up and down change the
 * selected column, left and right move between columns, OK saves, Back cancels.
 */
final class TimeChooser {
    interface Done {
        void set(int minutesOfDay);
    }

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private TimeChooser() {}

    /** A length of time: hours (0-9) and minutes (0-55 in fives). */
    static void showDuration(Activity a, String title, int minutes, Done done) {
        build(a, title, minutes, true, true, done);
    }

    static void show(Activity a, String title, int minutes, boolean h24, Done done) {
        build(a, title, minutes, h24, false, done);
    }

    private static void build(Activity a, String title, int minutes, boolean h24, boolean length, Done done) {
        int accent = Config.get(a).accent();
        int[] v = {length ? Math.min(9, minutes / 60) : (minutes / 60) % 24, minutes % 60};
        int hours = length ? 10 : 24;
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(a, 40), dp(a, 28), dp(a, 40), dp(a, 28));
        GradientDrawable bg = Ui.round(0xFA1C1D22, dp(a, 26));
        bg.setStroke(Math.max(1, dp(a, 1)), 0x29FFFFFF);
        panel.setBackground(bg);

        TextView head = Ui.text(a, 22, Ui.TEXT, Ui.MEDIUM);
        head.setText(title);
        head.setGravity(Gravity.CENTER);
        panel.addView(head);

        LinearLayout wheels = new LinearLayout(a);
        wheels.setGravity(Gravity.CENTER);
        wheels.setPadding(0, dp(a, 18), 0, dp(a, 8));
        panel.addView(wheels);
        TextView hour = wheel(a, wheels, accent);
        TextView colon = Ui.text(a, length ? 22 : 48, Ui.MUTED, length ? Ui.MEDIUM : Ui.LIGHT);
        colon.setText(length ? "hr" : ":");
        colon.setPadding(dp(a, 4), 0, dp(a, 4), 0);
        wheels.addView(colon, 1);
        TextView minute = wheel(a, wheels, accent);
        TextView half = h24 ? null : wheel(a, wheels, accent);
        if (length) {
            TextView unit = Ui.text(a, 22, Ui.MUTED, Ui.MEDIUM);
            unit.setText("min");
            unit.setPadding(dp(a, 4), 0, 0, 0);
            wheels.addView(unit);
        }

        Runnable show = () -> {
            int h = v[0];
            hour.setText(length ? String.valueOf(h)
                    : h24 ? String.format(Locale.ROOT, "%02d", h) : String.valueOf(h % 12 == 0 ? 12 : h % 12));
            minute.setText(String.format(Locale.ROOT, "%02d", v[1]));
            if (half != null) half.setText(h < 12 ? "AM" : "PM");
        };
        show.run();

        LinearLayout buttons = new LinearLayout(a);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, dp(a, 14), 0, 0);
        TextView save = button(a, length ? "Start" : "Save", accent);
        TextView cancel = button(a, "Cancel", accent);
        buttons.addView(save);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(WRAP, WRAP);
        clp.leftMargin = dp(a, 12);
        buttons.addView(cancel, clp);
        panel.addView(buttons);

        LinearLayout keys = new LinearLayout(a);
        keys.setGravity(Gravity.CENTER);
        keys.setPadding(dp(a, 22), dp(a, 24), 0, 0);
        Ui.hints(keys, "▲ ▼", "Change", "◀ ▶", "Move", "OK", length ? "Start" : "Save");
        panel.addView(keys);

        Runnable commit = () -> {
            int total = v[0] * 60 + v[1];
            if (length && total == 0) return; // a zero-length timer is nothing to start
            done.set(total);
            d.dismiss();
        };
        save.setOnClickListener(x -> commit.run());
        cancel.setOnClickListener(x -> d.dismiss());

        TextView[] cols = half == null ? new TextView[] {hour, minute} : new TextView[] {hour, minute, half};
        TextView last = cols[cols.length - 1];
        // right from the last column goes to Save; the buttons lead back up
        last.setNextFocusRightId(save.getId());
        save.setNextFocusLeftId(last.getId());
        save.setNextFocusUpId(hour.getId());
        cancel.setNextFocusUpId(hour.getId());
        for (TextView w : cols) {
            w.setOnKeyListener((view, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                int by = code == KeyEvent.KEYCODE_DPAD_UP ? 1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? -1 : 0;
                if (by != 0) {
                    if (view == hour) v[0] = (v[0] + by + hours) % hours;
                    else if (view == minute) v[1] = by > 0 ? (v[1] / 5 * 5 + 5) % 60 : ((v[1] + 4) / 5 * 5 - 5 + 60) % 60;
                    else v[0] = (v[0] + 12) % 24;
                    view.playSoundEffect(by > 0 ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
                    show.run();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                    commit.run();
                    return true;
                }
                return false;
            });
        }

        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setDimAmount(0.6f);
        }
        d.show();
        // the dialog theme caps the width; let the panel take what it needs so nothing wraps
        if (w != null) w.setLayout(dp(a, 680), ViewGroup.LayoutParams.WRAP_CONTENT);
        hour.requestFocus();
    }

    /** One column: a ▲, the value, a ▼, all centered on one line down the middle. */
    private static TextView wheel(Activity a, LinearLayout wheels, int accent) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView up = arrow(a, "▲");
        TextView value = Ui.text(a, 48, Ui.TEXT, Ui.LIGHT);
        value.setId(View.generateViewId());
        value.setSingleLine();
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(dp(a, 112));
        value.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        value.setFocusable(true);
        Ui.focusStyle(value, accent, 0x14FFFFFF, dp(a, 18));
        TextView down = arrow(a, "▼");
        col.addView(up, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        col.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        col.addView(down, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(a, 8);
        lp.rightMargin = dp(a, 8);
        wheels.addView(col, lp);
        return value;
    }

    private static TextView arrow(Activity a, String glyph) {
        TextView t = Ui.text(a, 13, Ui.FAINT, Ui.MEDIUM);
        t.setText(glyph);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(a, 6), 0, dp(a, 6));
        return t;
    }

    private static TextView button(Activity a, String label, int accent) {
        TextView b = Ui.text(a, 17, Ui.TEXT, Ui.MEDIUM);
        b.setId(View.generateViewId());
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(dp(a, 120));
        b.setPadding(dp(a, 22), dp(a, 11), dp(a, 22), dp(a, 11));
        b.setFocusable(true);
        b.setClickable(true);
        Ui.chipStyle(b, accent, dp(a, 22), Math.max(1, dp(a, 1)));
        return b;
    }

    private static int dp(Activity a, float v) {
        return Ui.dp(a, v);
    }
}
