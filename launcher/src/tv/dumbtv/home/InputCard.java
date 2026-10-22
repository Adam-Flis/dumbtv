package tv.dumbtv.home;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "HDMI 2 just turned on. Switch to it now?": a card at the bottom of the home screen with
 * Switch and Not now. It goes away by itself after 15 s and gives the focus back.
 */
final class InputCard {
    private static final long SHOW_MS = 15_000;
    private static final int WRAP = FrameLayout.LayoutParams.WRAP_CONTENT;

    private final Activity a;
    private final FrameLayout root;
    private final Handler ui;
    /** Where the focus goes when the tile focused before the card is gone. */
    private final Runnable focusHome;
    private final Runnable timeout = this::hide;
    private LinearLayout card;
    private View returnTo;

    InputCard(Activity a, FrameLayout root, Handler ui, Runnable focusHome) {
        this.a = a;
        this.root = root;
        this.ui = ui;
        this.focusHome = focusHome;
    }

    boolean showing() {
        return card != null;
    }

    /** Shows the card for the input named {@code name}; Switch runs {@code switchTo}. */
    void show(CharSequence name, int accent, Runnable switchTo) {
        remove();
        int ink = Ui.ink(accent);
        LinearLayout c = new LinearLayout(a) {
            /** The buttons keep focus to themselves while the card is up. */
            @Override
            public View focusSearch(View focused, int direction) {
                View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
                return next != null ? next : focused;
            }
        };
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(20), dp(18), dp(20), dp(18));
        c.setBackground(Ui.card(a, 0xF51C1D22, 26));
        c.setElevation(dp(24));

        View badge = new View(a);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(accent);
        LayerDrawable art = new LayerDrawable(new Drawable[] {circle, Icons.of(Icons.INPUT, dp(26), ink)});
        art.setLayerInset(1, dp(14), dp(14), dp(14), dp(14));
        badge.setBackground(art);
        c.addView(badge, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(18), 0, dp(30), 0);
        TextView title = Ui.text(a, 21, Ui.TEXT, Ui.MEDIUM);
        title.setText(name + " just turned on");
        TextView sub = Ui.text(a, 15, Ui.MUTED, Ui.MEDIUM);
        sub.setPadding(0, dp(4), 0, 0);
        sub.setText("Switch to it now?");
        words.addView(title);
        words.addView(sub);
        c.addView(words);

        TextView go = Ui.pill(a, "Switch", accent);
        go.setOnClickListener(v -> {
            hide();
            switchTo.run();
        });
        TextView later = Ui.pill(a, "Not now", accent);
        later.setOnClickListener(v -> hide());
        c.addView(go);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(WRAP, WRAP);
        llp.leftMargin = dp(10);
        c.addView(later, llp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(44);
        root.addView(c, lp);
        card = c;
        returnTo = a.getCurrentFocus();
        go.requestFocus();
        Tween.run(320, t -> {
            c.setAlpha(t);
            c.setTranslationY(dp(36) * (1 - t));
        }, null);
        ui.postDelayed(timeout, SHOW_MS);
    }

    /** Fades the card out and gives the focus back. */
    void hide() {
        ui.removeCallbacks(timeout);
        LinearLayout c = card;
        if (c == null) return;
        card = null;
        if (returnTo != null && returnTo.isAttachedToWindow()) returnTo.requestFocus();
        else focusHome.run();
        returnTo = null;
        Tween.run(220, t -> c.setAlpha(1 - t), () -> root.removeView(c));
    }

    /** Takes the card away at once (the home screen is being left). */
    void remove() {
        ui.removeCallbacks(timeout);
        if (card != null) root.removeView(card);
        card = null;
        returnTo = null;
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }
}
