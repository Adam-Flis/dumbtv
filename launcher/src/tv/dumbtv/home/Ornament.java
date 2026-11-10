package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Style › Regency: a gilt flourish after each row title, a small lozenge with a hairline that
 * fades out to the right, like the rules in a Regency letter or a theatre programme.
 */
final class Ornament extends Drawable {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path diamond = new Path();
    private final int w, h, color;

    Ornament(int width, int height, int color) {
        this.w = width;
        this.h = height;
        this.color = color;
        mark.setColor(color);
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
        float left = getBounds().left, mid = getBounds().exactCenterY(), r = h / 2.6f;
        // a lozenge with a smaller one beside it, then the rule
        diamond.reset();
        lozenge(left + r, mid, r);
        lozenge(left + r * 3.1f, mid, r * 0.45f);
        c.drawPath(diamond, mark);
        float start = left + r * 4.2f, end = getBounds().right;
        // full gilt for the first half, then fading away
        line.setShader(new LinearGradient(start, 0, end, 0, new int[] {color, color, color & 0x00FFFFFF},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        float half = Math.max(1f, h / 12f);
        c.drawRect(start, mid - half / 2, end, mid + half / 2, line);
    }

    private void lozenge(float cx, float cy, float r) {
        diamond.moveTo(cx - r, cy);
        diamond.lineTo(cx, cy - r);
        diamond.lineTo(cx + r, cy);
        diamond.lineTo(cx, cy + r);
        diamond.close();
    }

    @Override
    public void setAlpha(int alpha) {
        mark.setAlpha(alpha);
        line.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mark.setColorFilter(cf);
        line.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
