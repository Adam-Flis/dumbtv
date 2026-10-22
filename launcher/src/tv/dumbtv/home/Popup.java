package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Every pop-up the launcher shows (messages, pick-one lists, checklists, text entry), built on
 * {@link Panel} so they all share one look: the dark card, a light title, accent-focused rows
 * and pill buttons. Android's AlertDialog is not used anywhere.
 */
final class Popup {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT, MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    /** A pill button; the dialog closes before {@code run} (which may be null) runs. */
    static final class Action {
        final String label;
        final Runnable run;

        Action(String label, Runnable run) {
            this.label = label;
            this.run = run;
        }
    }

    interface Pick {
        void at(int index);
    }

    interface Toggle {
        void set(int index, boolean on);
    }

    interface Text {
        void done(String text);
    }

    private Popup() {}

    /** A title, a few lines of text and buttons; the first button has the focus. */
    static void message(Activity a, String title, String message, Action... actions) {
        Dialog d = Panel.dialog(a);
        LinearLayout box = Panel.box(a);
        title(box, title);
        TextView body = Ui.text(a, 17, Ui.MUTED, Ui.MEDIUM);
        body.setLineSpacing(0, 1.3f);
        body.setText(message);
        box.addView(body, new LinearLayout.LayoutParams(MATCH, WRAP));
        View first = buttons(d, box, actions);
        Panel.show(a, d, box, 640);
        if (first != null) first.requestFocus();
    }

