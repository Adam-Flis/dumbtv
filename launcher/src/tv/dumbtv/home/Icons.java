package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Small icons drawn with paths, so no image files: weather conditions and the row titles.
 * Weather icons keep their own colors (sun amber, rain blue); row icons take {@code tint}.
 */
final class Icons extends Drawable {
    static final int SUN = 0, MOON = 1, PARTLY = 2, PARTLY_NIGHT = 3, CLOUD = 4, FOG = 5, RAIN = 6,
            SNOW = 7, STORM = 8, STAR = 9, GRID = 10, INPUT = 11, WIFI = 12, GEAR = 13, SLIDERS = 14,
            HOUSE = 15, PALETTE = 16, PHOTO = 17, DOTS = 18, BAG = 19, LIBRARY = 20,
            PENCIL = 21;
    private static final int AMBER = 0xFFFFC53D, CLOUDY = 0xFFE3E6EA, BLUE = 0xFF5AB0FF,
            MOONLIGHT = 0xFFEDE7CF;

    private final int kind, tint;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    private Icons(int kind, int tint) {
        this.kind = kind;
        this.tint = tint;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    static Icons of(int kind, int px, int tint) {
        Icons d = new Icons(kind, tint);
        d.setBounds(0, 0, px, px);
        return d;
    }

    /** The icon for a WMO weather code. */
    static int forCode(int c, boolean day) {
        if (c <= 1) return day ? SUN : MOON;
        if (c == 2) return day ? PARTLY : PARTLY_NIGHT;
        if (c == 3) return CLOUD;
        if (c == 45 || c == 48) return FOG;
        if (c >= 71 && c <= 77 || c == 85 || c == 86) return SNOW;
        if (c >= 95) return STORM;
        if (c >= 51) return RAIN;
        return CLOUD;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        c.save();
        c.translate(b.left, b.top);
        switch (kind) {
            case SUN: sun(c, s / 2, s / 2, s * 0.44f, s); break;
            // the bite is on the right, so the crescent sits a little right of center to look centered
            case MOON: moon(c, s * 0.54f, s / 2, s * 0.36f); break;
            case PARTLY:
                sun(c, s * 0.36f, s * 0.36f, s * 0.3f, s);
                cloud(c, s * 0.18f, s * 0.34f, s * 0.8f);
                break;
            case PARTLY_NIGHT:
                moon(c, s * 0.38f, s * 0.34f, s * 0.22f);
                cloud(c, s * 0.18f, s * 0.34f, s * 0.8f);
                break;
            case CLOUD: cloud(c, s * 0.05f, s * 0.2f, s * 0.9f); break;
            case FOG:
                stroke(CLOUDY, s * 0.08f);
                for (int i = 0; i < 3; i++) {
                    float y = s * (0.32f + i * 0.18f);
                    c.drawLine(s * (0.14f + i * 0.04f), y, s * (0.86f - i * 0.04f), y, line);
                }
                break;
            case RAIN:
                cloud(c, s * 0.08f, s * 0.04f, s * 0.84f);
                stroke(BLUE, s * 0.075f);
                for (int i = 0; i < 3; i++) {
                    float x = s * (0.32f + i * 0.2f);
                    c.drawLine(x, s * 0.68f, x - s * 0.06f, s * 0.88f, line);
                }
                break;
            case SNOW:
                cloud(c, s * 0.08f, s * 0.04f, s * 0.84f);
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(s * 0.3f, s * 0.74f, s * 0.055f, fill);
                c.drawCircle(s * 0.5f, s * 0.88f, s * 0.055f, fill);
                c.drawCircle(s * 0.7f, s * 0.74f, s * 0.055f, fill);
                break;
            case STORM:
                cloud(c, s * 0.08f, s * 0.02f, s * 0.84f);
                fill.setColor(AMBER);
                poly(c, s, 0.54f, 0.52f, 0.36f, 0.78f, 0.5f, 0.78f, 0.42f, 0.98f, 0.68f, 0.68f, 0.54f, 0.68f, 0.62f, 0.52f);
                break;
            case STAR: {
                fill.setColor(tint);
                path.reset();
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI / 5 * i - Math.PI / 2;
                    float rad = s * (i % 2 == 0 ? 0.48f : 0.2f);
                    float x = s / 2 + (float) Math.cos(a) * rad, y = s * 0.54f + (float) Math.sin(a) * rad;
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case GRID:
                fill.setColor(tint);
                for (int i = 0; i < 4; i++) {
                    float x = s * (i % 2 == 0 ? 0.08f : 0.54f), y = s * (i < 2 ? 0.08f : 0.54f);
                    r.set(x, y, x + s * 0.38f, y + s * 0.38f);
                    c.drawRoundRect(r, s * 0.09f, s * 0.09f, fill);
                }
                break;
            case WIFI:
                fill.setColor(tint);
                c.drawCircle(s * 0.5f, s * 0.8f, s * 0.08f, fill);
                stroke(tint, s * 0.09f);
                for (float rad : new float[] {0.24f, 0.42f, 0.6f}) {
                    r.set(s * (0.5f - rad), s * (0.8f - rad), s * (0.5f + rad), s * (0.8f + rad));
                    c.drawArc(r, 228, 84, false, line);
                }
                break;
            case GEAR:
                stroke(tint, s * 0.13f);
                c.drawCircle(s / 2, s / 2, s * 0.24f, line);
                line.setStrokeCap(Paint.Cap.BUTT);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                    c.drawLine(s / 2 + cos * s * 0.28f, s / 2 + sin * s * 0.28f,
                            s / 2 + cos * s * 0.46f, s / 2 + sin * s * 0.46f, line);
                }
                line.setStrokeCap(Paint.Cap.ROUND);
                break;
            case SLIDERS: {
                stroke(tint, s * 0.08f);
                fill.setColor(tint);
                float[] knobs = {0.66f, 0.34f, 0.7f};
                for (int i = 0; i < 3; i++) {
                    float y = s * (0.22f + i * 0.28f);
                    c.drawLine(s * 0.1f, y, s * 0.9f, y, line);
                    c.drawCircle(s * knobs[i], y, s * 0.11f, fill);
                }
                break;
            }
            case HOUSE:
                stroke(tint, s * 0.09f);
                path.reset();
                path.moveTo(s * 0.1f, s * 0.5f);
                path.lineTo(s * 0.5f, s * 0.12f);
                path.lineTo(s * 0.9f, s * 0.5f);
                c.drawPath(path, line);
                path.reset();
                path.moveTo(s * 0.22f, s * 0.42f);
                path.lineTo(s * 0.22f, s * 0.88f);
                path.lineTo(s * 0.78f, s * 0.88f);
                path.lineTo(s * 0.78f, s * 0.42f);
                c.drawPath(path, line);
                break;
            case PALETTE:
                stroke(tint, s * 0.08f);
                c.drawCircle(s / 2, s / 2, s * 0.4f, line);
                fill.setColor(tint);
                c.drawCircle(s * 0.36f, s * 0.36f, s * 0.07f, fill);
                c.drawCircle(s * 0.6f, s * 0.32f, s * 0.07f, fill);
                c.drawCircle(s * 0.34f, s * 0.6f, s * 0.07f, fill);
                c.drawCircle(s * 0.64f, s * 0.6f, s * 0.07f, fill);
                break;
            case PHOTO:
                stroke(tint, s * 0.08f);
                r.set(s * 0.08f, s * 0.16f, s * 0.92f, s * 0.84f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, line);
                fill.setColor(tint);
                poly(c, s, 0.18f, 0.76f, 0.4f, 0.48f, 0.56f, 0.64f, 0.66f, 0.54f, 0.82f, 0.76f);
                c.drawCircle(s * 0.68f, s * 0.34f, s * 0.07f, fill);
                break;
            case DOTS:
                fill.setColor(tint);
                for (int i = 0; i < 3; i++) c.drawCircle(s * (0.22f + i * 0.28f), s / 2, s * 0.085f, fill);
                break;
            case PENCIL:
                stroke(tint, s * 0.17f);
                line.setStrokeCap(Paint.Cap.BUTT);
                c.drawLine(s * 0.32f, s * 0.68f, s * 0.78f, s * 0.22f, line);
                line.setStrokeCap(Paint.Cap.ROUND);
                fill.setColor(tint);
                poly(c, s, 0.14f, 0.86f, 0.2f, 0.62f, 0.38f, 0.8f);
                break;
            case BAG:
                stroke(tint, s * 0.08f);
                r.set(s * 0.16f, s * 0.32f, s * 0.84f, s * 0.9f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, line);
                r.set(s * 0.34f, s * 0.1f, s * 0.66f, s * 0.46f);
                c.drawArc(r, 180, 180, false, line);
                fill.setColor(tint);
                c.drawCircle(s * 0.5f, s * 0.61f, s * 0.035f, fill);
                c.drawLine(s * 0.5f, s * 0.52f, s * 0.5f, s * 0.7f, line);
                c.drawLine(s * 0.41f, s * 0.61f, s * 0.59f, s * 0.61f, line);
                break;
            case LIBRARY:
                fill.setColor(tint);
                float[][] books = {{0.1f, 0.22f}, {0.3f, 0.12f}, {0.5f, 0.3f}};
                for (float[] book : books) {
                    r.set(s * book[0], s * book[1], s * (book[0] + 0.15f), s * 0.88f);
                    c.drawRoundRect(r, s * 0.03f, s * 0.03f, fill);
                }
                stroke(tint, s * 0.15f);
                line.setStrokeCap(Paint.Cap.BUTT);
                c.drawLine(s * 0.7f, s * 0.24f, s * 0.86f, s * 0.86f, line);
                line.setStrokeCap(Paint.Cap.ROUND);
                break;
            default: // INPUT: a little TV
                stroke(tint, s * 0.09f);
                r.set(s * 0.08f, s * 0.14f, s * 0.92f, s * 0.7f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, line);
                c.drawLine(s * 0.32f, s * 0.88f, s * 0.68f, s * 0.88f, line);
        }
        c.restore();
    }

    private void stroke(int color, float width) {
        line.setColor(color);
        line.setStrokeWidth(width);
    }

    private void sun(Canvas c, float cx, float cy, float rad, float s) {
        fill.setColor(AMBER);
        c.drawCircle(cx, cy, rad * 0.52f, fill);
        stroke(AMBER, Math.max(1.5f, s * 0.06f));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * rad * 0.74f, cy + sin * rad * 0.74f, cx + cos * rad, cy + sin * rad, line);
        }
    }

    private void moon(Canvas c, float cx, float cy, float rad) {
        fill.setColor(MOONLIGHT);
        path.reset();
        path.addCircle(cx, cy, rad, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + rad * 0.5f, cy - rad * 0.35f, rad * 0.85f, Path.Direction.CW);
        path.op(bite, Path.Op.DIFFERENCE);
        c.drawPath(path, fill);
    }

    /** A cloud {@code w} wide whose top is at {@code y}; it is about 0.62 w tall. */
    private void cloud(Canvas c, float x, float y, float w) {
        fill.setColor(CLOUDY);
        c.drawCircle(x + w * 0.3f, y + w * 0.42f, w * 0.18f, fill);
        c.drawCircle(x + w * 0.55f, y + w * 0.32f, w * 0.24f, fill);
        c.drawCircle(x + w * 0.76f, y + w * 0.46f, w * 0.16f, fill);
        r.set(x + w * 0.12f, y + w * 0.4f, x + w * 0.92f, y + w * 0.62f);
        c.drawRoundRect(r, w * 0.11f, w * 0.11f, fill);
    }

    private void poly(Canvas c, float s, float... xy) {
        path.reset();
        path.moveTo(xy[0] * s, xy[1] * s);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i] * s, xy[i + 1] * s);
        path.close();
        c.drawPath(path, fill);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        line.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        line.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return getBounds().width();
    }

    @Override
    public int getIntrinsicHeight() {
        return getBounds().height();
    }
}
