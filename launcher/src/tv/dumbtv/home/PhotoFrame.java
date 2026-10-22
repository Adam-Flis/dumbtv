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
    private static final long SLIDE_MS = 20_000, FADE_MS = 2000;
    private static final int MAX_W = 1920, MAX_H = 1080;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io;
    private final ImageView[] views = new ImageView[2];
    private final TextView clock, date, weather, hint;
    private final List<Object> photos = new ArrayList<>();
    private int next, front;
    private boolean running;
    private Tween fade;
    private final View info;
    private final View scrim;
    private boolean drift, left = true;

    private final Runnable advance = this::loadNext;

    PhotoFrame(Context c, ExecutorService io, File dir) {
        super(c);
        this.io = io;
        setBackgroundColor(0xFF000000);
        for (int i = 0; i < 2; i++) {
            views[i] = new ImageView(c);
            views[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            views[i].setAlpha(0f);
            addView(views[i], new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0xB3000000, 0x00000000}));
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 260), Gravity.BOTTOM));

        LinearLayout box = new LinearLayout(c);
        info = box;
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 56);
        box.setPadding(pad, 0, pad, Ui.dp(c, 44));
        clock = Ui.text(c, 72, Ui.TEXT, Ui.LIGHT);
        date = Ui.text(c, 20, Ui.TEXT, Ui.MEDIUM);
        date.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), 0, 0);
        weather = Ui.text(c, 20, Ui.TEXT, Ui.MEDIUM);
        weather.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), 0, 0);
        hint = Ui.text(c, 20, Ui.TEXT, Ui.MEDIUM);
        hint.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, 0);
        for (TextView t : new TextView[] {clock, date, weather, hint}) {
            t.setShadowLayer(Ui.dp(c, 6), 0, 0, 0x99000000);
            box.addView(t);
        }
        addView(box, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));

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

    /** Clock, date, weather and hint lines; null or empty hides a line. */
    void setInfo(CharSequence time, CharSequence day, CharSequence sky, CharSequence tip) {
        clock.setText(time);
        date.setText(day);
        weather.setText(sky);
        weather.setVisibility(sky == null || sky.length() == 0 ? GONE : VISIBLE);
        hint.setText(tip);
        hint.setVisibility(tip == null || tip.length() == 0 ? GONE : VISIBLE);
    }

    /** Screen care: the clock and date switch corners and shift a little with each picture. */
    void setDrift(boolean on) {
        drift = on;
    }

    private void moveInfo() {
        left = !left;
        LayoutParams lp = (LayoutParams) info.getLayoutParams();
        lp.gravity = Gravity.BOTTOM | (left ? Gravity.START : Gravity.END);
        info.setLayoutParams(lp);
        ((LinearLayout) info).setGravity(left ? Gravity.START : Gravity.END);
        int d = Ui.dp(getContext(), 16);
        info.setTranslationX((float) (Math.random() * 2 - 1) * d);
        info.setTranslationY((float) -Math.random() * d);
        info.setAlpha(0f);
        Tween.run(FADE_MS, info::setAlpha, null);
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
        scrim.bringToFront();
        info.bringToFront();
        if (drift) moveInfo();
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
