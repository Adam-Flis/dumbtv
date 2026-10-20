package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Home extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<TextView> chips = new ArrayList<>();
    private Config cfg;
    private TvInputManager tv;
    private int applied = -1, appsGeneration;
    private float builtScale;
    /** Set by Options > Show photo frame now. */
    static boolean frameNow;
    private PhotoFrame frame;
    private boolean swallowUp;
    private final Runnable idle = this::startFrame;
    private final Runnable frameDone = this::endFrame;
    private boolean resumed, landed, ticking;
    private FrameLayout root;
    private ImageView wall;
    private View shade;
    private TextView greeting, clock, date, focusName, temp, sky, feels, hint, sunLine;
    private View skyIcon;
    private LinearLayout hours;
    private View hoursRule;
    private LinearLayout weatherBox;
    private Weather.Report weather;
    private String weatherKey;
    private long weatherAt;
    private boolean fetching;
    private LinearLayout bar;
    private TextView net;
    private int netColor = 0xFF34C759;
    private final java.util.Map<TextView, String> chipLabels = new java.util.HashMap<>();
    private ConnectivityManager cm;
    private WifiManager wifi;

    /** Keeps the Wi-Fi chip current; callbacks arrive on a background thread. */
    private final ConnectivityManager.NetworkCallback netWatch = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network n) {
            ui.post(Home.this::showNet);
        }

        @Override
        public void onLost(Network n) {
            ui.post(Home.this::showNet);
        }

        @Override
        public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
            ui.post(Home.this::showNet);
        }
    };
    private Row favs, apps, inputs;
    private View glow;
    private GradientDrawable glowBg;
    private int glowColor = GLOW_DEFAULT;
    private ValueAnimator glowAnim;
    private final java.util.Map<String, Integer> tints = new java.util.HashMap<>();
    private Tile moving;
    private String wallKey;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            loadApps(data == null ? null : data.getSchemeSpecificPart());
        }
    };

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    private final TvInputManager.TvInputCallback inputWatch = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String id, int state) {
            loadInputs();
        }

        @Override
        public void onInputAdded(String id) {
            loadInputs();
        }

        @Override
        public void onInputRemoved(String id) {
            loadInputs();
        }

        @Override
        public void onInputUpdated(String id) {
            loadInputs();
        }
    };

    private final class Row {
        final String kind;
        final TextView title;
        int icon;

        /** Options > Row title size: text and icon grow together. */
        void sizeTitle(float s) {
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17 * Ui.scale * s);
            title.setCompoundDrawablesRelative(Icons.of(icon, dp(20 * s), 0xD9FFFFFF), null, null, null);
            title.setCompoundDrawablePadding(dp(12 * s));
        }
        final HorizontalScrollView scroll;
        final LinearLayout strip;
        List<Item> all = new ArrayList<>();

        Row(String kind, String name, int icon, LinearLayout parent) {
            this.kind = kind;
            int edge = dp(56), bleed = dp(14);
            // spaced capitals, like the group headings in Options
            title = Ui.text(Home.this, 17, 0xD9FFFFFF, Ui.MEDIUM);
            title.setText(name.toUpperCase(Locale.ROOT));
            title.setLetterSpacing(0.12f);
            title.setGravity(Gravity.CENTER_VERTICAL);
            this.icon = icon;
            title.setCompoundDrawablePadding(dp(12));
            title.setPadding(edge, dp(26), edge, dp(4));
            strip = new LinearLayout(Home.this);
            strip.setClipChildren(false);
            strip.setClipToPadding(false);
            strip.setPadding(bleed, dp(16), bleed, dp(10));
            scroll = new HorizontalScrollView(Home.this) {
                /** Keeps the focused tile centered, except near either end of the row. */
                @Override
                protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                    if (getChildCount() == 0) return 0;
                    int range = Math.max(0, getChildAt(0).getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
                    int target = Math.max(0, Math.min(range, r.centerX() - getWidth() / 2));
                    return target - getScrollX();
                }

                /**
                 * Left/right inside a row is moved by this view, not by Android's focus search,
                 * so Android plays no navigation sound for it. Play it here when focus moved.
                 */
                @Override
                public boolean executeKeyEvent(KeyEvent e) {
                    int code = e.getKeyCode();
                    boolean side = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
                    View before = side ? findFocus() : null;
                    boolean handled = super.executeKeyEvent(e);
                    if (side && handled && e.getAction() == KeyEvent.ACTION_DOWN && findFocus() != before) {
                        playSoundEffect(SoundEffectConstants.getContantForFocusDirection(
                                code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT));
                    }
                    return handled;
                }
            };
            scroll.setFocusable(false);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setClipChildren(false);
            scroll.setClipToPadding(false);
            scroll.setPadding(edge - bleed, 0, edge - bleed, 0);
            scroll.addView(strip, new FrameLayout.LayoutParams(WRAP, WRAP));
            parent.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
            parent.addView(scroll, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        void setVisible(boolean on) {
            int v = on ? View.VISIBLE : View.GONE;
            title.setVisibility(v);
            scroll.setVisibility(v);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Future<List<Item>> catalog = io.submit(() -> Catalog.apps(this));
        cfg = Config.get(this);
        tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        builtScale = Ui.scale = cfg.textScale();
        setContentView(build());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        Keeper.start(this);
        if (tv != null) tv.registerCallback(inputWatch, ui);
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // the application context: WifiManager leaks the activity on Android 7 otherwise
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (cm != null) cm.registerDefaultNetworkCallback(netWatch);
        showNet();
        firstApps(catalog);
        loadInputs();
    }

    /**
     * Waits for the app list and loads the art of the tiles that fit on screen, so the first
     * frame is complete. After a cold start that frame is what people see first: loading in the
     * background drew the Inputs row alone, focused HDMI 1 for ~0.3 s, then popped the apps and
     * their banners in.
     */
    private void firstApps(Future<List<Item>> catalog) {
        try {
            apps.all = catalog.get();
        } catch (ExecutionException e) {
            loadApps(null);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            loadApps(null);
            return;
        }
        int fit = getResources().getDisplayMetrics().widthPixels / (cardSize()[0] + dp(18)) + 1;
        List<List<Item>> rows = new ArrayList<>();
        rows.add(Catalog.favorites(cfg, apps.all));
        rows.add(Catalog.visible(cfg, Catalog.APPS, apps.all));
        for (List<Item> row : rows) {
            int left = fit;
            for (Item it : row) {
                if (left-- <= 0) break;
                if (it.artRequested) continue;
                Catalog.art(getPackageManager(), it);
                it.artRequested = true;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Guard.arrived(this);
        cfg.refresh();
        cfg.visited();
        if (cfg.textScale() != builtScale) {
            // every text on the screen is sized at build time; rebuilding is simplest
            recreate();
            return;
        }
        if (cfg.version() != applied) apply();
        else background();
        if (frameNow) {
            frameNow = false;
            ui.postDelayed(idle, 600);
        } else {
            resetIdle();
        }
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        ticking = true;
        tick();
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (ticking) unregisterReceiver(time);
        ticking = false;
        ui.removeCallbacks(idle);
        stopFrame();
        if (moving != null) stopMoving();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        if (tv != null) tv.unregisterCallback(inputWatch);
        if (cm != null) cm.unregisterNetworkCallback(netWatch);
        io.shutdownNow();
        super.onDestroy();
    }

    /**
     * Android 9 keeps a hidden window's GPU context (about 35 MB on this TV) unless memory is
     * critical. Dropping it while an app plays costs one context rebuild on the way back.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_COMPLETE) return;
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object global = wmg.getMethod("getInstance").invoke(null);
            wmg.getMethod("trimMemory", int.class).invoke(global, TRIM_MEMORY_COMPLETE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not reachable on this build: the context simply stays
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (moving != null) stopMoving();
        if (resumed) focusFirst();
    }

    @Override
    public void onBackPressed() {
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        resetIdle();
        // any key wakes the photo frame and does nothing else
        if (frame != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                stopFrame();
                swallowUp = true;
            }
            return true;
        }
        if (swallowUp && e.getAction() == KeyEvent.ACTION_UP) {
            swallowUp = false;
            return true;
        }
        swallowUp = false;
        if (moving == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    shift(-1);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    shift(1);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    break;
                default:
                    stopMoving();
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f instanceof Tile) menu((Tile) f);
            else startActivity(new Intent(this, Options.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    /**
     * The scroll position for a focused rect {@code r}: the earliest of the top, the apps row or
     * the inputs row that still shows the row holding {@code r} whole.
     */
    private int rowTarget(ScrollView page, Rect r) {
        View content = page.getChildAt(0);
        List<int[]> rows = rowBlocks(content);
        int[] at = null;
        for (int[] b : rows) {
            if (r.centerY() >= b[0]) at = b;
        }
        if (at == null) return 0;
        int range = Math.max(0, content.getHeight() - page.getHeight());
        return Math.max(0, Math.min(range, stopFor(at, rows, page.getHeight())));
    }

    /** Title top and strip bottom of each visible row, in {@code content}'s coordinates. */
    private List<int[]> rowBlocks(View content) {
        List<int[]> rows = new ArrayList<>();
        for (Row row : new Row[] {favs, apps, inputs}) {
            if (row.scroll.getVisibility() != View.VISIBLE) continue;
            rows.add(new int[] {topIn(row.title, content), topIn(row.scroll, content) + row.scroll.getHeight()});
        }
        return rows;
    }

    /** The earliest of the top or a row title that shows the whole of {@code at}. */
    private static int stopFor(int[] at, List<int[]> rows, int height) {
        if (height >= at[1]) return 0;
        for (int[] b : rows) {
            if (b[0] <= at[0] && b[0] + height >= at[1]) return b[0];
        }
        return at[0];
    }

    /**
     * When the page is taller than the screen, pads its bottom just enough that every row's stop
     * is reachable. Otherwise the last stop is clamped and the clock above it shows half cut.
     */
    private void fitBottom(ScrollView page) {
        View content = page.getChildAt(0);
        if (content == null || page.getHeight() == 0) return;
        int base = dp(32), height = page.getHeight();
        int natural = content.getHeight() - content.getPaddingBottom() + base;
        int want = base;
        if (natural > height) {
            int last = 0;
            List<int[]> rows = rowBlocks(content);
            for (int[] b : rows) last = Math.max(last, stopFor(b, rows, height));
            want = base + Math.max(0, last + height - natural);
        }
        if (content.getPaddingBottom() != want) {
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), want);
        }
    }

    /** {@code v}'s top edge in {@code content}'s coordinates. */
    private static int topIn(View v, View content) {
        int y = 0;
        while (v != null && v != content) {
            y += v.getTop();
            v = (View) v.getParent();
        }
        return y;
    }

    private View build() {
        root = new FrameLayout(this) {
            /** Down from the top bar lands on the tile below it, not wherever Android guesses. */
            @Override
            public View focusSearch(View focused, int direction) {
                if (direction == View.FOCUS_DOWN && chips.contains(focused)) {
                    View below = nearest(favs.strip, focused);
                    if (below == null) below = nearest(apps.strip, focused);
                    if (below != null) return below;
                }
                // up from the first row goes to the closest top-bar button
                if (direction == View.FOCUS_UP && focused instanceof Tile && focused.getParent() == topStrip()) {
                    View above = nearest(bar, focused);
                    if (above != null) return above;
                }
                // left and right at the end of a row stay put instead of jumping to another row
                if ((direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) && focused instanceof Tile) {
                    View next = super.focusSearch(focused, direction);
                    return next != null && next.getParent() == focused.getParent() ? next : focused;
                }
                // left and right stay inside the top bar instead of dropping into the rows
                if ((direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) && chips.contains(focused)) {
                    int i = bar.indexOfChild(focused) + (direction == View.FOCUS_LEFT ? -1 : 1);
                    return i >= 0 && i < bar.getChildCount() ? bar.getChildAt(i) : focused;
                }
                return super.focusSearch(focused, direction);
            }
        };
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));
        // Background > Selected app's color: a soft glow in the focused app's color
        glow = new View(this);
        glowBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, glowColors(glowColor));
        glow.setBackground(glowBg);
        root.addView(glow, new FrameLayout.LayoutParams(MATCH, MATCH));
        shade = new View(this);
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Fixed header (greeting, clock, focused name) over a scrolling list of rows.
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        // clips the scrolling rows at their own top edge, so they slide under the header
        page.setPadding(0, dp(32), 0, 0);
        root.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Header, three lines: greeting | top bar; clock + weather | next hours; date, hint | sun line.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setClipChildren(false);
        head.setClipToPadding(false);
        head.setPadding(dp(56), 0, dp(56), 0);

        LinearLayout top = new LinearLayout(this);
        // the greeting sits on the bottom edge of the top-bar line, right above the time
        top.setGravity(Gravity.BOTTOM);
        top.setClipChildren(false);
        top.setClipToPadding(false);
        greeting = Ui.text(this, 30, 0xE6FFFFFF, Ui.MEDIUM);
        greeting.setPadding(dp(3), 0, 0, 0);
        top.addView(greeting, new LinearLayout.LayoutParams(0, WRAP, 1f));
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        // No LayoutTransition here: with system animations off (this TV) it can leave a button at
        // its old width for a moment when focus moves quickly, so buttons resize at once.
        bar.addView(chip(Icons.INPUT, "Inputs  \u25BE", this::inputsMenu));
        net = chip(Icons.WIFI, "", v -> openWifi());
        bar.addView(net);
        bar.addView(chip(Icons.SLIDERS, "Options", v -> startActivity(new Intent(this, Options.class))));
        bar.addView(chip(Icons.GEAR, "Settings", v -> openSettings()));
        top.addView(bar, new LinearLayout.LayoutParams(WRAP, WRAP));
        head.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        // clock, current weather and the next hours share one line, centered on each other
        LinearLayout middle = new LinearLayout(this);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        // baseline alignment would push the columns around and cut the forecast's last line
        middle.setBaselineAligned(false);
        middle.setClipChildren(false);
        middle.setClipToPadding(false);
        clock = Ui.text(this, 64, Ui.TEXT, Ui.LIGHT);
        clock.setSingleLine();
        // A big font keeps empty room above its digits and below the baseline. Negative margins
        // let the line hug the digits, so the greeting and date sit close to the time.
        android.graphics.Paint.FontMetricsInt fm = clock.getPaint().getFontMetricsInt();
        Rect digits = new Rect();
        clock.getPaint().getTextBounds("0", 0, 1, digits);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(WRAP, WRAP);
        clp.topMargin = -Math.max(0, digits.top - fm.ascent);
        clp.bottomMargin = -Math.max(0, fm.descent);
        // the date rides under the clock in its own column, so it stays close however tall the
        // forecast next to it gets
        LinearLayout timeCol = new LinearLayout(this);
        timeCol.setOrientation(LinearLayout.VERTICAL);
        timeCol.setPadding(0, dp(8), 0, 0);
        timeCol.setClipChildren(false);
        timeCol.setClipToPadding(false);
        timeCol.addView(clock, clp);
        date = Ui.text(this, 17, Ui.MUTED, Ui.MEDIUM);
        date.setPadding(dp(3), dp(10), 0, 0);
        timeCol.addView(date);
        middle.addView(timeCol);
        // icon, temperature, then conditions and feels-like stacked beside them: two short lines
        weatherBox = new LinearLayout(this);
        weatherBox.setGravity(Gravity.CENTER_VERTICAL);
        weatherBox.setPadding(0, 0, 0, 0);
        skyIcon = new View(this);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(10);
        weatherBox.addView(skyIcon, ilp);
        temp = Ui.text(this, 40, Ui.TEXT, Ui.LIGHT);
        weatherBox.addView(temp);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(14), 0, 0, 0);
        sky = Ui.text(this, 15, Ui.TEXT, Ui.MEDIUM);
        sky.setSingleLine();
        feels = Ui.text(this, 14, Ui.MUTED, Ui.MEDIUM);
        feels.setSingleLine();
        feels.setPadding(0, dp(5), 0, 0);
        details.addView(sky);
        details.addView(feels);
        weatherBox.addView(details);
        weatherBox.setVisibility(View.GONE);
        // current weather, and under it the next sunrise/sunset and tomorrow
        LinearLayout weatherCol = new LinearLayout(this);
        weatherCol.setOrientation(LinearLayout.VERTICAL);
        weatherCol.setPadding(dp(34), 0, 0, 0);
        weatherCol.addView(weatherBox);
        sunLine = Ui.text(this, 14, Ui.MUTED, Ui.MEDIUM);
        sunLine.setSingleLine();
        sunLine.setPadding(dp(2), dp(10), 0, 0);
        sunLine.setVisibility(View.GONE);
        weatherCol.addView(sunLine, new LinearLayout.LayoutParams(WRAP, WRAP));
        middle.addView(weatherCol);
        // the next hours sit right after the current weather, split by a thin line
        hoursRule = new View(this);
        hoursRule.setBackgroundColor(0x2EFFFFFF);
        hoursRule.setVisibility(View.GONE);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(56));
        rlp.leftMargin = dp(28);
        rlp.rightMargin = dp(12);
        rlp.topMargin = dp(16);
        middle.addView(hoursRule, rlp);
        // next hours, and under them the next sunrise/sunset and tomorrow: one forecast block
        LinearLayout forecast = new LinearLayout(this);
        forecast.setOrientation(LinearLayout.VERTICAL);
        hours = new LinearLayout(this);
        hours.setVisibility(View.GONE);
        forecast.addView(hours, new LinearLayout.LayoutParams(WRAP, WRAP));
        // padding, not a margin: a centered child's margin shifts it and cut off the last line
        forecast.setPadding(0, dp(16), 0, 0); // clear of the top bar when a button opens up
        middle.addView(forecast, new LinearLayout.LayoutParams(WRAP, WRAP));
        head.addView(middle, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.TOP);
        LinearLayout notes = new LinearLayout(this);
        notes.setOrientation(LinearLayout.VERTICAL);
        hint = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
        hint.setPadding(dp(3), dp(12), 0, 0);
        hint.setVisibility(View.GONE);
        notes.addView(hint);
        bottom.addView(notes, new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        page.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        focusName = Ui.text(this, 30, Ui.TEXT, Ui.MEDIUM);
        focusName.setSingleLine();
        focusName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        focusName.setPadding(dp(56), dp(18), dp(56), 0);
        focusName.setText(" ");
        page.addView(focusName, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView scroll = new ScrollView(this) {
            /** Scrolls in whole rows: the focused row shows complete, and a row starts at the top edge. */
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                return rowTarget(this, r) - getScrollY();
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                fitBottom(this);
            }

            /**
             * Up and down only move the focus; scrolling follows it. Left to ScrollView, a press
             * with nothing focusable that way scrolls half a screen and leaves rows cut.
             */
            @Override
            public boolean executeKeyEvent(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_PAGE_UP:
                    case KeyEvent.KEYCODE_PAGE_DOWN:
                    case KeyEvent.KEYCODE_SPACE:
                        return false;
                    default:
                        return super.executeKeyEvent(e);
                }
            }
        };
        scroll.setFocusable(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(0, 0, 0, dp(32));
        scroll.addView(column, new FrameLayout.LayoutParams(MATCH, WRAP));

        favs = new Row(Catalog.FAVS, "Favorites", Icons.STAR, column);
        apps = new Row(Catalog.APPS, "All apps", Icons.GRID, column);
        inputs = new Row(Catalog.INPUTS, "Inputs", Icons.INPUT, column);

        root.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            showFocusName(now);
            if (now instanceof Tile) tintFor(((Tile) now).item);
        });
        return root;
    }

    /** The strip of the first visible row: Favorites, else All apps, else Inputs. */
    private LinearLayout topStrip() {
        for (Row row : new Row[] {favs, apps, inputs}) {
            if (row.scroll.getVisibility() == View.VISIBLE) return row.strip;
        }
        return null;
    }

    /** The focused tile's name, large above the rows (App names = At top). */
    private void showFocusName(View now) {
        boolean top = "focus".equals(cfg.get(Config.LABELS));
        if (now instanceof Tile && top) {
            focusName.setText(((Tile) now).title());
            focusName.setVisibility(View.VISIBLE);
        } else {
            focusName.setVisibility(top ? View.INVISIBLE : View.GONE);
        }
    }

    /** The child of {@code strip} horizontally closest to {@code from}, or null when empty. */
    private static View nearest(LinearLayout strip, View from) {
        int[] at = new int[2];
        from.getLocationOnScreen(at);
        int x = at[0] + from.getWidth() / 2;
        View best = null;
        int bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View c = strip.getChildAt(i);
            c.getLocationOnScreen(at);
            int gap = Math.abs(at[0] + c.getWidth() / 2 - x);
            if (gap < bestGap) {
                bestGap = gap;
                best = c;
            }
        }
        return best;
    }

    private TextView chip(int icon, String label, View.OnClickListener click) {
        TextView v = Ui.text(this, 15, Ui.TEXT, Ui.MEDIUM);
        v.setTag(icon);
        chipLabels.put(v, label);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(44));
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(click);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(110).start();
            chipIcon((TextView) view);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(10);
        v.setLayoutParams(lp);
        chips.add(v);
        return v;
    }

    /**
     * Top-bar button: icon only, and the name next to it while focused. The icon turns dark on a
     * light accent; the Wi-Fi icon always shows its status color.
     */
    private void chipIcon(TextView v) {
        if (!(v.getTag() instanceof Integer)) return;
        boolean open = v.isFocused();
        String label = chipLabels.get(v);
        v.setText(open ? label : "");
        v.setContentDescription(label);
        v.setCompoundDrawablePadding(open ? dp(8) : 0);
        // closed: 12 + 20 + 12 = 44 dp wide, as tall as it is wide, so the icon sits dead center
        v.setPadding(dp(open ? 16 : 12), dp(10), dp(open ? 18 : 12), dp(10));
        // On the accent fill the icon turns dark/white like the text; the Wi-Fi status then moves to
        // a ringed dot after the name, so green never sits on blue.
        int ink = Ui.ink(cfg.accent());
        int color = open ? ink : v == net ? netColor : Ui.TEXT;
        android.graphics.drawable.Drawable status = null;
        if (open && v == net) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(netColor);
            dot.setStroke(dp(2), ink);
            dot.setBounds(0, 0, dp(14), dp(14));
            status = dot;
        }
        v.setCompoundDrawablesRelative(Icons.of((Integer) v.getTag(), dp(20), color), null, status, null);
    }

    private void apply() {
        applied = cfg.version();
        int accent = cfg.accent();
        for (TextView c : chips) {
            Ui.chipStyle(c, accent, dp(20), Math.max(1, dp(1)));
            chipIcon(c);
        }
        float ts = cfg.titleScale();
        for (Row row : new Row[] {favs, apps, inputs}) row.sizeTitle(ts);
        render(favs);
        render(apps);
        render(inputs);
        showFocusName(getCurrentFocus());
        background();
        tick();
    }

    /** Rebuilds the app list, keeping loaded art for every package except {@code changed}. */
    private void loadApps(String changed) {
        int generation = ++appsGeneration;
        io.execute(() -> {
            List<Item> list = Catalog.apps(this);
            ui.post(() -> {
                if (generation != appsGeneration || isDestroyed()) return;
                for (Item fresh : list) {
                    for (Item old : apps.all) {
                        if (old.id.equals(fresh.id) && !old.id.equals(changed) && old.artRequested) {
                            fresh.banner = old.banner;
                            fresh.icon = old.icon;
                            fresh.artRequested = true;
                        }
                    }
                }
                apps.all = list;
                render(favs);
                render(apps);
            });
        });
    }

    private void loadInputs() {
        inputs.all = Catalog.inputs(this, tv);
        render(inputs);
    }

    private void render(Row row) {
        if (moving != null && moving.kind.equals(row.kind)) stopMoving();
        View focused = getCurrentFocus();
        String keep = focused instanceof Tile && ((Tile) focused).kind.equals(row.kind)
                ? ((Tile) focused).item.id : null;
        row.strip.removeAllViews();

        boolean enabled = !Catalog.INPUTS.equals(row.kind) || "on".equals(cfg.get(Config.INPUTS));
        List<Item> shown;
        if (Catalog.FAVS.equals(row.kind)) {
            shown = Catalog.favorites(cfg, apps.all);
            // an empty dock shows one tile that opens the picker, so it is never a dead end
            if (shown.isEmpty() && !apps.all.isEmpty()) shown.add(button(Catalog.ADD, "Add favorites", Icons.STAR));
            // the easy way to change the dock: the same picker, from the end of the row
            else if (!shown.isEmpty()) shown.add(button(Catalog.EDIT, "Edit favorites", Icons.PENCIL));
        } else {
            shown = enabled ? Catalog.visible(cfg, row.kind, row.all) : new ArrayList<>();
            // the way to more apps: the Appstore, and every installed app to pick from
            if (Catalog.APPS.equals(row.kind) && !apps.all.isEmpty()) {
                shown.add(button(Catalog.STORE, "Get more apps", Icons.BAG));
                shown.add(button(Catalog.LIBRARY, "App library", Icons.LIBRARY));
            }
        }
        boolean appRow = !Catalog.INPUTS.equals(row.kind);
        int[] size = cardSize();
        int w = size[0], h = appRow ? size[1] : Math.round(size[1] * 0.56f);
        String labels = cfg.get(Config.LABELS);
        int accent = cfg.accent();
        int index = 0, restore = -1;
        for (Item it : shown) {
            Tile t = new Tile(this, it, row.kind, Catalog.label(cfg, it), w, h, labels, accent);
            t.setOnClickListener(v -> open((Tile) v));
            t.setOnLongClickListener(v -> {
                menu((Tile) v);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(22);
            row.strip.addView(t, lp);
            if (it.id.equals(keep)) restore = index;
            index++;
            if (appRow && !it.artRequested && it.intent != null) loadArt(it);
        }
        row.setVisible(!shown.isEmpty());
        if (restore >= 0) {
            row.strip.getChildAt(restore).requestFocus();
        } else if (keep != null && row.strip.getChildCount() > 0) {
            row.strip.getChildAt(0).requestFocus();
        } else if (getCurrentFocus() == null || !landed) {
            focusFirst();
        }
    }

    /** A tile that is a button: a drawn icon over its name. */
    private Item button(String id, String label, int icon) {
        Item it = new Item(id, label, null);
        it.icon = Icons.of(icon, dp(48), Ui.TEXT);
        it.artRequested = true;
        return it;
    }

    private static final int GLOW_DEFAULT = 0xFF2B3A67;

    private static int[] glowColors(int c) {
        return new int[] {c, Ui.mix(c, Config.APP_BASE, 0.62f), Config.APP_BASE};
    }

    /** Fades the glow toward {@code it}'s color; keeps the last one when its art is not loaded yet. */
    private void tintFor(Item it) {
        if (glow.getVisibility() != View.VISIBLE) return;
        Integer target = Catalog.special(it) ? Integer.valueOf(GLOW_DEFAULT) : tints.get(it.id);
        if (target == null) {
            Drawable art = it.banner != null ? it.banner : it.icon;
            if (art == null) return;
            target = Ui.vibrant(art);
            tints.put(it.id, target);
        }
        if (target == glowColor) return;
        if (glowAnim != null) glowAnim.cancel();
        int from = glowColor, to = target;
        glowColor = to;
        glowAnim = ValueAnimator.ofArgb(from, to);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(a -> glowBg.setColors(glowColors((Integer) a.getAnimatedValue())));
        glowAnim.start();
    }

    private void loadArt(Item it) {
        it.artRequested = true;
        io.execute(() -> {
            Catalog.art(getPackageManager(), it);
            ui.post(() -> {
                for (Row row : new Row[] {favs, apps}) {
                    for (int i = 0; i < row.strip.getChildCount(); i++) row.strip.getChildAt(i).invalidate();
                }
            });
        });
    }

    private int[] cardSize() {
        switch (cfg.get(Config.TILES)) {
            case "small":
                return new int[] {dp(144), dp(81)};
            case "large":
                return new int[] {dp(224), dp(126)};
            default:
                return new int[] {dp(180), dp(101)};
        }
    }

    private void focusFirst() {
        favs.scroll.scrollTo(0, 0);
        apps.scroll.scrollTo(0, 0);
        inputs.scroll.scrollTo(0, 0);
        View first = favs.strip.getChildAt(0);
        if (first == null) first = apps.strip.getChildAt(0);
        if (first == null) first = inputs.strip.getChildAt(0);
        landed = apps.strip.getChildCount() > 0;
        if (first == null) first = chips.get(0);
        first.requestFocus();
    }

    private void tick() {
        String mode = cfg.get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
        Date now = new Date();
        if (h24) {
            clock.setText(DateFormat.format("HH:mm", now));
        } else {
            SpannableString half = new SpannableString(" " + DateFormat.format("a", now));
            half.setSpan(new RelativeSizeSpan(0.36f), 0, half.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            clock.setText(DateFormat.format("h:mm", now));
            clock.append(half);
        }
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        greeting.setText(hour >= 4 && hour < 12 ? "Good morning" : hour >= 4 && hour < 17 ? "Good afternoon" : "Good evening");
        Locale locale = Locale.getDefault();
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), now));
        // hourly and time-of-day rotations change while the screen stays open; a no-op otherwise
        background();
        weather();
        frameInfo();
    }

    private void background() {
        boolean image = "image".equals(cfg.get(Config.BACKGROUND));
        File file = image ? cfg.wallpaper() : null;
        root.setBackgroundColor(cfg.background());
        glow.setVisibility("app".equals(cfg.get(Config.BACKGROUND)) ? View.VISIBLE : View.GONE);
        int dim = Integer.parseInt(cfg.get(Config.DIM));
        shade.setBackgroundColor(file == null ? Color.TRANSPARENT : Color.argb(dim * 255 / 100, 0, 0, 0));
        String key = file == null ? null : file.getPath() + ":" + file.lastModified();
        if (key == null ? wallKey == null : key.equals(wallKey)) return;
        wallKey = key;
        if (file == null) {
            wall.setImageDrawable(null);
            return;
        }
        io.execute(() -> {
            Bitmap b = decode(file, getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            ui.post(() -> {
                if (key.equals(wallKey)) wall.setImageBitmap(b);
            });
        });
    }

    private static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /**
     * Fire OS answers the generic settings action with a stub that reopens Amazon's home, which
     * Guard then replaces with ours. The TV settings entry point opens the real settings screen.
     */
    private void openSettings() {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS");
        boolean found = tvSettings.resolveActivity(getPackageManager()) != null;
        launch(found ? tvSettings : new Intent(Settings.ACTION_SETTINGS), null);
    }

    private void resetIdle() {
        ui.removeCallbacks(idle);
        String after = cfg.get(Config.FRAME);
        if (frame != null || "off".equals(after) || !resumed) return;
        ui.postDelayed(idle, Integer.parseInt(after) * 60_000L);
    }

    private void startFrame() {
        if (frame != null || !resumed) return;
        // a dialog or the Inputs dropdown is open: wait for the next quiet period
        if (!hasWindowFocus()) {
            resetIdle();
            return;
        }
        frame = new PhotoFrame(this, io, cfg.dir);
        frame.setAlpha(0f);
        root.addView(frame, new FrameLayout.LayoutParams(MATCH, MATCH));
        frame.animate().alpha(1f).setDuration(800).start();
        frameInfo();
        frame.start();
        // the TV stays on while the frame runs, then sleeps as usual
        root.setKeepScreenOn(true);
        ui.postDelayed(frameDone, Integer.parseInt(cfg.get(Config.FRAME_FOR)) * 60_000L);
    }

    private void endFrame() {
        root.setKeepScreenOn(false);
        if (frame != null) frame.pause();
    }

    private void stopFrame() {
        ui.removeCallbacks(frameDone);
        root.setKeepScreenOn(false);
        if (frame == null) return;
        // fade back to the home screen; keys work again at once
        PhotoFrame going = frame;
        frame = null;
        going.pause();
        going.animate().alpha(0f).setDuration(resumed ? 450 : 0).withEndAction(() -> {
            going.stop();
            root.removeView(going);
        }).start();
        resetIdle();
    }

    private void frameInfo() {
        if (frame == null) return;
        CharSequence line = null;
        if (weather != null && weatherBox.getVisibility() == View.VISIBLE) {
            line = temp.getText() + "  " + sky.getText();
        }
        frame.setInfo(clock.getText(), date.getText(), line,
                hint.getVisibility() == View.VISIBLE ? hint.getText() : null);
    }

    /** Refreshes the forecast every 30 min while weather is on; shows or hides the weather views. */
    private void weather() {
        String lat = cfg.get(Weather.LAT, ""), lon = cfg.get(Weather.LON, "");
        boolean on = "on".equals(cfg.get(Config.WEATHER)) && !lat.isEmpty() && !lon.isEmpty();
        if (!on) {
            weather = null;
            weatherKey = null;
            showWeather();
            return;
        }
        boolean f = "f".equals(cfg.get(Config.UNITS));
        boolean h24 = clock24();
        String key = lat + "," + lon + "," + f + "," + h24;
        long now = System.currentTimeMillis();
        if (!key.equals(weatherKey)) {
            weatherKey = key;
            weather = null;
            weatherAt = 0;
        }
        showWeather();
        if (fetching || now - weatherAt < 30 * 60_000L) return;
        fetching = true;
        io.execute(() -> {
            Weather.Report r = null;
            try {
                r = Weather.fetch(lat, lon, f, h24);
            } catch (Exception e) {
                android.util.Log.w("dumbtv", "weather: " + e);
            }
            Weather.Report got = r;
            ui.post(() -> {
                fetching = false;
                if (!key.equals(weatherKey)) return;
                // a failure keeps the last forecast and tries again in 5 minutes
                weatherAt = got != null ? System.currentTimeMillis() : System.currentTimeMillis() - 25 * 60_000L;
                if (got != null) weather = got;
                showWeather();
            });
        });
    }

    private void showWeather() {
        if (weather == null) {
            weatherBox.setVisibility(View.GONE);
            hint.setVisibility(View.GONE);
            hours.setVisibility(View.GONE);
            hoursRule.setVisibility(View.GONE);
            sunLine.setVisibility(View.GONE);
            return;
        }
        temp.setText(weather.temp + "\u00B0");
        String words = Weather.describe(weather.code, weather.day);
        sky.setText((words.isEmpty() ? "" : words + "  \u00B7  ")
                + "H " + weather.hi + "\u00B0  L " + weather.lo + "\u00B0");
        boolean icon = on(Config.W_ICON);
        skyIcon.setVisibility(icon ? View.VISIBLE : View.GONE);
        if (icon) skyIcon.setBackground(Icons.of(Icons.forCode(weather.code, weather.day), dp(38), 0));
        feels.setText("Feels " + weather.feels + "\u00B0  \u00B7  Wind " + weather.wind + " " + weather.windUnit);
        feels.setVisibility(on(Config.W_FEELS) ? View.VISIBLE : View.GONE);
        weatherBox.setVisibility(View.VISIBLE);
        showHours();
        if (on(Config.W_SUN) && weather.sun != null) {
            String t = Weather.describe(weather.tomorrowCode, true);
            sunLine.setText(weather.sun + "  \u00B7  Tomorrow " + weather.tomorrowHi + "\u00B0 / "
                    + weather.tomorrowLo + "\u00B0" + (t.isEmpty() ? "" : "  " + t)
                    + (weather.tomorrowRain >= 30 ? "  " + weather.tomorrowRain + "%" : ""));
            sunLine.setVisibility(View.VISIBLE);
        } else {
            sunLine.setVisibility(View.GONE);
        }
        hint.setText(weather.hint);
        hint.setVisibility(weather.hint == null ? View.GONE : View.VISIBLE);
        frameInfo();
    }

    private boolean on(Config.Choice c) {
        return "on".equals(cfg.get(c));
    }

    /** The next five hours under the top bar: time, icon, temperature, chance of rain. */
    private void showHours() {
        hours.removeAllViews();
        if (!on(Config.W_HOURS) || weather.hours.isEmpty()) {
            hours.setVisibility(View.GONE);
            hoursRule.setVisibility(View.GONE);
            return;
        }
        // the rain line only takes room when one of the hours has a real chance of rain
        boolean anyRain = false;
        for (Weather.Hour h : weather.hours) anyRain |= h.rain >= 20;
        for (Weather.Hour h : weather.hours) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView at = Ui.text(this, 13, Ui.MUTED, Ui.MEDIUM);
            at.setText(h.at);
            View icon = new View(this);
            icon.setBackground(Icons.of(Icons.forCode(h.code, h.day), dp(24), 0));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
            ilp.topMargin = dp(4);
            ilp.bottomMargin = dp(4);
            TextView t = Ui.text(this, 16, Ui.TEXT, Ui.MEDIUM);
            t.setText(h.temp + "\u00B0");
            TextView rain = Ui.text(this, 12, Ui.MUTED, Ui.MEDIUM);
            rain.setPadding(0, dp(2), 0, 0);
            rain.setText(h.rain >= 20 ? h.rain + "%" : "");
            rain.setVisibility(anyRain ? View.VISIBLE : View.GONE);
            cell.addView(at);
            cell.addView(icon, ilp);
            cell.addView(t);
            cell.addView(rain);
            hours.addView(cell, new LinearLayout.LayoutParams(dp(58), WRAP));
        }
        hours.setVisibility(View.VISIBLE);
        hoursRule.setVisibility(weatherBox.getVisibility());
    }

    private boolean clock24() {
        String mode = cfg.get(Config.CLOCK);
        return "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
    }

    /** Wi-Fi name (or Ethernet) with a dot: green online, amber no internet, red offline. */
    private void showNet() {
        if (net == null) return;
        Network n = cm == null ? null : cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        String name;
        int color;
        if (caps == null) {
            name = "Not connected";
            color = 0xFFFF5147;
        } else {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiInfo w = wifi == null ? null : wifi.getConnectionInfo();
                String ssid = w == null ? null : w.getSSID();
                if (ssid != null && ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                name = ssid == null || ssid.isEmpty() || ssid.contains("unknown ssid") ? "Wi-Fi" : ssid;
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                name = "Ethernet";
            } else {
                name = "Connected";
            }
            boolean online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            color = online ? 0xFF34C759 : 0xFFFFB340;
            if (!online) name += " \u00B7 No internet";
        }
        // a drawable, not a colored character: Fire OS "high contrast text" repaints text black/white
        chipLabels.put(net, name);
        netColor = color;
        chipIcon(net);
    }

    private android.graphics.drawable.Drawable dot(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        d.setBounds(0, 0, dp(10), dp(10));
        return d;
    }

    private void openWifi() {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        if (i.resolveActivity(getPackageManager()) != null) launch(i, null);
        else openSettings();
    }

    /** The Inputs dropdown under its top-bar button: every shown input, a dot when something is plugged in. */
    private void inputsMenu(View anchor) {
        List<Item> list = Catalog.visible(cfg, Catalog.INPUTS, inputs.all);
        if (list.isEmpty()) {
            Toast.makeText(this, "No inputs found", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        PopupWindow pop = new PopupWindow(box, WRAP, WRAP, true);
        pop.setBackgroundDrawable(Ui.round(0xF2202126, dp(16)));
        pop.setElevation(dp(12));
        int accent = cfg.accent();
        TextView first = null;
        for (Item it : list) {
            TextView row = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
            String label = Catalog.label(cfg, it).toString();
            row.setText(label);
            if (it.state == TvInputManager.INPUT_STATE_CONNECTED) {
                row.setCompoundDrawablesRelative(null, null, dot(0xFF34C759), null);
                row.setCompoundDrawablePadding(dp(12));
            }
            row.setMinWidth(dp(220));
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setFocusable(true);
            row.setClickable(true);
            Ui.focusStyle(row, accent, 0x00000000, dp(10));
            row.setOnClickListener(v -> {
                pop.dismiss();
                launch(new Intent(it.intent), null);
            });
            box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
            if (first == null) first = row;
        }
        pop.showAsDropDown(anchor, 0, dp(8));
        first.requestFocus();
    }

    private void open(Tile t) {
        if (Catalog.ADD.equals(t.item.id) || Catalog.EDIT.equals(t.item.id)) {
            Picker.open(this, Picker.FAVS);
            return;
        }
        if (Catalog.LIBRARY.equals(t.item.id)) {
            Picker.open(this, Picker.LIBRARY);
            return;
        }
        if (Catalog.STORE.equals(t.item.id)) {
            Intent store = Catalog.storeIntent(this);
            if (store == null) Toast.makeText(this, "The Appstore is not available", Toast.LENGTH_SHORT).show();
            else launch(store, Catalog.APPSTORE);
            return;
        }
        launch(new Intent(t.item.intent), Catalog.INPUTS.equals(t.kind) ? null : t.item.id);
    }

    private void launch(Intent i, String pkg) {
        if (Guard.AMAZON.equals(pkg)) Guard.pause();
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private static final String MOVE = "Move", RENAME = "Rename", HIDE = "Hide", INFO = "App info",
            FAV_ADD = "Add to Favorites", FAV_REMOVE = "Remove from Favorites";

    private void menu(Tile t) {
        if (Catalog.special(t.item)) {
            open(t);
            return;
        }
        List<String> actions = new ArrayList<>();
        if (Catalog.FAVS.equals(t.kind)) {
            actions.add(MOVE);
            actions.add(FAV_REMOVE);
            actions.add(RENAME);
            actions.add(INFO);
        } else if (Catalog.APPS.equals(t.kind)) {
            actions.add(Catalog.isFavorite(cfg, t.item) ? FAV_REMOVE : FAV_ADD);
            actions.add(MOVE);
            actions.add(RENAME);
            actions.add(HIDE);
            actions.add(INFO);
        } else {
            actions.add(MOVE);
            actions.add(RENAME);
            actions.add(HIDE);
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.label(cfg, t.item))
                .setItems(actions.toArray(new String[0]), (d, which) -> {
                    switch (actions.get(which)) {
                        case MOVE:
                            if (t.getParent() == null) break;
                            moving = t;
                            t.setMoving(true);
                            break;
                        case RENAME:
                            rename(t);
                            break;
                        case HIDE:
                            Catalog.setShown(cfg, t.kind, t.item, false);
                            render(Catalog.APPS.equals(t.kind) ? apps : inputs);
                            Toast.makeText(this, "Hidden. Bring it back from Options.", Toast.LENGTH_SHORT).show();
                            break;
                        case FAV_ADD:
                            Catalog.setFavorite(cfg, t.item, true);
                            render(favs);
                            Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show();
                            break;
                        case FAV_REMOVE:
                            Catalog.setFavorite(cfg, t.item, false);
                            render(favs);
                            break;
                        default:
                            launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", t.item.id, null)), null);
                    }
                })
                .show();
    }

    private void rename(Tile t) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setText(Catalog.label(cfg, t.item));
        field.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field);
        String key = "name." + t.item.id;
        AlertDialog d = new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Rename")
                .setView(box)
                .setPositiveButton("Save", (dialog, w) -> {
                    String name = field.getText().toString().trim();
                    cfg.set(key, name.equals(t.item.label.toString()) ? null : name);
                    apply();
                })
                .setNeutralButton("Original", (dialog, w) -> {
                    cfg.set(key, null);
                    apply();
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private void shift(int by) {
        LinearLayout strip = (LinearLayout) moving.getParent();
        int from = strip.indexOfChild(moving), to = from + by;
        if (to < 0 || to >= strip.getChildCount()) return;
        // apps stay in front of the Get more apps / App library buttons
        if (Catalog.special(((Tile) strip.getChildAt(to)).item)) return;
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        strip.removeViewAt(from);
        strip.addView(moving, to, lp);
        moving.requestFocus();
        moving.playSoundEffect(by < 0 ? SoundEffectConstants.NAVIGATION_LEFT : SoundEffectConstants.NAVIGATION_RIGHT);
    }

    private void stopMoving() {
        Tile t = moving;
        moving = null;
        t.setMoving(false);
        LinearLayout strip = (LinearLayout) t.getParent();
        if (strip == null) return;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < strip.getChildCount(); i++) {
            Item it = ((Tile) strip.getChildAt(i)).item;
            if (!Catalog.special(it)) ids.add(it.id);
        }
        Catalog.saveOrder(cfg, t.kind, ids);
        applied = cfg.version();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
