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
    private long start = -1;
    private boolean running;

    private Tween(long ms, Step step, Runnable end) {
        this.durationNanos = Math.max(1, ms) * 1_000_000L;
        this.step = step;
        this.end = end;
    }

    static Tween run(long ms, Step step, Runnable end) {
        Tween t = new Tween(ms, step, end);
        t.running = true;
        step.at(0f);
        Choreographer.getInstance().postFrameCallback(t);
        return t;
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
        step.at(t * t * (3 - 2 * t));
        if (t < 1f) {
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            running = false;
            if (end != null) end.run();
        }
    }
}
