package tv.dumbtv.home;

import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Burn-in care for OLED TVs (Options › Night & photos › Screen care): the home screen drifts a
 * couple of pixels every few minutes, and after a quiet spell it fades to near black until a
 * button is pressed. Everything here hangs off Config.CARE and Config.REST.
 */
final class ScreenCare {
    /** True while going dark would get in the way: the photo frame runs, a menu is open. */
    interface Busy {
        boolean now();
    }

    private static final long SHIFT_MS = 3 * 60_000L, RETRY_MS = 60_000L;
    private static final float DARK = 0.92f;
    /** Nine spots, at most 2 dp from the home screen's own place. */
    private static final int[][] SHIFTS = {{0, 0}, {2, 0}, {2, 2}, {0, 2}, {-2, 2}, {-2, 0}, {-2, -2}, {0, -2}, {2, -2}};

    private final Config cfg;
    private final Handler ui;
    private final FrameLayout root;
    private final View page;
    private final Busy busy;
    private final Runnable shift = this::shift, rest = this::rest;
    private int step;
    private boolean running;
    private View dark;

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

    /** Something happened: restart the quiet-spell countdown, or hold it while not {@code allowed}. */
    void idle(boolean allowed) {
        ui.removeCallbacks(rest);
        if (running && allowed && dark == null && on()) {
            ui.postDelayed(rest, Integer.parseInt(cfg.get(Config.REST)) * 60_000L);
        }
    }

    /** Goes dark after {@code ms} regardless of the setting's delay (the photo frame ended, tests). */
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
        if (!on()) {
            page.setTranslationX(0);
            page.setTranslationY(0);
            return;
        }
        step = (step + 1) % SHIFTS.length;
        page.setTranslationX(Ui.dp(page.getContext(), SHIFTS[step][0]));
        page.setTranslationY(Ui.dp(page.getContext(), SHIFTS[step][1]));
        if (running) ui.postDelayed(shift, SHIFT_MS);
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
