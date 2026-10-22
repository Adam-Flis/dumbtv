package tv.dumbtv.home;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * A top-bar dropdown (Inputs, Sleep timer, Settings): an optional heading, rows that close the
 * menu and then do their thing, dividers. Built the same way everywhere, so they look alike.
 */
final class Dropdown {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Context c;
    private final int accent;
    private final LinearLayout box;
    private final PopupWindow pop;
    private TextView first;

    Dropdown(Context c, int accent) {
        this.c = c;
        this.accent = accent;
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(10), dp(8), dp(8));
        pop = new PopupWindow(box, WRAP, WRAP, true);
        pop.setBackgroundDrawable(Ui.round(Ui.MENU, dp(18)));
        pop.setElevation(dp(12));
    }

    /** Small spaced capitals at the top. */
    Dropdown heading(String text) {
        TextView head = Ui.text(c, 14, Ui.MUTED, Ui.MEDIUM);
        head.setLetterSpacing(0.08f);
        head.setPadding(dp(16), dp(4), dp(16), dp(4));
        head.setText(text);
        box.addView(head);
        return this;
    }

    /** Any other view, such as the sleep timer's time left. */
    Dropdown add(View v) {
        box.addView(v);
        return this;
    }

    Dropdown rule() {
        box.addView(Ui.rule(c, Ui.RULE, 12, 6, 6));
        return this;
    }

    /** A row; the menu closes before {@code run} runs. */
    TextView item(String label, Runnable run) {
        return item(label, null, null, run);
    }

    /**
     * A row with an optional smaller second line ({@code hint}) saying what is inside, and an
     * optional drawable at its end (the green "plugged in" dot of an input).
     */
    TextView item(String label, String hint, Drawable end, Runnable run) {
        TextView row = Ui.text(c, 17, Ui.TEXT, Ui.MEDIUM);
        if (hint == null) {
            row.setText(label);
        } else {
            SpannableString s = new SpannableString(label + "\n" + hint);
            s.setSpan(new RelativeSizeSpan(0.76f), label.length() + 1, s.length(), 0);
            row.setText(s);
            row.setLineSpacing(dp(3), 1f);
        }
        if (end != null) {
            row.setCompoundDrawablesRelative(null, null, end, null);
            row.setCompoundDrawablePadding(dp(12));
        }
        row.setMinWidth(dp(260));
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        Ui.focusStyle(row, accent, 0, dp(10));
        row.setOnClickListener(v -> {
            pop.dismiss();
            run.run();
        });
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        if (first == null) first = row;
        return row;
    }

    /** Opens under {@code anchor}, right edges lined up, with the first row focused. */
    void show(View anchor) {
        // a known width lets Gravity.END line it up under the button
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        pop.setWidth(box.getMeasuredWidth());
        pop.showAsDropDown(anchor, 0, dp(8), Gravity.END);
        if (first != null) first.requestFocus();
    }

    void dismiss() {
        if (pop.isShowing()) pop.dismiss();
    }

    private int dp(float v) {
        return Ui.dp(c, v);
    }
}
