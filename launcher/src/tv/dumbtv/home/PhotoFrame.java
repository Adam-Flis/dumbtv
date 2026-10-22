package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * The idle screen: photos cross-fade (2 s) every 20 s, clock and weather on top.
 * Pictures come from {@code photos/} next to home.conf, or the bundled ones when that is empty.
 * Only two bitmaps exist at a time, decoded at 1080p in RGB_565 (about 4 MB each).
 */
final class PhotoFrame extends FrameLayout {
    static final String FOLDER = "photos";
    /**
     * When the home screen's own screen saver last ran out (ms). The system dream starts on the
     * same idle timer right after, so it checks this and lets the TV sleep instead of repeating.
     */
    static volatile long endedAt;
    private static final long SLIDE_MS = 20_000, FADE_MS = 2000;
    private static final int MAX_W = 1920, MAX_H = 1080;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io;
    private final ImageView[] views = new ImageView[2];
    private final TextView clock, date, temp, sky, hint;
    /** The weather icon beside the temperature. */
    private final View icon;
    private final View weatherGroup;
    private final List<Object> photos = new ArrayList<>();
    private int next, front;
    private boolean running;
    private Tween fade;
    /** Clock and weather, one full-screen layer over the photos: screen care drifts it as a whole. */
    private final FrameLayout overlay;
    private final List<View> scrims = new ArrayList<>();

    private final Runnable advance = this::loadNext;

    /**
     * {@code clockAt} and {@code weatherAt} are corners (Config.FRAME_CLOCK / FRAME_WEATHER:
     * "tl", "tr", "bl", "br") or "off". Both in one corner stack, clock first.
     */
    PhotoFrame(Context c, ExecutorService io, File dir, String clockAt, String weatherAt) {
        super(c);
        this.io = io;
        setBackgroundColor(0xFF000000);
        for (int i = 0; i < 2; i++) {
            views[i] = new ImageView(c);
            views[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            views[i].setAlpha(0f);
            addView(views[i], new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        // a soft shade along each edge that carries text keeps it readable over bright photos
        for (boolean top : new boolean[] {true, false}) {
            if (!onEdge(clockAt, top) && !onEdge(weatherAt, top)) continue;
            View scrim = new View(c);
            scrim.setBackground(new GradientDrawable(top ? GradientDrawable.Orientation.TOP_BOTTOM
                    : GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xB3000000, 0x00000000}));
            addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 260), top ? Gravity.TOP : Gravity.BOTTOM));
            scrims.add(scrim);
        }

        overlay = new FrameLayout(c);
        addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout time = group(c, clockAt);
        clock = line(c, time, 72, Ui.LIGHT, 0);
        date = line(c, time, 20, Ui.MEDIUM, 6);

