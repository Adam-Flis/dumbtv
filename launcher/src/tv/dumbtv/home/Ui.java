package tv.dumbtv.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFF2F2F3;
    static final int MUTED = 0x99FFFFFF;
    static final int FAINT = 0x66FFFFFF;
    static final int SURFACE = 0xFF1C1D21;
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    /** Options > Text size; set before building a screen. */
    static float scale = 1f;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** {@code a} blended toward {@code b} by {@code t} (0 = a, 1 = b). */
    static int mix(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    /**
     * The color an app's banner is known by: its colorful pixels averaged by hue (white and black
     * backgrounds count for little), then set to a rich but dark tone white text reads well on.
     */
    static int vibrant(android.graphics.drawable.Drawable art) {
        int w = 32, h = 18;
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Rect keep = art.copyBounds();
        art.setBounds(0, 0, w, h);
        art.draw(new android.graphics.Canvas(b));
        art.setBounds(keep);
        float[] hsv = new float[3];
        double x = 0, y = 0, sat = 0, weight = 0;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int p = b.getPixel(i, j);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double k = hsv[1] * hsv[1] * Math.min(1f, hsv[2] * 1.6f);
                double a = Math.toRadians(hsv[0]);
                x += Math.cos(a) * k;
                y += Math.sin(a) * k;
                sat += hsv[1] * k;
                weight += k;
            }
        }
        b.recycle();
        // logos in black, white or grey get a calm slate blue
        if (weight < w * h * 0.03) return 0xFF2B3A67;
        float hue = (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
        float s = (float) Math.max(0.5, Math.min(0.85, sat / weight));
        return Color.HSVToColor(new float[] {hue, s, 0.46f});
    }

    /** Black on light accents, white on dark ones. */
    static int ink(int color) {
        // 0.36 rather than 0.5: cyan, green and amber read far better with black than white
        return Color.luminance(color) > 0.36f ? 0xFF000000 : 0xFFFFFFFF;
    }

    static TextView text(Context c, float sp, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Top-bar buttons: accent fill when focused; otherwise a visible fill with a thin outline. */
    static void chipStyle(TextView v, int accent, float radius, int stroke) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(accent, radius));
        GradientDrawable idle = round(0x2EFFFFFF, radius);
        idle.setStroke(stroke, 0x33FFFFFF);
        bg.addState(new int[0], idle);
        v.setBackground(bg);
        v.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {ink(accent), 0xFFFFFFFF}));
    }

    /** Accent fill when focused, a faint fill otherwise. */
    static void focusStyle(TextView v, int accent, int idle, float radius) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(accent, radius));
        bg.addState(new int[0], round(idle, radius));
        v.setBackground(bg);
        v.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {ink(accent), 0xD9FFFFFF}));
    }
}
