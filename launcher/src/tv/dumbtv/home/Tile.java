package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;

/** A card drawn in one pass: banner, or icon plus name, or an input name; focus ring on top. */
final class Tile extends View {
    private static final float FOCUS_SCALE = 1.1f;
    /** The lift: a spring that overshoots a little on the way up; a quick settle on the way down. */
    private static final long LIFT_MS = 420, BOUNCE_MS = 600, RISE_MS = 220, DROP_MS = 160, SHEEN_MS = 750,
            GLOW_MS = 1600;

    final Item item;
    final String kind;
    private final CharSequence title;
    private final int cardW, cardH, labelH;
    private final boolean labelAlways;
    private final float radius, stroke;
    private final RectF card = new RectF();
    private final Path clip = new Path();
    private final Path arrow = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint inner = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint under = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private boolean moving;
    /** A corner badge: "✓" draws a check mark, anything else is drawn as text (an order number). */
    private String badge;
    private final int accent;
    private final Paint badgeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** A band of light that sweeps across the card once when it is selected. */
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sheenAt = new Matrix();
    /** Where the sweep is, 0 to 1; below 0 when none is running. */
    private float sweep = -1;
    private Tween lift, sweepTween, glowTween;
    /** Tile motion › Glow: how bright the halo is, 0 (none) to 1. */
    private float glow;
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);

    Tile(Context c, Item item, String kind, CharSequence title, int cardW, int cardH,
            String labels, int accent) {
        super(c);
        this.item = item;
        this.kind = kind;
        this.title = title;
        this.cardW = cardW;
        this.cardH = cardH;
        boolean app = !Catalog.INPUTS.equals(kind);
        // "focus" shows the name large above the rows instead, so no room is kept under the tile
        labelH = app && "always".equals(labels) ? Ui.dp(c, 26 * Ui.scale) : 0;
        labelAlways = "always".equals(labels);
        radius = Ui.dp(c, 14);
        stroke = Ui.dp(c, 2.5f);

        fill.setColor(app ? Ui.SURFACE : 0xFF17181B);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setColor(accent);
        this.accent = accent;
        badgeFill.setColor(accent);
        badgeMark.setColor(Ui.ink(accent));
        badgeMark.setTypeface(Ui.MEDIUM);
        badgeMark.setTextAlign(Paint.Align.CENTER);
        badgeMark.setTextSize(Ui.dp(c, 15));
        badgeMark.setStrokeWidth(Ui.dp(c, 2.5f));
        badgeMark.setStrokeCap(Paint.Cap.ROUND);
        badgeMark.setStrokeJoin(Paint.Join.ROUND);
        dot.setColor(Ui.GOOD);
        float band = cardW * 0.45f;
        sheen.setShader(new LinearGradient(0, 0, band, 0, new int[] {0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        inner.setColor(Ui.TEXT);
        inner.setTypeface(Ui.MEDIUM);
        inner.setTextAlign(Paint.Align.CENTER);
        inner.setTextSize(Ui.dp(c, (app ? 14 : 17) * Ui.scale));
        under.setColor(Ui.TEXT);
        under.setTypeface(Ui.MEDIUM);
        under.setTextAlign(Paint.Align.CENTER);
        under.setTextSize(Ui.dp(c, 13 * Ui.scale));

        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(title);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, cardW, cardH, radius);
            }
        });
    }

    CharSequence title() {
        return title;
    }

    void setBadge(String b) {
        badge = b;
        invalidate();
    }

    private void drawBadge(Canvas canvas) {
        float r = Ui.dp(getContext(), 14), cx = cardW - r - Ui.dp(getContext(), 7), cy = r + Ui.dp(getContext(), 7);
        canvas.drawCircle(cx, cy, r, badgeFill);
        if ("\u2713".equals(badge)) {
            badgeMark.setStyle(Paint.Style.STROKE);
            arrow.reset();
            arrow.moveTo(cx - r * 0.42f, cy + r * 0.02f);
            arrow.lineTo(cx - r * 0.1f, cy + r * 0.34f);
            arrow.lineTo(cx + r * 0.45f, cy - r * 0.3f);
            canvas.drawPath(arrow, badgeMark);
        } else {
            badgeMark.setStyle(Paint.Style.FILL);
            canvas.drawText(badge, cx, cy - (badgeMark.ascent() + badgeMark.descent()) / 2, badgeMark);
        }
    }

    void setMoving(boolean on) {
        moving = on;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(cardW, cardH + labelH);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        card.set(0, 0, cardW, cardH);
        clip.reset();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        setPivotX(cardW / 2f);
        setPivotY(cardH / 2f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        // Lift: grow, rise a little and cast a soft shadow, like Apple TV's focus. Drawn frame by
        // frame (Tween): this TV runs with system animations off, which would make it a jump.
        if (lift != null) lift.cancel();
        if (sweepTween != null) sweepTween.cancel();
        if (glowTween != null) glowTween.cancel();
        sweep = -1;
        glow = 0;
        float s0 = getScaleX(), y0 = getTranslationY(), z0 = getTranslationZ();
        float s1 = focused ? FOCUS_SCALE : 1f, y1 = focused ? -Ui.dp(getContext(), 4) : 0;
        float z1 = focused ? Ui.dp(getContext(), 16) : 0;
        String motion = Ui.motion;
        if ("simple".equals(motion)) {
            // lifted at once: no spring, no light
            setScaleX(s1);
            setScaleY(s1);
            setTranslationY(y1);
            setTranslationZ(z1);
            invalidate();
            return;
        }
        // Lively: a light spring. Bounce: a springier one. Glow and Shine: a smooth rise.
        float give = "bounce".equals(motion) ? 3f : "lively".equals(motion) ? 1.4f : 0f;
        long ms = !focused ? DROP_MS : "bounce".equals(motion) ? BOUNCE_MS : give > 0 ? LIFT_MS : RISE_MS;
        lift = Tween.linear(ms, t -> {
            float e = focused && give > 0 ? Tween.springOut(t, give) : 1 - (1 - t) * (1 - t);
            float s = s0 + (s1 - s0) * e;
            setScaleX(s);
            setScaleY(s);
            setTranslationY(y0 + (y1 - y0) * e);
            setTranslationZ(z0 + (z1 - z0) * Math.min(1f, e));
        }, null);
        if (!focused || moving) {
            invalidate();
            return;
        }
        if ("lively".equals(motion) || "shine".equals(motion)) {
            // a band of light sweeps across the card once
            sweepTween = Tween.run(SHEEN_MS, t -> {
                sweep = t;
                invalidate();
            }, () -> {
                sweep = -1;
                invalidate();
            });
        } else if ("glow".equals(motion)) {
            // a halo in the ring's color breathes out twice, then settles faint
            glowTween = Tween.linear(GLOW_MS, t -> {
                glow = 0.35f + 0.65f * (float) Math.sin(Math.PI * ((t * 2) % 1f));
                invalidate();
            }, () -> {
                glow = 0.35f;
                invalidate();
            });
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable banner = item.banner, icon = item.icon;
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(card, fill);
        if (banner != null) {
            banner.setBounds(0, 0, cardW, cardH);
            banner.draw(canvas);
        } else if (icon != null) {
            int size = cardH * 46 / 100;
            int left = (cardW - size) / 2, top = cardH * 12 / 100;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
            drawLine(canvas, inner, cardW / 2f, cardH - cardH * 13 / 100f);
        } else {
            // inputs, the "Add favorites" tile, and apps whose art is still loading
            drawLine(canvas, inner, cardW / 2f, cardH / 2f - (inner.ascent() + inner.descent()) / 2);
        }
        if (moving) canvas.drawColor(0x99000000);
        if (sweep >= 0) {
            // from just off the left edge to just off the right, slanted like light on glass
            float band = cardW * 0.45f, x = -band + (cardW + band * 2) * sweep - band / 2;
            sheenAt.setRotate(20);
            sheenAt.postTranslate(x, 0);
            sheen.getShader().setLocalMatrix(sheenAt);
            canvas.drawRect(card, sheen);
        }
        canvas.restore();

        if (item.state == TvInputManager.INPUT_STATE_CONNECTED) {
            float r = Ui.dp(getContext(), 3.5f);
            canvas.drawCircle(cardW - r * 3, r * 3, r, dot);
        }
        if (isFocused() && glow > 0) drawHalo(canvas);
        if (isFocused() || moving) {
            float half = stroke / 2;
            ring.setPathEffect(moving ? new DashPathEffect(new float[] {stroke * 3, stroke * 2}, 0) : null);
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, ring);
            if (!moving) drawFrame(canvas);
        }
        if (moving) drawArrows(canvas);
        if (badge != null) drawBadge(canvas);
        // "Under the selected app": the space is kept under every tile, the name shows on focus only
        boolean label = banner != null && labelAlways && isFocused();
        if (labelH > 0 && label) {
            drawLine(canvas, under, cardW / 2f, cardH + labelH - Ui.dp(getContext(), 6));
        }
    }

    /**
     * The decorated Styles frame the selected tile inside its ring: Regency a second, finer line;
     * Art Deco stepped brackets in the corners; Storybook a dotted line.
     */
    private void drawFrame(Canvas canvas) {
        float in = stroke * 2.2f;
        switch (Ui.style) {
            case "regency":
                ring.setStrokeWidth(stroke * 0.45f);
                canvas.drawRoundRect(in, in, cardW - in, cardH - in, radius - in, radius - in, ring);
                break;
            case "deco": {
                float len = cardH * 0.22f, step = stroke * 1.6f, x0 = in + stroke, y0 = in + stroke;
                float x1 = cardW - x0, y1 = cardH - y0;
                ring.setStrokeWidth(stroke * 0.6f);
                for (int k = 0; k < 2; k++) {
                    float d = k * step, l = len - k * step * 1.5f;
                    canvas.drawLine(x0 + d, y0 + d, x0 + d + l, y0 + d, ring);
                    canvas.drawLine(x0 + d, y0 + d, x0 + d, y0 + d + l, ring);
                    canvas.drawLine(x1 - d, y0 + d, x1 - d - l, y0 + d, ring);
                    canvas.drawLine(x1 - d, y0 + d, x1 - d, y0 + d + l, ring);
                    canvas.drawLine(x0 + d, y1 - d, x0 + d + l, y1 - d, ring);
                    canvas.drawLine(x0 + d, y1 - d, x0 + d, y1 - d - l, ring);
                    canvas.drawLine(x1 - d, y1 - d, x1 - d - l, y1 - d, ring);
                    canvas.drawLine(x1 - d, y1 - d, x1 - d, y1 - d - l, ring);
                }
                break;
            }
            case "storybook":
                ring.setStrokeWidth(stroke * 0.8f);
                ring.setStrokeCap(Paint.Cap.ROUND);
                ring.setPathEffect(new DashPathEffect(new float[] {0.1f, stroke * 2.6f}, 0));
                canvas.drawRoundRect(in, in, cardW - in, cardH - in, radius - in, radius - in, ring);
                ring.setPathEffect(null);
                ring.setStrokeCap(Paint.Cap.BUTT);
                break;
            default:
                return;
        }
        ring.setStrokeWidth(stroke);
    }

    /** Tile motion › Glow: soft rings in the ring's color just outside the card. */
    private void drawHalo(Canvas canvas) {
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(stroke * 1.6f);
        int rgb = accent & 0x00FFFFFF;
        for (int k = 1; k <= 4; k++) {
            float out = stroke * 1.4f * k;
            int alpha = Math.round(glow * 110 / (k * 1.6f));
            halo.setColor(rgb | (alpha << 24));
            canvas.drawRoundRect(-out, -out, cardW + out, cardH + out, radius + out, radius + out, halo);
        }
    }

    private void drawLine(Canvas canvas, TextPaint paint, float x, float baseline) {
        float room = cardW - Ui.dp(getContext(), 16);
        CharSequence s = TextUtils.ellipsize(title, paint, room, TextUtils.TruncateAt.END);
        canvas.drawText(s, 0, s.length(), x, baseline, paint);
    }

    private void drawArrows(Canvas canvas) {
        float h = cardH / 6f, w = h * 0.7f, mid = cardH / 2f, pad = h * 0.8f;
        inner.setStyle(Paint.Style.FILL);
        arrow.reset();
        arrow.moveTo(pad, mid);
        arrow.lineTo(pad + w, mid - h / 2);
        arrow.lineTo(pad + w, mid + h / 2);
        arrow.close();
        arrow.moveTo(cardW - pad, mid);
        arrow.lineTo(cardW - pad - w, mid - h / 2);
        arrow.lineTo(cardW - pad - w, mid + h / 2);
        arrow.close();
        canvas.drawPath(arrow, inner);
    }
}
