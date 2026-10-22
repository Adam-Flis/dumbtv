package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;

/**
 * The centered card every custom dialog uses (time picker, welcome, remote guide, row order):
 * one look, one place to change it.
 */
final class Panel {
    private Panel() {}

    /** A dialog with no title bar, ready for {@link #show}. */
    static Dialog dialog(Activity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    /** The card itself: a vertical column with the shared padding, fill and outline. */
    static LinearLayout box(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 44), Ui.dp(c, 32), Ui.dp(c, 44), Ui.dp(c, 30));
        box.setBackground(Ui.card(c, Ui.PANEL, 26));
        return box;
    }

    /** Shows {@code content} in {@code d}, {@code widthDp} wide, over a dimmed screen. */
    static void show(Activity a, Dialog d, View content, int widthDp) {
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setDimAmount(0.7f);
        }
        d.show();
        // the dialog theme caps the width; set it so lines do not wrap early
        if (w != null) w.setLayout(Ui.dp(a, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
