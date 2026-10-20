package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.Toast;

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

        // Five short groups. Rows that only matter for another setting appear when it is on
        // (the image rows with an image background, the night times with night mode, ...).
        Cond image = () -> "image".equals(cfg.get(Config.BACKGROUND));
        Cond weatherOn = () -> "on".equals(cfg.get(Config.WEATHER));
        Cond frameOn = () -> !"off".equals(cfg.get(Config.FRAME));
        Cond nightOn = () -> "on".equals(cfg.get(Config.NIGHT));

        section("Apps", Icons.GRID);
        list.addView(action("Favorites", () -> {
            openingPicker = true;
            Picker.open(this, Picker.FAVS);
        }));
        list.addView(action("App library", () -> pick(Catalog.APPS)));
        list.addView(action("Get more apps", () -> {
            Intent store = Catalog.storeIntent(this);
            if (store == null) Toast.makeText(this, "The Appstore is not available", Toast.LENGTH_SHORT).show();
            else startActivity(store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }));
        list.addView(choice(Config.APPS_LAYOUT));
        list.addView(choice(Config.RECENT));
        list.addView(choice(Config.INPUTS));
        list.addView(action("Inputs to show", () -> pick(Catalog.INPUTS)));
        list.addView(choice(Config.SWITCH));

        section("Look", Icons.PALETTE);
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

        section("Weather", Icons.PARTLY);
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
        list.addView(only(timeRow(Config.NIGHT_START, "Night starts at", 22 * 60), nightOn));
        list.addView(only(timeRow(Config.NIGHT_END, "Night ends at", 6 * 60), nightOn));
        list.addView(only(choice(Config.NIGHT_DIM), nightOn));
        list.addView(note("Night mode dims the home screen and keeps the photo frame off, so the TV"
                + " can fall asleep. The sleep timer is the moon button in the top bar."));

        section("More", Icons.DOTS);
        list.addView(action("Reset order, names and hidden tiles", this::reset));
        list.addView(action("Open Amazon's home screen", () -> {
            Guard.amazon(this);
            finish();
        }));
        list.addView(only(action("Remove sleep timer permission", () -> {
            Sleep.set(this, 0);
            Sleep.release(this);
            Toast.makeText(this, "Removed. The sleep timer will ask again next time.", Toast.LENGTH_LONG).show();
            refresh();
        }), () -> Sleep.allowed(this)));

        TextView about = Ui.text(this, 13, Ui.FAINT, Ui.MEDIUM);
        about.setLineSpacing(0, 1.4f);
        about.setPadding(dp(16), dp(28), 0, 0);
        about.setText("Settings file: " + cfg.dir + "/" + Config.FILE
                + "\nWallpaper: put wallpaper.jpg in that folder, then pick Background › Image file"
                + "\nSeveral wallpapers: put them in " + Config.WALLPAPERS + "/ and pick a rotation"
                + "\nPhotos for the photo frame: put them in " + PhotoFrame.FOLDER + "/"
                + "\nInternet is used only for the weather, and only while it is on. No tracking, no ads.");
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

    private static final String CHOICE = "choice", TIME = "time";

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
        row.setOnClickListener(v -> TimeChooser.show(this, title, cfg.time(key, fallback), clock24(), m -> {
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

    private boolean clock24() {
        String mode = cfg.get(Config.CLOCK);
        return "24h".equals(mode) || "auto".equals(mode) && android.text.format.DateFormat.is24HourFormat(this);
    }

    private String clockText(int m) {
        int h = m / 60, min = m % 60;
        if (clock24()) return String.format(java.util.Locale.ROOT, "%02d:%02d", h, min);
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
            if (CHOICE.equals(now.getTag())) Ui.hintLines(hintBar, "\u25C0 \u25B6", "Change", "Back", "To the groups");
            else if (TIME.equals(now.getTag())) Ui.hintLines(hintBar, "\u25C0 \u25B6", "15 minutes", "OK", "Exact time", "Back", "To the groups");
            else Ui.hintLines(hintBar, "OK", "Open", "Back", "To the groups");
        } else {
            Ui.hintLines(hintBar, "\u25B2 \u25BC", "Choose a group", "\u25B6", "Open it", "Back", "Close");
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
        View first = list.getChildAt(list.indexOfChild(header) + 1);
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
                // a drawable swatch: Fire OS "high contrast text" repaints colored text
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(cfg.accent());
                dot.setBounds(0, 0, dp(14), dp(14));
                value.setCompoundDrawablesRelative(dot, null, null, null);
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
        field.setHint("City, e.g. Cleveland");
        field.setText(cfg.get(Weather.CITY, ""));
        field.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field);
        AlertDialog d = new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Weather city")
                .setView(box)
                .setPositiveButton("Save", (dialog, w) -> lookUp(field.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private void lookUp(String city) {
        if (city.isEmpty()) return;
        Toast.makeText(this, "Looking up " + city + "\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String[] found;
            try {
                found = Weather.geocode(city);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "No connection to the weather service",
                        Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> {
                if (found == null) {
                    Toast.makeText(this, "Could not find " + city, Toast.LENGTH_LONG).show();
                    return;
                }
                cfg.set(Weather.CITY, city);
                cfg.set(Weather.LAT, found[0]);
                cfg.set(Weather.LON, found[1]);
                cfg.set(Weather.PLACE, found[2]);
                if (!"on".equals(cfg.get(Config.WEATHER))) cfg.set(Config.WEATHER.key, "on");
                Toast.makeText(this, "Weather for " + found[2], Toast.LENGTH_SHORT).show();
                refresh();
            });
        }).start();
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
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused},
                Ui.round((cfg.accent() & 0x00FFFFFF) | 0x33000000, dp(10)));
        bg.addState(new int[0], Ui.round(0x00000000, dp(10)));
        row.setBackground(bg);
    }

    private TextView label(String text) {
        TextView v = Ui.text(this, 16, Ui.TEXT, Ui.MEDIUM);
        v.setText(text);
        return v;
    }

    /** A group heading: small, spaced capitals above its rows. */
    private void section(String name, int icon) {
        boolean first = headers.isEmpty();
        TextView v = Ui.text(this, 14, Ui.MUTED, Ui.MEDIUM);
        v.setText(name.toUpperCase(java.util.Locale.ROOT));
        v.setLetterSpacing(0.12f);
        v.setPadding(dp(16), dp(first ? 4 : 34), 0, dp(10));
        list.addView(v);
        headers.add(v);

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
