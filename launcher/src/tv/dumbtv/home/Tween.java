package tv.dumbtv.home;

import android.view.Choreographer;

/**
 * A small frame-by-frame animation. This TV runs with system animations off (animator duration
 * scale 0), which makes ViewPropertyAnimator and ValueAnimator jump straight to the end. Fades
 * that matter (screen saver, background glow, the input card) use this instead: it reads the
 * frame clock directly, so the scale does not apply.
 */
final class Tween implements Choreographer.FrameCallback {
    interface Step {
        /** {@code t} runs from 0 to 1, eased in and out. */
        void at(float t);
    }

    private final long durationNanos;
    private final Step step;
    private final Runnable end;
    /** False: {@code t} runs at an even pace, for steps that bring their own easing. */
    private final boolean eased;
    private long start = -1;
    private boolean running;

    private Tween(long ms, Step step, Runnable end, boolean eased) {
        this.durationNanos = Math.max(1, ms) * 1_000_000L;
        this.step = step;
        this.end = end;
        this.eased = eased;
    }

    static Tween run(long ms, Step step, Runnable end) {
        return start(new Tween(ms, step, end, true));
    }

    /** Like {@link #run}, but {@code t} is not eased: the step applies its own curve. */
    static Tween linear(long ms, Step step, Runnable end) {
        return start(new Tween(ms, step, end, false));
    }

    private static Tween start(Tween t) {
        t.running = true;
        t.step.at(0f);
        Choreographer.getInstance().postFrameCallback(t);
        return t;
    }

    /** Eases out past the end and back (about 8 % over), like a light spring. */
    static float springOut(float t) {
        return springOut(t, 1.4f);
    }

    /** The same with {@code give}: about 8 % over at 1.4, about 25 % at 3. */
    static float springOut(float t, float give) {
        float c3 = give + 1, u = t - 1;
        return 1 + c3 * u * u * u + give * u * u;
    }

    void cancel() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long nanos) {
        if (!running) return;
        if (start < 0) start = nanos;
        float t = Math.min(1f, (nanos - start) / (float) durationNanos);
        step.at(eased ? t * t * (3 - 2 * t) : t);
        if (t < 1f) {
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            running = false;
            if (end != null) end.run();
        }
    }
}
