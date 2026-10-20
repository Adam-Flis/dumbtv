package tv.dumbtv.home;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Tiles in rows that wrap: as many columns as fit, then the next line. Children are the tiles
 * themselves (no row containers), so moving and indexing work just like in a one-line strip.
 */
final class Grid extends ViewGroup {
    private final int gapX, gapY;
    private int cols = 1, spread;

    Grid(Context c, int gapX, int gapY) {
        super(c);
        this.gapX = gapX;
        this.gapY = gapY;
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int avail = width - getPaddingLeft() - getPaddingRight();
        int cw = 0, ch = 0, n = getChildCount();
        for (int i = 0; i < n; i++) {
            View c = getChildAt(i);
            c.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            cw = Math.max(cw, c.getMeasuredWidth());
            ch = Math.max(ch, c.getMeasuredHeight());
        }
        cols = cw == 0 ? 1 : Math.max(1, (avail + gapX) / (cw + gapX));
        // spread the columns so the grid ends at the same right edge as the header
        spread = cols > 1 && n >= cols ? Math.max(gapX, (avail - cols * cw) / (cols - 1)) : gapX;
        int lines = n == 0 ? 0 : (n + cols - 1) / cols;
        int height = getPaddingTop() + getPaddingBottom() + lines * ch + Math.max(0, lines - 1) * gapY;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            int x = getPaddingLeft() + (i % cols) * (c.getMeasuredWidth() + spread);
            int y = getPaddingTop() + (i / cols) * (c.getMeasuredHeight() + gapY);
            c.layout(x, y, x + c.getMeasuredWidth(), y + c.getMeasuredHeight());
        }
    }
}