        LinearLayout weather = group(c, weatherAt);
        // icon and temperature on one line, like the home screen
        LinearLayout now = new LinearLayout(c);
        now.setGravity(Gravity.CENTER_VERTICAL);
        icon = new View(c);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52));
        ilp.rightMargin = Ui.dp(c, 14);
        now.addView(icon, ilp);
        temp = Ui.text(c, 48, Ui.TEXT, Ui.LIGHT);
        temp.setShadowLayer(Ui.dp(c, 6), 0, 0, 0x99000000);
        now.addView(temp);
        weather.addView(now);
        sky = line(c, weather, 20, Ui.MEDIUM, 6);
        hint = line(c, weather, 18, Ui.MEDIUM, 4);
        weatherGroup = weather;

        // one box per corner in use; two groups in the same corner share it
        java.util.Map<String, LinearLayout> corners = new java.util.HashMap<>();
        place(c, corners, time, clockAt);
        place(c, corners, weather, weatherAt);

        File[] own = new File(dir, FOLDER).listFiles();
        if (own != null) {
            Arrays.sort(own);
            for (File f : own) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                        || n.endsWith(".webp"))) {
                    photos.add(f);
                }
            }
        }
        if (photos.isEmpty()) {
            try {
                String[] bundled = c.getAssets().list(FOLDER);
                if (bundled != null) {
                    for (String n : bundled) {
                        if (n.endsWith(".jpg")) photos.add(FOLDER + "/" + n);
                    }
                }
            } catch (IOException ignored) {
                // no bundled photos: the frame shows just the clock on black
            }
        }
        Collections.shuffle(photos);
    }

    /**
     * Clock and date; the weather icon ({@code iconKind}, or -1 for none), temperature,
     * conditions and the heads-up. Null hides a line; no temperature hides the weather.
     */
    private void setInfo(CharSequence time, CharSequence day, int iconKind, CharSequence degrees, CharSequence conditions,
            CharSequence tip) {
        clock.setText(time);
        date.setText(day);
        icon.setBackground(iconKind < 0 ? null : Icons.of(iconKind, Ui.dp(getContext(), 52), Ui.TEXT));
        icon.setVisibility(iconKind < 0 ? GONE : VISIBLE);
        show(temp, degrees);
        show(sky, conditions);
        show(hint, tip);
        weatherGroup.setVisibility(degrees == null ? GONE : VISIBLE);
    }

    /**
     * Fills the overlay from the clock and a forecast ({@code w} null: no weather). Shared by the
     * home screen's screen saver and the system one (FrameDream), so both read the same.
     */
    void update(Config cfg, Weather.Report w) {
        java.util.Date now = new java.util.Date();
        setInfo(Clock.time(cfg.clock24(getContext()), now), Clock.date(now),
                w == null ? -1 : Icons.forCode(w.code, w.day), w == null ? null : w.temp + "\u00B0",
                w == null ? null : Weather.summary(w), w == null ? null : w.hint);
    }

    /** The clock and weather layer, for screen care to drift. */
    View overlay() {
        return overlay;
    }

    private static void show(TextView v, CharSequence text) {
        v.setText(text);
        v.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
    }

    private static boolean onEdge(String corner, boolean top) {
        return !"off".equals(corner) && corner.startsWith(top ? "t" : "b");
    }

    /** A group's own column (clock or weather), aligned to the side of its corner. */
    private static LinearLayout group(Context c, String corner) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setGravity(corner.endsWith("r") ? Gravity.END : Gravity.START);
        return g;
    }

    /** Puts {@code g} in its corner's box, making the box on first use; "off" leaves it out. */
    private void place(Context c, java.util.Map<String, LinearLayout> corners, LinearLayout g, String corner) {
        if ("off".equals(corner)) return;
        boolean top = corner.startsWith("t"), right = corner.endsWith("r");
        LinearLayout box = corners.get(corner);
        if (box == null) {
            box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(right ? Gravity.END : Gravity.START);
            int side = Ui.dp(c, 56), gap = Ui.dp(c, 44);
            box.setPadding(side, top ? gap : 0, side, top ? 0 : gap);
            overlay.addView(box, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    (top ? Gravity.TOP : Gravity.BOTTOM) | (right ? Gravity.END : Gravity.START)));
            corners.put(corner, box);
        } else {
            g.setPadding(0, Ui.dp(c, 24), 0, 0);
        }
        box.addView(g);
    }

    private static TextView line(Context c, LinearLayout box, float sp, android.graphics.Typeface face, int topDp) {
        TextView t = Ui.text(c, sp, Ui.TEXT, face);
        t.setPadding(0, Ui.dp(c, topDp), 0, 0);
        t.setShadowLayer(Ui.dp(c, 6), 0, 0, 0x99000000);
        box.addView(t);
        return t;
    }

    void start() {
        running = true;
        loadNext();
    }

    /** Stops changing pictures; the current one stays on screen. */
    void pause() {
        running = false;
        ui.removeCallbacks(advance);
    }

    /** Stops and lets the bitmaps go. */
    void stop() {
        pause();
        if (fade != null) fade.cancel();
        for (ImageView v : views) v.setImageDrawable(null);
    }

    private void loadNext() {
        if (!running || photos.isEmpty()) return;
        Object src = photos.get(next);
        next = (next + 1) % photos.size();
        io.execute(() -> {
            Bitmap b = decode(src);
            ui.post(() -> {
                if (!running) return;
                if (b != null) show(b);
                ui.postDelayed(advance, b != null ? SLIDE_MS : 1000);
            });
        });
    }

    private void show(Bitmap b) {
        ImageView in = views[1 - front], out = views[front];
        front = 1 - front;
        if (fade != null) fade.cancel();
        in.setImageBitmap(b);
        in.setAlpha(0f);
        // the incoming picture fades in on top of the outgoing one, under the scrim and text
        in.bringToFront();
        for (View scrim : scrims) scrim.bringToFront();
        overlay.bringToFront();
        // a slow cross-fade, drawn frame by frame so it works with system animations off
        float from = out.getAlpha();
        fade = Tween.run(FADE_MS, t -> {
            in.setAlpha(t);
            out.setAlpha(from * (1 - t));
        }, () -> out.setImageDrawable(null));
    }

    private Bitmap decode(Object src) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        read(src, o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= MAX_W && o.outHeight / (o.inSampleSize * 2) >= MAX_H) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return read(src, o);
    }

    private Bitmap read(Object src, BitmapFactory.Options o) {
        if (src instanceof File) return BitmapFactory.decodeFile(((File) src).getPath(), o);
        try (InputStream in = getContext().getAssets().open((String) src)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (IOException e) {
            return null;
        }
    }
}
