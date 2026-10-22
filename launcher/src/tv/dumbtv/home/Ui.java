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
    // One place for the colors that carry meaning; change them here, not where they are used.
    /** Working / online / ready. */
    static final int GOOD = 0xFF34C759;
    /** Needs attention / offline. */
    static final int WARN = 0xFFFFB340;
    /** Something is running: the sleep timer, night mode. */
    static final int ACTIVE = 0xFFFFC53D;
    /** Dialog and card fill, and their thin outline. */
    static final int PANEL = 0xFA1C1D22, OUTLINE = 0x29FFFFFF;
    /** Top-bar dropdown fill. */
    static final int MENU = 0xF5202126;
    /** Thin dividers. */
    static final int RULE = 0x24FFFFFF;
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    /** Options > Text size; set before building a screen. */
    static float scale = 1f;

    private Ui() {}

    /**
     * A ScrollView that leaves up/down to Android's focus search, which plays the navigation
     * sound; ScrollView's own arrow handling moves focus silently. Scrolling still follows focus.
     */
    static class SoundScroll extends android.widget.ScrollView {
        SoundScroll(Context c) {
            super(c);
        }

        @Override
        public boolean executeKeyEvent(android.view.KeyEvent e) {
            int code = e.getKeyCode();
            if (code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return false;
            }
            return super.executeKeyEvent(e);
        }
    }

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

    /** A rounded card with the thin outline dialogs, notices and the input card share. */
    static GradientDrawable card(Context c, int fill, float radiusDp) {
        GradientDrawable d = round(fill, dp(c, radiusDp));
        d.setStroke(Math.max(1, dp(c, 1)), OUTLINE);
        return d;
    }

    /**
     * A colored dot sized for a compound drawable. Drawn, not text: Fire OS "high contrast
     * text" repaints colored text black or white.
     */
    static GradientDrawable dot(int color, int px) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setBounds(0, 0, px, px);
        return d;
    }

    /** A one-pixel divider for a vertical LinearLayout, inset by the given margins (dp). */
    static android.view.View rule(Context c, int color, float sideDp, float topDp, float bottomDp) {
        android.view.View v = new android.view.View(c);
        v.setBackgroundColor(color);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        lp.setMargins(dp(c, sideDp), dp(c, topDp), dp(c, sideDp), dp(c, bottomDp));
        v.setLayoutParams(lp);
        return v;
    }

    /** A list row's background: a soft accent tint when focused ({@code strong}: full accent). */
    static StateListDrawable rowFocus(int accent, float radius, boolean strong) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused},
                round(strong ? accent : (accent & 0x00FFFFFF) | 0x33000000, radius));
        bg.addState(new int[0], round(0, radius));
        return bg;
    }

    /** A pill button (Save, Got it, Start): accent fill when focused, outlined otherwise. */
    static TextView pill(Context c, String label, int accent) {
        TextView b = text(c, 17, TEXT, MEDIUM);
        b.setId(android.view.View.generateViewId());
        b.setText(label);
        b.setGravity(android.view.Gravity.CENTER);
        b.setMinWidth(dp(c, 120));
        b.setPadding(dp(c, 22), dp(c, 11), dp(c, 22), dp(c, 11));
        b.setFocusable(true);
        b.setClickable(true);
        chipStyle(b, accent, dp(c, 22), Math.max(1, dp(c, 1)));
        return b;
    }

    /**
     * Fills {@code bar} with remote-button hints: a key cap, then what it does.
     * {@code pairs} is key, text, key, text...
     */
    static void hints(android.widget.LinearLayout bar, String... pairs) {
        Context c = bar.getContext();
        bar.removeAllViews();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            TextView key = text(c, 13, TEXT, MEDIUM);
            key.setText(pairs[i]);
            key.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 5));
            GradientDrawable cap = round(RULE, dp(c, 8));
            cap.setStroke(Math.max(1, dp(c, 1)), 0x47FFFFFF);
            key.setBackground(cap);
            TextView what = text(c, 15, 0xCCFFFFFF, MEDIUM);
            what.setSingleLine();
            what.setText(pairs[i + 1]);
            what.setPadding(dp(c, 9), 0, dp(c, 22), 0);
            bar.addView(key);
            bar.addView(what);
        }
    }

    /** Like {@link #hints}, one key per line. */
    static void hintLines(android.widget.LinearLayout box, String... pairs) {
        box.removeAllViews();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            android.widget.LinearLayout line = new android.widget.LinearLayout(box.getContext());
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            line.setPadding(0, dp(box.getContext(), 5), 0, dp(box.getContext(), 5));
            hints(line, pairs[i], pairs[i + 1]);
            box.addView(line);
        }
    }

    /**
     * A short message in a pill at the bottom of the screen, styled like the rest of the UI
     * (Android's toast is a grey box). Fades in, stays 2.6 s, fades out. {@code icon} may be -1.
     */
    static void notice(android.app.Activity a, String text, int icon) {
        android.view.View content = a.findViewById(android.R.id.content);
        if (!(content instanceof android.widget.FrameLayout)) return;
        android.widget.FrameLayout root = (android.widget.FrameLayout) content;
        android.view.View old = root.findViewWithTag(NOTICE);
        if (old != null) root.removeView(old);

        android.widget.LinearLayout pill = new android.widget.LinearLayout(a);
        pill.setTag(NOTICE);
        pill.setGravity(android.view.Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(a, icon < 0 ? 26 : 20), dp(a, 14), dp(a, 26), dp(a, 14));
        pill.setBackground(card(a, 0xF51C1D22, 30));
        pill.setElevation(dp(a, 20));
        if (icon >= 0) {
            android.view.View art = new android.view.View(a);
            art.setBackground(Icons.of(icon, dp(a, 24), Config.get(a).accent()));
            android.widget.LinearLayout.LayoutParams ilp = new android.widget.LinearLayout.LayoutParams(dp(a, 24), dp(a, 24));
            ilp.rightMargin = dp(a, 14);
            pill.addView(art, ilp);
        }
        TextView t = text(a, 18, TEXT, MEDIUM);
        t.setText(text);
        pill.addView(t);

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(a, 56);
        root.addView(pill, lp);
        float rise = dp(a, 24);
        Tween.run(260, f -> {
            pill.setAlpha(f);
            pill.setTranslationY(rise * (1 - f));
        }, null);
        pill.postDelayed(() -> Tween.run(300, f -> pill.setAlpha(1 - f), () -> root.removeView(pill)), 2600);
    }

    private static final String NOTICE = "dumbtv.notice";

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
