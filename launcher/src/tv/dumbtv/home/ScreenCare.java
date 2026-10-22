package tv.dumbtv.home;

import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Burn-in care for OLED TVs (Options › Screen saver › Screen care): the home screen and the
 * screen saver's clock and weather drift a couple of pixels every few minutes, the screen
 * saver's text is drawn a little dimmer, and when the screen saver ends the screen goes black
 * until the TV sleeps. Everything here hangs off Config.CARE.
 */
final class ScreenCare {
    /** True while going dark would get in the way: the screen saver runs, a menu is open. */
    interface Busy {
        boolean now();
    }

    private static final long SHIFT_MS = 3 * 60_000L, RETRY_MS = 60_000L;
    private static final float DARK = 0.92f;
    /** Layers that stay put for a long time (the screen saver's clock and weather) run a little dimmer. */
    private static final float STATIC_ALPHA = 0.85f;
    /** Nine spots, at most 2 dp from the home screen's own place. */
    private static final int[][] SHIFTS = {{0, 0}, {2, 0}, {2, 2}, {0, 2}, {-2, 2}, {-2, 0}, {-2, -2}, {0, -2}, {2, -2}};

    private final Config cfg;
    private final Handler ui;
    private final FrameLayout root;
    private final View page;
    private final Busy busy;
    private final Runnable shift = this::shift, rest = this::rest;
    /** Other layers that drift with the page, such as the screen saver's clock and weather. */
    private final java.util.List<View> followers = new java.util.ArrayList<>();
    private int step;
    private boolean running;
    private View dark;

    /** {@code page} (may be null) is the main layer that drifts; {@code root} receives the dark layer. */
    ScreenCare(Config cfg, Handler ui, FrameLayout root, View page, Busy busy) {
        this.cfg = cfg;
        this.ui = ui;
        this.root = root;
        this.page = page;
        this.busy = busy;
    }

    boolean on() {
        return cfg.on(Config.CARE);
    }

    /**
     * {@code v} drifts with the home screen until {@link #unfollow}, and is drawn slightly dimmer
     * while screen care is on: the two burn-in rules for anything that stays on screen.
     */
    void follow(View v) {
        followers.add(v);
        v.setAlpha(on() ? STATIC_ALPHA : 1f);
        place(v);
    }

    void unfollow(View v) {
        followers.remove(v);
    }

    /** The home screen is showing: start drifting. */
    void start() {
        running = true;
        shift();
    }

    /** The home screen is gone: stop everything and drop the dark layer at once. */
    void stop() {
        running = false;
        ui.removeCallbacks(shift);
        ui.removeCallbacks(rest);
        if (dark != null) root.removeView(dark);
        dark = null;
    }

    /** Goes dark after {@code ms} regardless of the setting's delay (the screen saver ended, tests). */
    void darkIn(long ms) {
        ui.removeCallbacks(rest);
        ui.postDelayed(rest, ms);
    }

    boolean isDark() {
        return dark != null;
    }

    /** Brings the screen back; false when it was not dark. */
    boolean wake() {
        View going = dark;
        dark = null;
        if (going == null) return false;
        Tween.run(300, t -> going.setAlpha(DARK * (1 - t)), () -> root.removeView(going));
        return true;
    }

    private void shift() {
        ui.removeCallbacks(shift);
        if (on()) step = (step + 1) % SHIFTS.length;
        if (page != null) place(page);
        for (View v : followers) place(v);
        if (running && on()) ui.postDelayed(shift, SHIFT_MS);
    }

    /** Moves {@code v} to the current spot, or back home when screen care is off. */
    private void place(View v) {
        boolean on = on();
        v.setTranslationX(on ? Ui.dp(v.getContext(), SHIFTS[step][0]) : 0);
        v.setTranslationY(on ? Ui.dp(v.getContext(), SHIFTS[step][1]) : 0);
    }

    private void rest() {
        if (dark != null || !running || !on()) return;
        if (busy.now()) {
            ui.postDelayed(rest, RETRY_MS);
            return;
        }
        View v = new View(root.getContext());
        v.setBackgroundColor(0xFF000000);
        v.setAlpha(0f);
        root.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        dark = v;
        Tween.run(2500, t -> v.setAlpha(DARK * t), null);
    }
}
