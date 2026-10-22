package tv.dumbtv.home;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screen saver as Android's own screensaver ("dream"), so it starts after the same idle time
 * on the home screen and in any other app (a paused video). SystemSaver makes it Fire TV's
 * screensaver and sets the timers. It shows the same PhotoFrame with the same settings and
 * screen care, then ends after "Sleep after saver" and the TV sleeps. With the screen saver off
 * it ends at once; at night it stays black until the usual no-screen-saver sleep time.
 */
public final class FrameDream extends DreamService {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;
    private final Runnable done = this::finish;
    private ExecutorService io;
    private Config cfg;
    private PhotoFrame frame;
    private ScreenCare care;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // any key ends the dream and goes back to the paused app
        setFullscreen(true);
        cfg = Config.get(this);
        cfg.refresh();
        // off, or the home screen's screen saver just ran its course: let the TV sleep now
        boolean justRan = System.currentTimeMillis() - PhotoFrame.endedAt < 2 * 60_000L;
        if (!cfg.frameOn() || justRan) {
            finish();
            return;
        }
        if (cfg.nightNow()) {
            // no pictures at night; a black screen until the usual no-screen-saver sleep time
            setContentView(new android.view.View(this));
            int wait = SystemSaver.NO_SAVER_SLEEP_MIN - SystemSaver.startMinutes(cfg);
            ui.postDelayed(done, Math.max(0, wait) * 60_000L);
            return;
        }
        io = Executors.newSingleThreadExecutor();
        frame = new PhotoFrame(this, io, cfg.dir, cfg.get(Config.FRAME_CLOCK), cfg.get(Config.FRAME_WEATHER));
        setContentView(frame);
        care = new ScreenCare(cfg, ui, frame, null, () -> false);
        care.follow(frame.overlay());
        care.start();
        frame.start();
        tick();
        fetchWeather();
        ui.postDelayed(done, Integer.parseInt(cfg.get(Config.FRAME_FOR)) * 60_000L);
    }

    @Override
    public void onDetachedFromWindow() {
        ui.removeCallbacks(tick);
        ui.removeCallbacks(done);
        if (care != null) care.stop();
        if (frame != null) frame.stop();
        if (io != null) io.shutdownNow();
        super.onDetachedFromWindow();
    }

    /** Clock every 30 s; the weather the home screen fetched last (or the one fetched here). */
    private void tick() {
        refresh();
        ui.postDelayed(tick, 30_000L);
    }

    /** This process may be fresh (Fire OS stops idle apps): fetch the weather once if none is cached. */
    private void fetchWeather() {
        String key = Weather.key(cfg, this);
        if (key == null || Weather.latest(key) != null) return;
        io.execute(() -> {
            try {
                Weather.fetch(key);
                ui.post(this::refresh);
            } catch (Exception e) {
                android.util.Log.w("dumbtv", "dream weather: " + e);
            }
        });
    }

    private void refresh() {
        if (frame != null) frame.update(cfg, Weather.latest(Weather.key(cfg, this)));
    }
}
