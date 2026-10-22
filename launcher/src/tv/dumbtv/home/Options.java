package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Every setting is a row: left/right or OK cycles it, and the change is saved at once. */
public final class Options extends Activity {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<TextView> headers = new ArrayList<>(), navItems = new ArrayList<>();
    private LinearLayout nav, list;
    private ScrollView scroll;
    private Config cfg;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        Ui.scale = cfg.textScale();

        // Left: the groups, so the bottom ones are one press away. Right: the settings, scrolling.
        LinearLayout screen = new LinearLayout(this);
        screen.setBackgroundColor(0xFF0C0C0E);
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setPadding(dp(40), dp(40), dp(20), dp(40));
        nav.setBackgroundColor(0xFF141519);
        TextView title = Ui.text(this, 30, Ui.TEXT, Ui.LIGHT);
        title.setText("Options");
        title.setPadding(dp(14), 0, 0, dp(28));
        nav.addView(title);
        screen.addView(nav, new LinearLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.MATCH_PARENT));

        scroll = new Ui.SoundScroll(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(40), dp(40), dp(56), dp(110));
        scroll.addView(list);
        screen.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        screen.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (now != null && now.getParent() == list) markSection(sectionOf(now));
            showHints(now);
        });

        // Six short groups. Rows that only matter for another setting appear when it is on
        // (the image rows with an image background, the night times with night mode, ...).
        Cond image = () -> "image".equals(cfg.get(Config.BACKGROUND));
        Cond weatherOn = () -> cfg.on(Config.WEATHER);
        Cond frameOn = cfg::frameOn;
        Cond nightOn = () -> cfg.on(Config.NIGHT);
        Cond careOn = () -> cfg.on(Config.CARE);

        section("Home screen", Icons.GRID);
        list.addView(action("Favorites", () -> {
            openingPicker = true;
            Picker.open(this, Picker.FAVS);
        }));
        list.addView(action("App library", () -> pick(Catalog.APPS)));
        list.addView(action("Get more apps", () -> {
            Intent store = Catalog.storeIntent(this);
            if (store == null) Ui.notice(this, "The Appstore is not available", Icons.BAG);
            else startActivity(store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }));
        list.addView(choice(Config.APPS_LAYOUT));
        list.addView(action("Home screen layout", () -> RowsEditor.show(this, this::refresh)));
        list.addView(action("Inputs", () -> pick(Catalog.INPUTS)));
        list.addView(choice(Config.SWITCH));

        section("Appearance", Icons.CONTRAST);
        list.addView(choice(Config.ACCENT));
        list.addView(choice(Config.BACKGROUND));
        list.addView(only(choice(Config.DIM), image));
        list.addView(only(choice(Config.ROTATE), image));
        list.addView(only(action("Next wallpaper", () -> {
            cfg.nextWallpaper();
            refresh();
        }), image));
        list.addView(choice(Config.TILES));
        list.addView(choice(Config.TEXT));
        list.addView(choice(Config.TITLES));
        list.addView(choice(Config.LABELS));
        list.addView(choice(Config.CLOCK));

        int weather = section("Weather", Icons.PARTLY);
        list.addView(choice(Config.WEATHER));
        list.addView(only(cityRow(), weatherOn));
        list.addView(only(choice(Config.UNITS), weatherOn));
        list.addView(only(choice(Config.W_DETAIL), weatherOn));

        section("Night & photos", Icons.SLEEP);
        list.addView(choice(Config.FRAME));
        list.addView(only(choice(Config.FRAME_FOR), frameOn));
        list.addView(only(action("Show photo frame now", () -> {
            Home.frameNow = true;
            finish();
        }), frameOn));
        list.addView(choice(Config.NIGHT));
        list.addView(only(timeRow(Config.NIGHT_START, "Starts at", 22 * 60), nightOn));
        list.addView(only(timeRow(Config.NIGHT_END, "Ends at", 6 * 60), nightOn));
        list.addView(slider(Config.NIGHT_DIM, "Night dim", 10, 90, 5));
        list.addView(choice(Config.CARE));
        list.addView(only(choice(Config.REST), careOn));

        section("More", Icons.DOTS);
        list.addView(action("Reset tiles", this::reset));
        list.addView(action("Amazon home screen", () -> {
            Guard.amazon(this);
            finish();
        }));
        list.addView(only(action("Remove sleep timer permission", () -> {
            Sleep.set(this, 0);
            Sleep.release(this);
            Ui.notice(this, "Sleep timer permission removed", Icons.TIMER);
            refresh();
        }), () -> Sleep.allowed(this)));

        section("Help", Icons.HELP);
        list.addView(status("Home button", () -> Help.homeHeld(this), () -> "Brings you here", "Needs setup",
                this::fixHome));
        list.addView(status("Sleep timer", () -> Sleep.allowed(this), () -> "Ready", "Needs setup",
                () -> Help.sleepSetup(this)));
        list.addView(status("Internet", () -> Help.online(this), () -> "Connected", "Not connected",
                () -> startActivity(FireSettings.wifi(this))));
        list.addView(status("Weather", () -> cfg.on(Config.WEATHER)
                        && !cfg.get(Weather.LAT, "").isEmpty(),
                () -> cfg.get(Weather.PLACE, "On"), "Off", () -> openSection(weather)));
        list.addView(action("Welcome tips", () -> {
            Home.tipsNow = true;
            finish();
        }));
        list.addView(action("Remote guide", () -> Help.remote(this)));
        list.addView(note("Something looks wrong? Press Home on the remote to start over from the home screen."));

        TextView about = Ui.text(this, 13, Ui.FAINT, Ui.MEDIUM);
        about.setLineSpacing(0, 1.4f);
        about.setPadding(dp(16), dp(28), 0, 0);
        about.setText("Settings: " + cfg.dir + "/" + Config.FILE
                + "\nYour photos: " + PhotoFrame.FOLDER + "/   \u00B7   Wallpapers: " + Config.WALLPAPERS + "/"
                + "\nInternet only for weather. No tracking, no ads.");
        list.addView(about);

        // what the remote does right here, at the bottom of the group list; follows the focus
        nav.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        hintBar = new LinearLayout(this);
        hintBar.setOrientation(LinearLayout.VERTICAL);
        hintBar.setPadding(dp(14), 0, 0, 0);
        nav.addView(hintBar);
        setContentView(screen);
        refresh();
    }

    private static final String CHOICE = "choice", TIME = "time", SLIDER = "slider";

    /** A percentage as a bar: left/right moves it by {@code step}. */
    private View slider(String key, String title, int min, int max, int step) {
        LinearLayout row = row();
        row.setTag(SLIDER);
        row.setId(180 + Math.abs(key.hashCode() % 10));
        row.addView(label(title), new LinearLayout.LayoutParams(0, WRAP, 1f));
        int[] value = {cfg.nightDim()};
        View bar = new View(this) {
            private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(android.graphics.Canvas c) {
                float h = dp(6), r = dp(9), y = getHeight() / 2f, left = r, right = getWidth() - r;
                float x = left + (right - left) * (value[0] - min) / (float) (max - min);
                p.setColor(0x33FFFFFF);
                c.drawRoundRect(left, y - h / 2, right, y + h / 2, h / 2, h / 2, p);
                p.setColor(cfg.accent());
                c.drawRoundRect(left, y - h / 2, x, y + h / 2, h / 2, h / 2, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, row.isFocused() ? r : r * 0.8f, p);
            }
        };
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(220), dp(24));
        blp.rightMargin = dp(16);
        row.addView(bar, blp);
        TextView pct = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        pct.setGravity(Gravity.END);
        pct.setMinWidth(dp(48));
        row.addView(pct);
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int by = code == KeyEvent.KEYCODE_DPAD_LEFT ? -step : code == KeyEvent.KEYCODE_DPAD_RIGHT ? step : 0;
            if (by == 0) return false;
            int next = Math.max(min, Math.min(max, value[0] + by));
            if (next != value[0]) {
                v.playSoundEffect(by < 0 ? SoundEffectConstants.NAVIGATION_LEFT : SoundEffectConstants.NAVIGATION_RIGHT);
                value[0] = next;
                cfg.set(key, String.valueOf(next));
                refresh();
            }
            return true;
        });
        Runnable show = () -> {
            value[0] = cfg.nightDim();
            pct.setText(value[0] + "%");
            pct.setTextColor(row.isFocused() ? Ui.TEXT : Ui.MUTED);
            bar.invalidate();
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        refreshers.add(() -> style(row));
        return only(row, () -> cfg.on(Config.NIGHT));
    }

    interface Cond {
        boolean ok();
    }

    /** Shows {@code row} only while {@code when} holds; checked on every change. */
    private View only(View row, Cond when) {
        refreshers.add(() -> row.setVisibility(when.ok() ? View.VISIBLE : View.GONE));
        return row;
    }

    /** A time setting: left/right moves it 15 minutes, OK opens the exact-time picker. */
    private View timeRow(String key, String title, int fallback) {
        LinearLayout row = row();
        row.setTag(TIME);
        row.setId(100 + key.hashCode() % 50 + 50);
        TextView value = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        row.addView(label(title), new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        row.setOnClickListener(v -> TimeChooser.show(this, title, cfg.time(key, fallback), cfg.clock24(this), m -> {
            cfg.setTime(key, m);
            refresh();
        }));
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int by = code == KeyEvent.KEYCODE_DPAD_LEFT ? -15 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 15 : 0;
            if (by == 0) return false;
            v.playSoundEffect(by < 0 ? SoundEffectConstants.NAVIGATION_LEFT : SoundEffectConstants.NAVIGATION_RIGHT);
            int m = cfg.time(key, fallback);
            cfg.setTime(key, ((m / 15 * 15 + by) % 1440 + 1440) % 1440);
            refresh();
            return true;
        });
        Runnable show = () -> {
            String text = clockText(cfg.time(key, fallback));
            boolean focused = row.isFocused();
            value.setText(focused ? "\u2039  " + text + "  \u203A" : text);
            value.setTextColor(focused ? Ui.TEXT : Ui.MUTED);
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        refreshers.add(() -> style(row));
        return row;
    }

    private String clockText(int m) {
        int h = m / 60, min = m % 60;
        if (cfg.clock24(this)) return String.format(java.util.Locale.ROOT, "%02d:%02d", h, min);
        return (h % 12 == 0 ? 12 : h % 12) + ":" + String.format(java.util.Locale.ROOT, "%02d", min) + (h < 12 ? " AM" : " PM");
    }

    /** A line of small print under a group. */
    private View note(String text) {
        TextView v = Ui.text(this, 14, Ui.FAINT, Ui.MEDIUM);
        v.setText(text);
        v.setLineSpacing(0, 1.3f);
        v.setPadding(dp(16), dp(6), dp(16), dp(4));
        return v;
    }
    private LinearLayout hintBar;

    private void showHints(View now) {
        if (hintBar == null || now == null) return;
        if (now.getParent() == list) {
            if (CHOICE.equals(now.getTag()) || SLIDER.equals(now.getTag())) Ui.hintLines(hintBar, "\u25C0 \u25B6", "Change", "Back", "Groups");
            else if (TIME.equals(now.getTag())) Ui.hintLines(hintBar, "\u25C0 \u25B6", "\u00B115 min", "OK", "Set time", "Back", "Groups");
            else Ui.hintLines(hintBar, "OK", "Open", "Back", "Groups");
        } else {
            Ui.hintLines(hintBar, "\u25B2 \u25BC", "Group", "\u25B6", "Open", "Back", "Close");
        }
    }

    /** Back from a setting goes to its group on the left; back from the left closes Options. */
    @Override
    public void onBackPressed() {
        View f = getCurrentFocus();
        if (f != null && f.getParent() == list) {
            navItems.get(sectionOf(f)).requestFocus();
            return;
        }
        super.onBackPressed();
    }

    /** Index of the group whose heading is the last one above {@code row}. */
    private int sectionOf(View row) {
        int at = list.indexOfChild(row), found = 0;
        for (int i = 0; i < headers.size(); i++) {
            if (list.indexOfChild(headers.get(i)) < at) found = i;
        }
        return found;
    }

    private void markSection(int index) {
        for (int i = 0; i < navItems.size(); i++) navItems.get(i).setSelected(i == index);
    }

    private void openSection(int index) {
        View header = headers.get(index);
        // skip the heading's rule: the first setting is the first focusable row after it
        View first = null;
        for (int i = list.indexOfChild(header) + 1; i < list.getChildCount() && first == null; i++) {
            View c = list.getChildAt(i);
            if (c.isFocusable() && c.getVisibility() == View.VISIBLE) first = c;
        }
        if (first != null) first.requestFocus();
        scroll.smoothScrollTo(0, index == 0 ? 0 : header.getTop());
    }

    private void navIcon(TextView v) {
        int kind = (Integer) v.getTag();
        int color = v.isFocused() ? Ui.ink(cfg.accent()) : v.isSelected() ? Ui.TEXT : Ui.MUTED;
        // the weather icon keeps its own colors
        v.setCompoundDrawablesRelative(Icons.of(kind, dp(22), color), null, null, null);
    }

    private boolean openingPicker;

    /**
     * Leaving Options any other way (HOME, an app, Fire OS settings) closes it. Otherwise HOME
     * brings back this task with Options still on top instead of the home screen.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (!openingPicker && !isChangingConfigurations()) finish();
        openingPicker = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        refresh();
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    private View choice(Config.Choice c) {
        LinearLayout row = row();
        row.setTag(CHOICE);
        // a fixed id, so the focused row survives the rebuild after Text size changes
        row.setId(1 + Arrays.asList(Config.CHOICES).indexOf(c));
        TextView name = label(c.title);
        TextView value = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        value.setGravity(Gravity.END);
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        row.setOnClickListener(v -> step(c, 1));
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            // changing a value is a key the system does not sound for: give it the same tick
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                v.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                return step(c, -1);
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                return step(c, 1);
            }
            return false;
        });
        Runnable show = () -> {
            boolean focused = row.isFocused();
            String text = c.name(cfg.get(c));
            value.setText(focused ? "‹  " + text + "  ›" : text);
            if (c == Config.ACCENT) {
                value.setCompoundDrawablesRelative(Ui.dot(cfg.accent(), dp(14)), null, null, null);
                value.setCompoundDrawablePadding(dp(10));
            }
            value.setTextColor(focused ? Ui.TEXT : Ui.MUTED);
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        refreshers.add(() -> style(row));
        return row;
    }

    /** "Weather city": typed once, looked up on Open-Meteo, saved with its coordinates. */
    private View cityRow() {
        LinearLayout row = row();
        row.addView(label("Weather city"), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView value = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        row.addView(value);
        row.setOnClickListener(v -> askCity());
        row.setOnKeyListener(this::leftToNav);
        refreshers.add(() -> {
            value.setText(cfg.get(Weather.PLACE, "Not set") + "  \u203A");
            style(row);
        });
        return row;
    }

    private void askCity() {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint("City or ZIP code");
        field.setText(cfg.get(Weather.CITY, ""));
        field.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field);
        AlertDialog d = new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Weather city")
                .setView(box)
                .setPositiveButton("Find", (dialog, w) -> lookUp(field.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private void lookUp(String city) {
        if (city.isEmpty()) return;
        Ui.notice(this, "Looking up " + city + "\u2026", Icons.PARTLY);
        new Thread(() -> {
            List<Weather.Place> found;
            try {
                found = Weather.geocode(city, 6);
            } catch (Exception e) {
                runOnUiThread(() -> Ui.notice(this, "No connection to the weather service", Icons.PARTLY));
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (found.isEmpty()) {
                    Ui.notice(this, "Could not find " + city, Icons.PARTLY);
                } else if (found.size() == 1) {
                    useCity(city, found.get(0));
                } else {
                    // several places share the name (or the ZIP code): let the viewer pick
                    CharSequence[] names = new CharSequence[found.size()];
                    for (int i = 0; i < names.length; i++) names[i] = found.get(i).full;
                    new AlertDialog.Builder(this, Ui.DIALOG)
                            .setTitle("Which one?")
                            .setItems(names, (d, which) -> useCity(city, found.get(which)))
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        }).start();
    }

    private void useCity(String typed, Weather.Place p) {
        cfg.set(Weather.CITY, typed);
        cfg.set(Weather.LAT, p.lat);
        cfg.set(Weather.LON, p.lon);
        cfg.set(Weather.PLACE, p.name);
        cfg.setOn(Config.WEATHER, true);
        Ui.notice(this, "Weather for " + p.name, Icons.PARTLY);
        refresh();
    }

    interface Label {
        String text();
    }

    /**
     * A check with a green or amber dot: {@code good} decides which text shows. OK on a check
     * that needs attention explains how to fix it; on a good one it does nothing.
     */
    private View status(String title, Cond good, Label okText, String badText, Runnable fix) {
        LinearLayout row = row();
        row.addView(label(title), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView value = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        value.setCompoundDrawablePadding(dp(10));
        row.addView(value);
        row.setOnClickListener(v -> {
            if (!good.ok()) fix.run();
        });
        row.setOnKeyListener(this::leftToNav);
        refreshers.add(() -> {
            boolean ok = good.ok();
            value.setText(ok ? okText.text() : badText + "  \u203A");
            value.setCompoundDrawablesRelative(Ui.dot(ok ? Ui.GOOD : Ui.WARN, dp(12)), null, null, null);
            style(row);
        });
        return row;
    }

    /**
     * The Home button is not ours. Fire OS 6 holds it with usage access, which its own settings
     * screen may let you switch on; Fire OS 7/8 need the accessibility hook from a computer.
     */
    private void fixHome() {
        String why = "Right now the Home button may open Amazon's home screen instead of this one.";
        String computer = "Whoever set up this TV can fix it from their computer by running the"
                + " install again (launcher/install.sh).";
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            Help.explain(this, "Home button needs a one-time setup", why + "\n\n" + computer);
            return;
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Home button needs a one-time setup")
                .setMessage(why + "\n\nYou may be able to fix it here: Open the setting, choose"
                        + " \"dumbtv\" and switch it on, then press Back.\n\nIf that does not work: " + computer)
                .setPositiveButton("Open the setting", (d, w) -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (RuntimeException e) {
                        Ui.notice(this, "This TV does not allow that here", -1);
                    }
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    private boolean step(Config.Choice c, int by) {
        int i = Arrays.asList(c.values).indexOf(cfg.get(c));
        int n = c.values.length;
        cfg.set(c.key, c.values[((i + by) % n + n) % n]);
        if (c == Config.TEXT) {
            recreate();
            return true;
        }
        refresh();
        return true;
    }

    private View action(String text, Runnable run) {
        LinearLayout row = row();
        row.addView(label(text), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView chevron = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        chevron.setText("›");
        row.addView(chevron);
        row.setOnClickListener(v -> run.run());
        row.setOnKeyListener(this::leftToNav);
        refreshers.add(() -> style(row));
        return row;
    }

    /** Left on a row without values goes to the group list. */
    private boolean leftToNav(View row, int code, KeyEvent e) {
        if (code != KeyEvent.KEYCODE_DPAD_LEFT || e.getAction() != KeyEvent.ACTION_DOWN) return false;
        row.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
        navItems.get(sectionOf(row)).requestFocus();
        return true;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    private void style(View row) {
        row.setBackground(Ui.rowFocus(cfg.accent(), dp(10), false));
    }

    private TextView label(String text) {
        TextView v = Ui.text(this, 16, Ui.TEXT, Ui.MEDIUM);
        v.setText(text);
        return v;
    }

    /**
     * A group heading (small, spaced capitals above its rows) and its entry in the left list.
     * Returns the group's index, for {@link #openSection}.
     */
    private int section(String name, int icon) {
        boolean first = headers.isEmpty();
        TextView v = Ui.text(this, 14, Ui.MUTED, Ui.MEDIUM);
        v.setText(name.toUpperCase(java.util.Locale.ROOT));
        v.setLetterSpacing(0.12f);
        v.setPadding(dp(16), dp(first ? 4 : 38), 0, dp(10));
        list.addView(v);
        headers.add(v);
        // a thin rule under the heading separates it from its settings
        list.addView(Ui.rule(this, 0x2EFFFFFF, 16, 0, 8));

        int index = navItems.size();
        TextView item = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
        item.setText(name);
        item.setTag(icon);
        item.setId(200 + index);
        item.setFocusable(true);
        item.setClickable(true);
        item.setCompoundDrawablePadding(dp(14));
        item.setPadding(dp(14), dp(13), dp(14), dp(13));
        int accent = cfg.accent();
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.round(accent, dp(12)));
        bg.addState(new int[] {android.R.attr.state_selected}, Ui.round(0x1FFFFFFF, dp(12)));
        bg.addState(new int[0], Ui.round(0, dp(12)));
        item.setBackground(bg);
        item.setTextColor(new android.content.res.ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {android.R.attr.state_selected}, {}},
                new int[] {Ui.ink(accent), Ui.TEXT, Ui.MUTED}));
        item.setOnFocusChangeListener((x, has) -> {
            if (has) {
                markSection(index);
                scroll.smoothScrollTo(0, index == 0 ? 0 : v.getTop());
            }
            navIcon(item);
        });
        item.setOnClickListener(x -> openSection(index));
        item.setOnKeyListener((x, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                x.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                openSection(index);
                return true;
            }
            // the ends of the group list stay put instead of jumping into the settings
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            if (code == KeyEvent.KEYCODE_DPAD_UP && index == 0) return true;
            return code == KeyEvent.KEYCODE_DPAD_DOWN && index == navItems.size() - 1;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP);
        lp.bottomMargin = dp(6);
        nav.addView(item, lp);
        navItems.add(item);
        if (first) item.setSelected(true);
        navIcon(item);
        return index;
    }

    private void pick(String kind) {
        if (Catalog.APPS.equals(kind)) {
            openingPicker = true;
            Picker.open(this, Picker.LIBRARY);
            return;
        }
        List<Item> items = Catalog.inputs(this, (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE));
        CharSequence[] names = new CharSequence[items.size()];
        boolean[] on = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = Catalog.label(cfg, items.get(i));
            on[i] = Catalog.shown(cfg, kind, items.get(i));
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.APPS.equals(kind) ? "Apps on home screen" : "Inputs on home screen")
                .setMultiChoiceItems(names, on, (d, which, checked) ->
                        Catalog.setShown(cfg, kind, items.get(which), checked))
                .setPositiveButton("Done", null)
                .show();
    }

    private void reset() {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Reset the home screen layout?")
                .setMessage("Order, names and hidden tiles go back to default. Colors and sizes stay.")
                .setPositiveButton("Reset", (d, w) -> cfg.removePrefix("name.", "apps.", "inputs."))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
