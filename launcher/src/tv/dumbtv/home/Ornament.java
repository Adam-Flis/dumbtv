package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The flourish after each row title in the decorated Styles, in the title's own color, then a
 * rule that fades out to the right:
 * <ul>
 *   <li>Regency: a lozenge with a smaller one beside it, like a letter's rule;</li>
 *   <li>Art Deco: a sunburst fan over a double line;</li>
 *   <li>Storybook: a four-point sparkle and a dotted line.</li>
 * </ul>
 */
final class Ornament extends Drawable {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final String kind;
    private final int w, h, color;

    /** {@code kind} is the Style: "regency", "deco" or "storybook". */
    Ornament(String kind, int width, int height, int color) {
        this.kind = kind;
        this.w = width;
        this.h = height;
        this.color = color;
        ink.setColor(color);
        // TextView draws a compound drawable within the bounds it already has
        setBounds(0, 0, width, height);
    }

    @Override
    public int getIntrinsicWidth() {
        return w;
    }

    @Override
    public int getIntrinsicHeight() {
        return h;
    }

    @Override
    public void draw(Canvas c) {
        float left = getBounds().left, mid = getBounds().exactCenterY(), end = getBounds().right;
        float start;
        path.reset();
        switch (kind) {
            case "deco":
                start = fan(left, mid);
                break;
            case "storybook":
                start = sparkle(left, mid);
                break;
            default:
                start = lozenges(left, mid);
        }
        ink.setStyle(Paint.Style.FILL);
        c.drawPath(path, ink);
        // full color for the first half, then fading away
        line.setShader(new LinearGradient(start, 0, end, 0, new int[] {color, color, color & 0x00FFFFFF},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        float t = Math.max(1f, h / 12f);
        if ("deco".equals(kind)) {
            c.drawRect(start, mid - t * 2.2f, end, mid - t * 1.2f, line);
            c.drawRect(start, mid + t * 1.2f, end, mid + t * 2.2f, line);
        } else if ("storybook".equals(kind)) {
            for (float x = start; x < end; x += t * 4.5f) c.drawCircle(x, mid, t * 0.9f, line);
        } else {
            c.drawRect(start, mid - t / 2, end, mid + t / 2, line);
        }
    }

    /** Regency: two lozenges; returns where the rule starts. */
    private float lozenges(float left, float mid) {
        float r = h / 2.6f;
        lozenge(left + r, mid, r);
        lozenge(left + r * 3.1f, mid, r * 0.45f);
        return left + r * 4.2f;
    }

    private void lozenge(float cx, float cy, float r) {
        path.moveTo(cx - r, cy);
        path.lineTo(cx, cy - r);
        path.lineTo(cx + r, cy);
        path.lineTo(cx, cy + r);
        path.close();
    }

    /** Art Deco: a half sunburst of five rays standing on the line; returns where the rule starts. */
    private float fan(float left, float mid) {
        float r = h * 0.62f, cx = left + r, cy = mid + h * 0.18f;
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(200 + i * 35);
            double a0 = a - Math.toRadians(9), a1 = a + Math.toRadians(9);
            path.moveTo(cx, cy);
            path.lineTo(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r);
            path.lineTo(cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r);
            path.close();
        }
        path.addArc(new RectF(cx - r * 0.3f, cy - r * 0.3f, cx + r * 0.3f, cy + r * 0.3f), 180, 180);
        return cx + r + h * 0.25f;
    }

    /** Storybook: a four-point sparkle with a small one beside it; returns where the dots start. */
    private float sparkle(float left, float mid) {
        float r = h * 0.55f;
        star(left + r, mid, r);
        star(left + r * 2.5f, mid - r * 0.45f, r * 0.35f);
        return left + r * 3.2f;
    }

    private void star(float cx, float cy, float r) {
        float k = r * 0.22f;
        path.moveTo(cx, cy - r);
        path.quadTo(cx + k, cy - k, cx + r, cy);
        path.quadTo(cx + k, cy + k, cx, cy + r);
        path.quadTo(cx - k, cy + k, cx - r, cy);
        path.quadTo(cx - k, cy - k, cx, cy - r);
        path.close();
    }

    @Override
    public void setAlpha(int alpha) {
        ink.setAlpha(alpha);
        line.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        ink.setColorFilter(cf);
        line.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
