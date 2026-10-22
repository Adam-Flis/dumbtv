package tv.dumbtv.home;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screen saver as Android's own screensaver ("dream"), so it also starts when another app
 * sits idle (a paused video), not only on the home screen. Fire OS starts it instead of
 * sleeping once it is chosen as the screensaver (an adb setting, see README). It shows the same
 * PhotoFrame, follows the same settings and screen care, then ends after Stop after and lets
 * the TV sleep. Off, or at night, it ends at once so the TV sleeps as before.
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
        // off, at night, or the home screen's screen saver just ran its course: let the TV sleep
        boolean justRan = System.currentTimeMillis() - PhotoFrame.endedAt < 2 * 60_000L;
        if (!cfg.frameOn() || cfg.nightNow() || justRan) {
            finish();
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
        String lat = cfg.get(Weather.LAT, ""), lon = cfg.get(Weather.LON, "");
        if (!cfg.on(Config.WEATHER) || lat.isEmpty() || Weather.latest() != null) return;
        boolean f = "f".equals(cfg.get(Config.UNITS));
        boolean h24 = cfg.clock24(this);
        io.execute(() -> {
            try {
                Weather.fetch(lat, lon, f, h24);
                ui.post(this::refresh);
            } catch (Exception e) {
                android.util.Log.w("dumbtv", "dream weather: " + e);
            }
        });
    }

    private void refresh() {
        if (frame != null) frame.update(cfg, cfg.on(Config.WEATHER) ? Weather.latest() : null);
    }
}
