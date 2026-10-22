package tv.dumbtv.home;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One row of a settings pop-up: an optional icon, a name, and its value on the right. Every
 * pop-up list that shows a value (home screen layout, visible inputs, adjust overlay) is built
 * from this, so they look and behave alike:
 * <ul>
 *   <li>the value gets ‹ › while the row has the focus and the value can change;</li>
 *   <li>a row that is off (Hidden) has its name dimmed;</li>
 *   <li>left/right step the value, OK steps forward, each with the navigation tick.</li>
 * </ul>
 */
final class ValueRow {
    interface Step {
        void by(int step);
    }

    /** Dimmed names for rows that are off: one value, so every pop-up dims alike. */
    static final float OFF_ALPHA = 0.5f;

    final LinearLayout view;
    private final TextView name, value;
    private final int accent;
    private String text = "";
    private boolean on = true, changeable = true, strong;

    /** {@code icon} is an {@link Icons} kind, or -1 for none. */
    ValueRow(Context c, String label, int icon) {
        accent = Config.get(c).accent();
        view = new LinearLayout(c);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setFocusable(true);
        view.setClickable(true);
        view.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 4);
        view.setLayoutParams(lp);
        if (icon >= 0) {
            View art = new View(c);
            art.setBackground(Icons.of(icon, dp(c, 22), 0xD9FFFFFF));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22));
            alp.rightMargin = dp(c, 16);
            view.addView(art, alp);
        }
        name = Ui.text(c, 18, Ui.TEXT, Ui.MEDIUM);
        name.setText(label);
        view.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        value = Ui.text(c, 16, Ui.MUTED, Ui.MEDIUM);
        view.addView(value);
        view.setOnFocusChangeListener((v, has) -> paint());
        paint();
    }

    /** The value text, whether the row is on (off dims the name) and whether ‹ › should show. */
    void show(String text, boolean on, boolean changeable) {
        this.text = text;
        this.on = on;
        this.changeable = changeable;
        paint();
    }

    /** Full accent fill instead of the soft tint: the row is picked up (layout editor). */
    void strong(boolean strong) {
        this.strong = strong;
        paint();
    }

    /** OK steps forward; left/right step back and forward. */
    void onChange(Step step) {
        view.setOnClickListener(v -> step.by(1));
        view.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int by = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (by == 0) return false;
            v.playSoundEffect(by < 0 ? SoundEffectConstants.NAVIGATION_LEFT : SoundEffectConstants.NAVIGATION_RIGHT);
            step.by(by);
            return true;
        });
    }

    private void paint() {
        boolean focused = view.isFocused();
        value.setText(focused && changeable && !strong ? "‹  " + text + "  ›" : text);
        value.setTextColor(focused || strong ? Ui.TEXT : Ui.MUTED);
        name.setAlpha(on ? 1f : OFF_ALPHA);
        view.setBackground(Ui.rowFocus(accent, dp(view.getContext(), 10), strong));
    }

    private static int dp(Context c, float v) {
        return Ui.dp(c, v);
    }
}
