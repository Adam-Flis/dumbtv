package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Decodes the user's pictures (wallpapers, screen saver photos) at screen size. One place for
 * the file types we accept and for keeping a huge or oddly shaped picture from running the
 * TV out of memory.
 */
final class Images {
    private Images() {}

    /** Whether {@code name} is a picture we show: .jpg, .jpeg, .png or .webp. */
    static boolean isPicture(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    /**
     * The power-of-two step that shrinks a {@code w} x {@code h} picture until neither side is
     * more than twice the screen's: sharp on screen, and a panorama no longer decodes at full size.
     */
    static int sampleSize(int w, int h, int maxW, int maxH) {
        int s = 1;
        while (w / (s * 2) >= maxW || h / (s * 2) >= maxH) s *= 2;
        return s;
    }

    /**
     * {@code src} (a File, or a path in the APK's assets) decoded to fit {@code maxW} x
     * {@code maxH}; null when it is missing, broken or too big for memory.
     */
    static Bitmap decode(Context c, Object src, int maxW, int maxH, Bitmap.Config format) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        read(c, src, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight, maxW, maxH);
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = format;
        try {
            return read(c, src, o);
        } catch (OutOfMemoryError e) {
            android.util.Log.w("dumbtv", "picture too big: " + src);
            return null;
        }
    }

    private static Bitmap read(Context c, Object src, BitmapFactory.Options o) {
        if (src instanceof File) return BitmapFactory.decodeFile(((File) src).getPath(), o);
        try (InputStream in = c.getAssets().open((String) src)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (IOException e) {
            return null;
        }
    }
}