    /** A list to choose one from (an app's menu, matching places). Back cancels. */
    static void choose(Activity a, String title, List<String> items, Pick pick) {
        Dialog d = Panel.dialog(a);
        LinearLayout box = Panel.box(a);
        title(box, title);
        LinearLayout list = list(box);
        View first = null;
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            TextView row = row(a, items.get(i));
            row.setOnClickListener(v -> {
                d.dismiss();
                pick.at(index);
            });
            list.addView(row, rowParams(a));
            if (first == null) first = row;
        }
        Panel.show(a, d, box, 600);
        if (first != null) first.requestFocus();
    }

    /**
     * Rows that OK or left/right switch between Shown and Hidden (a hidden name is dimmed, as in
     * the home screen layout editor), and a Done button. {@code subtitle} may be null.
     */
    static void checklist(Activity a, String title, String subtitle, List<String> items, boolean[] on, Toggle toggle) {
        Dialog d = Panel.dialog(a);
        LinearLayout box = Panel.box(a);
        title(box, title);
        subtitle(box, subtitle);
        LinearLayout list = list(box);
        View first = null;
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            ValueRow row = new ValueRow(a, items.get(i), -1);
            Runnable show = () -> row.show(on[index] ? "Shown" : "Hidden", on[index], true);
            Runnable flip = () -> {
                on[index] = !on[index];
                toggle.set(index, on[index]);
                show.run();
            };
            row.onChange(by -> flip.run());
            show.run();
            list.addView(row.view);
            if (first == null) first = row.view;
        }
        buttons(d, box, new Action("Done", null));
        Panel.show(a, d, box, 600);
        if (first != null) first.requestFocus();
    }

    /** Settings that left/right (or OK) step through, saved at once, and a Done button. */
    static void choices(Activity a, String title, String subtitle, Config cfg, Runnable changed, Config.Choice... cs) {
        Dialog d = Panel.dialog(a);
        LinearLayout box = Panel.box(a);
        title(box, title);
        subtitle(box, subtitle);
        LinearLayout list = list(box);
        View first = null;
        for (Config.Choice c : cs) {
            ValueRow row = new ValueRow(a, c.title, -1);
            // an "off" value (Hidden) dims the name, as everywhere else
            Runnable show = () -> row.show(c.name(cfg.get(c)), !Config.OFF.equals(cfg.get(c)), true);
            row.onChange(by -> {
                List<String> values = java.util.Arrays.asList(c.values);
                int n = values.size(), i = values.indexOf(cfg.get(c));
                cfg.set(c.key, values.get(((i + by) % n + n) % n));
                show.run();
                if (changed != null) changed.run();
            });
            show.run();
            list.addView(row.view);
            if (first == null) first = row.view;
        }
        buttons(d, box, new Action("Done", null));
        Panel.show(a, d, box, 600);
        if (first != null) first.requestFocus();
    }

    /**
     * One line of text to type: {@code digits} gives the number keypad and accepts only digits,
     * up to {@code max} of them (0: no limit). OK on the keyboard counts as the first button.
     * {@code more} buttons (such as "Original") follow the first, then Cancel.
     */
    static void input(Activity a, String title, String value, boolean digits, int max, String go, Text done,
            Action... more) {
        Dialog d = Panel.dialog(a);
        LinearLayout box = Panel.box(a);
        title(box, title);
        EditText field = new EditText(a);
        field.setSingleLine();
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24 * Ui.scale);
        field.setTextColor(Ui.TEXT);
        field.setHintTextColor(Ui.FAINT);
        field.setTypeface(Ui.MEDIUM);
        field.setPadding(Ui.dp(a, 20), Ui.dp(a, 14), Ui.dp(a, 20), Ui.dp(a, 14));
        // a filled box with an accent outline while it has the focus
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable focused = Ui.round(0x1FFFFFFF, Ui.dp(a, 12));
        focused.setStroke(Ui.dp(a, 2), Config.get(a).accent());
        bg.addState(new int[] {android.R.attr.state_focused}, focused);
        bg.addState(new int[0], Ui.round(0x14FFFFFF, Ui.dp(a, 12)));
        field.setBackground(bg);
        if (digits) field.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (max > 0) field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(max)});
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setText(value);
        field.setSelectAllOnFocus(true);
        box.addView(field, new LinearLayout.LayoutParams(MATCH, WRAP));
        Runnable submit = () -> {
            String text = field.getText().toString().trim();
            if (text.isEmpty()) return;
            d.dismiss();
            done.done(text);
        };
        field.setOnEditorActionListener((v, action, e) -> {
            boolean enter = e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_DONE && !enter) return false;
            submit.run();
            return true;
        });
        Action[] all = new Action[more.length + 2];
        all[0] = new Action(go, null);
        System.arraycopy(more, 0, all, 1, more.length);
        all[all.length - 1] = new Action("Cancel", null);
        LinearLayout row = buttonRow(box);
        for (int i = 0; i < all.length; i++) {
            Action act = all[i];
            TextView b = pill(row, act.label, i > 0);
            // the first button submits what was typed; the others close and run their own action
            b.setOnClickListener(v -> {
                if (act == all[0]) {
                    submit.run();
                } else {
                    d.dismiss();
                    if (act.run != null) act.run.run();
                }
            });
        }
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        Panel.show(a, d, box, 640);
        field.requestFocus();
    }

    // ---- the shared pieces ----

    /** The small line under a pop-up's title; null adds nothing. */
    static void subtitle(LinearLayout box, String text) {
        if (text == null) return;
        TextView t = Ui.text(box.getContext(), 15, Ui.MUTED, Ui.MEDIUM);
        t.setLineSpacing(0, 1.25f);
        t.setText(text);
        // pull it up under the title, then leave room before the rows
        t.setPadding(0, 0, 0, Ui.dp(box.getContext(), 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = -Ui.dp(box.getContext(), 10);
        box.addView(t, lp);
    }

    /** Every pop-up's title: one size, one weight, one gap below. */
    static void title(LinearLayout box, String text) {
        TextView t = Ui.text(box.getContext(), 26, Ui.TEXT, Ui.LIGHT);
        t.setText(text);
        t.setPadding(0, 0, 0, Ui.dp(box.getContext(), 18));
        box.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    /** A scrolling column for rows, so a long list never pushes the card off screen. */
    private static LinearLayout list(LinearLayout box) {
        ScrollView scroll = new Ui.SoundScroll(box.getContext());
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(box.getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        box.addView(scroll, new LinearLayout.LayoutParams(MATCH, WRAP));
        return list;
    }

    private static TextView row(Activity a, String text) {
        TextView row = Ui.text(a, 18, Ui.TEXT, Ui.MEDIUM);
        row.setText(text);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(a, 16), Ui.dp(a, 13), Ui.dp(a, 16), Ui.dp(a, 13));
        row.setFocusable(true);
        row.setClickable(true);
        Ui.focusStyle(row, Config.get(a).accent(), 0, Ui.dp(a, 10));
        return row;
    }

    private static LinearLayout.LayoutParams rowParams(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = Ui.dp(a, 4);
        return lp;
    }

    /** The button row; returns the first button. */
    private static View buttons(Dialog d, LinearLayout box, Action... actions) {
        LinearLayout row = buttonRow(box);
        View first = null;
        for (int i = 0; i < actions.length; i++) {
            Action act = actions[i];
            TextView b = pill(row, act.label, i > 0);
            b.setOnClickListener(v -> {
                d.dismiss();
                if (act.run != null) act.run.run();
            });
            if (first == null) first = b;
        }
        return first;
    }

    private static LinearLayout buttonRow(LinearLayout box) {
        LinearLayout row = new LinearLayout(box.getContext());
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, Ui.dp(box.getContext(), 24), 0, 0);
        box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        return row;
    }

    private static TextView pill(LinearLayout row, String label, boolean gap) {
        TextView b = Ui.pill(row.getContext(), label, Config.get(row.getContext()).accent());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        if (gap) lp.leftMargin = Ui.dp(row.getContext(), 12);
        row.addView(b, lp);
        return b;
    }
}
