package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * All settings live in one plain {@code key=value} file in the app's external files dir,
 * so {@code adb pull}/{@code adb push} is the whole export/import story and a hand edit is
 * picked up on the next return to the home screen.
 */
final class Config {
    static final String FILE = "home.conf";

    static final class Choice {
        final String key, title;
        final String[] values, names;
        final String fallback;

        Choice(String key, String title, String fallback, String[] values, String[] names) {
            this.key = key;
            this.title = title;
            this.fallback = fallback;
            this.values = values;
            this.names = names;
        }

        String name(String value) {
            int i = Arrays.asList(values).indexOf(value);
            return i < 0 ? value : names[i];
        }
    }

    static final Choice ACCENT = new Choice("accent", "Accent color", "blue",
            new String[] {"white", "blue", "cyan", "green", "amber", "red", "pink", "violet"},
            new String[] {"White", "Blue", "Cyan", "Green", "Amber", "Red", "Pink", "Violet"});
    static final Choice TILES = new Choice("tiles", "Tile size", "medium",
            new String[] {"small", "medium", "large"},
            new String[] {"Small", "Medium", "Large"});
    static final Choice TEXT = new Choice("text", "Text size", "medium",
            new String[] {"small", "medium", "large", "xlarge"},
            new String[] {"Small", "Medium", "Large", "Extra large"});
    private static final float[] TEXT_SCALES = {0.9f, 1f, 1.15f, 1.3f};
    static final Choice TITLES = new Choice("titles", "Row title size", "medium",
            new String[] {"small", "medium", "large", "xlarge"},
            new String[] {"Small", "Medium", "Large", "Extra large"});
    private static final float[] TITLE_SCALES = {0.85f, 1f, 1.3f, 1.6f};

    float titleScale() {
        return TITLE_SCALES[Arrays.asList(TITLES.values).indexOf(get(TITLES))];
    }
    static final Choice LABELS = new Choice("labels", "App names", "focus",
            new String[] {"focus", "always", "never"},
            new String[] {"At top", "Under tile", "Off"});
    static final Choice CLOCK = new Choice("clock", "Clock", "auto",
            new String[] {"auto", "24h", "12h"},
            new String[] {"System", "24-hour", "12-hour"});
    static final Choice INPUTS = new Choice("inputs", "Inputs row", "on",
            new String[] {"on", "off"},
            new String[] {"Shown", "Hidden"});
    static final Choice BACKGROUND = new Choice("background", "Background", "app",
            new String[] {"app", "black", "graphite", "midnight", "image"},
            new String[] {"App color", "Black", "Graphite", "Midnight", "Image"});
    static final Choice DIM = new Choice("dim", "Image dim", "40",
            new String[] {"0", "20", "40", "60", "80"},
            new String[] {"0%", "20%", "40%", "60%", "80%"});

    static final Choice ROTATE = new Choice("rotate", "Wallpaper rotation", "off",
            new String[] {"off", "visit", "hourly", "daily", "daytime"},
            new String[] {"Off", "Every return home", "Every hour", "Every day", "By time of day"});

    static final Choice WEATHER = new Choice("weather", "Weather", "off",
            new String[] {"off", "on"},
            new String[] {"Off", "On"});
    static final Choice UNITS = new Choice("units", "Temperature", "f",
            new String[] {"f", "c"},
            new String[] {"\u00B0F", "\u00B0C"});

    static final Choice APPS_LAYOUT = new Choice("layout", "All apps layout", "grid",
            new String[] {"grid", "row"},
            new String[] {"Grid", "Scroll"});
    static final Choice RECENT = new Choice("recent", "Recently used row", "on",
            new String[] {"on", "off"},
            new String[] {"Shown", "Hidden"});
    /** Favorites row shown or hidden (Options › Home screen › Home screen layout). */
    static final Choice FAVS = new Choice("favs", "Favorites row", "on",
            new String[] {"on", "off"},
            new String[] {"Shown", "Hidden"});
    /** When a device on an HDMI input turns on, offer to switch to it. */
    static final Choice SWITCH = new Choice("switch", "Detect input", "on",
            new String[] {"on", "off"},
            new String[] {"On", "Off"});
    static final Choice NIGHT = new Choice("night", "Night mode schedule", "off",
            new String[] {"off", "on"},
            new String[] {"Off", "On"});
    /** Night dim in percent (a slider in Options); "override" is the top bar's temporary switch. */
    static final String NIGHT_START = "night.start", NIGHT_END = "night.end", NIGHT_DIM = "night.dim",
            NIGHT_OVERRIDE = "night.override";

    /** Minutes after midnight stored as "HH:MM" under {@code key}. */
    int time(String key, int fallback) {
        String v = get(key, "");
        int colon = v.indexOf(':');
        try {
            if (colon > 0) return (Integer.parseInt(v.substring(0, colon)) % 24) * 60 + Integer.parseInt(v.substring(colon + 1)) % 60;
        } catch (NumberFormatException ignored) {
            // falls back
        }
        return fallback;
    }

    void setTime(String key, int minutes) {
        set(key, String.format(Locale.ROOT, "%02d:%02d", minutes / 60 % 24, minutes % 60));
    }

    int nightStart() {
        return time(NIGHT_START, 22 * 60);
    }

    int nightEnd() {
        return time(NIGHT_END, 6 * 60);
    }

    /** The schedule: on, and the clock between start and end (which may cross midnight). */
    boolean nightScheduled() {
        if (!"on".equals(get(NIGHT))) return false;
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), s = nightStart(), e = nightEnd();
        if (s == e) return false;
        return s < e ? now >= s && now < e : now >= s || now < e;
    }

    /**
     * Night mode right now: the top bar's switch while it lasts ("on@until" / "off@until"), else
     * the schedule.
     */
    boolean nightNow() {
        String o = get(NIGHT_OVERRIDE, "");
        int at = o.indexOf('@');
        if (at > 0) {
            try {
                if (System.currentTimeMillis() < Long.parseLong(o.substring(at + 1))) return o.startsWith("on");
            } catch (NumberFormatException ignored) {
                // a broken value falls back to the schedule
            }
        }
        return nightScheduled();
    }

    /**
     * The top bar's switch: flips night mode now. Turned on, it lasts until the next scheduled
     * end; turned off, until the next scheduled start. Returns when that is.
     */
    long toggleNight() {
        boolean on = !nightNow();
        long until = next(on ? nightEnd() : nightStart());
        set(NIGHT_OVERRIDE, (on ? "on@" : "off@") + until);
        return until;
    }

    /** The next time the clock reads {@code minutes} after midnight. */
    static long next(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minutes / 60);
        c.set(Calendar.MINUTE, minutes % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    /** Night dim, 10-90 %. Older builds stored light / medium / strong. */
    int nightDim() {
        String v = get(NIGHT_DIM, "50");
        switch (v) {
            case "light": return 30;
            case "medium": return 50;
            case "strong": return 65;
            default:
                try {
                    return Math.max(10, Math.min(90, Integer.parseInt(v)));
                } catch (NumberFormatException e) {
                    return 50;
                }
        }
    }

    int nightAlpha() {
        return nightDim() * 255 / 100;
    }

    /** How much weather the header shows; one setting instead of four toggles. */
    static final Choice W_DETAIL = new Choice("weather.detail", "Weather details", "full",
            new String[] {"full", "simple", "today", "minimal"},
            new String[] {"Everything", "Next hours", "Today", "Temperature"});

    static final Choice FRAME = new Choice("frame", "Photo frame", "off",
            new String[] {"off", "1", "2", "5", "10"},
            new String[] {"Off", "After 1 min", "After 2 min", "After 5 min", "After 10 min"});
    static final Choice FRAME_FOR = new Choice("frame.for", "Photo frame lasts", "30",
            new String[] {"15", "30", "60", "120"},
            new String[] {"15 min", "30 min", "1 hour", "2 hours"});

    /** Home screen row order (Catalog kinds, comma-separated); set in Options › Home screen rows. */
    static final String ROW_ORDER = "rows.order";

    /** The home screen rows in their saved order. */
    List<String> rowOrder() {
        return parseRowOrder(get(ROW_ORDER, ""));
    }

    /** Every row kind exactly once: saved ones first, unknown names dropped, missing ones appended. */
    static List<String> parseRowOrder(String saved) {
        List<String> kinds = Arrays.asList(Catalog.ROWS);
        List<String> out = new ArrayList<>();
        for (String k : saved.split(",")) {
            k = k.trim();
            if (kinds.contains(k) && !out.contains(k)) out.add(k);
        }
        for (String k : kinds) {
            if (!out.contains(k)) out.add(k);
        }
        return out;
    }

    /** OLED burn-in care: the home screen drifts a few pixels and goes near black when idle. */
    static final Choice CARE = new Choice("care", "Screen care (OLED)", "on",
            new String[] {"off", "on"}, new String[] {"Off", "On"});
    static final Choice REST = new Choice("care.rest", "Go dark when idle", "10",
            new String[] {"5", "10", "20", "30"},
            new String[] {"After 5 min", "After 10 min", "After 20 min", "After 30 min"});

    static final Choice[] CHOICES = {ACCENT, TILES, TEXT, TITLES, BACKGROUND, CLOCK, LABELS, INPUTS, DIM, ROTATE,
        WEATHER, UNITS, W_DETAIL, FRAME, FRAME_FOR, APPS_LAYOUT, RECENT, SWITCH, NIGHT, CARE, REST, FAVS};

    /** Folder of wallpapers, next to home.conf, that Background › Image file rotates through. */
    static final String WALLPAPERS = "wallpapers";
    private static final String OFFSET = "wallpaper.offset";

    private static final int[] ACCENTS = {
        0xFFFFFFFF, 0xFF3D8BFF, 0xFF2BC4E8, 0xFF34C759, 0xFFFFB340, 0xFFFF5147, 0xFFFF5C9D, 0xFFA07CFF,
    };
    /** The base under each background; "app" lays a color glow over its near-black. */
    static final int APP_BASE = 0xFF08090C;
    private static final int[] BACKGROUNDS = {APP_BASE, 0xFF000000, 0xFF141518, 0xFF0B1220, 0xFF000000};

    private static Config instance;

    final File dir;
    private final File file;
    private final Map<String, String> values = new LinkedHashMap<>();
    private long signature = Long.MIN_VALUE;
    private int version;
    private long visits;

    static Config get(Context c) {
        if (instance == null) instance = new Config(c.getApplicationContext());
        return instance;
    }

    private Config(Context c) {
        File external = c.getExternalFilesDir(null);
        dir = external != null ? external : c.getFilesDir();
        file = new File(dir, FILE);
        refresh();
    }

    /** Changes every time a setting changes, from the UI or from the file on disk. */
    int version() {
        return version;
    }

    void refresh() {
        long sig = file.lastModified() * 31 + file.length();
        if (sig == signature) return;
        signature = sig;
        values.clear();
        if (file.isFile()) read();
        version++;
    }

    private void read() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.startsWith("#") || eq <= 0) continue;
                values.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
            // older builds kept the night start hour in "night" (21, 22, 23)
            String night = values.get(NIGHT.key);
            if (night != null && night.matches("\\d+")) {
                values.put(NIGHT.key, "on");
                if (!values.containsKey(NIGHT_START)) values.put(NIGHT_START, night + ":00");
            }
        } catch (IOException e) {
            Log.w("dumbtv", "could not read " + file, e);
        }
    }

    private void save() {
        File tmp = new File(dir, FILE + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write("# dumbtv home. Edit on the TV (Options) or here; unknown keys are kept.\n");
            for (Map.Entry<String, String> e : values.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue() + "\n");
            }
        } catch (IOException e) {
            Log.w("dumbtv", "could not write " + tmp, e);
            return;
        }
        if (!tmp.renameTo(file)) Log.w("dumbtv", "could not replace " + file);
        signature = file.lastModified() * 31 + file.length();
    }

    String get(String key, String fallback) {
        String v = values.get(key);
        return v == null || v.isEmpty() ? fallback : v;
    }

    /** Whether an on/off choice is on: the one place that knows the stored word is "on". */
    boolean on(Choice c) {
        return "on".equals(get(c));
    }

    /** Options › Clock: 24-hour time, or the TV's own choice when set to Auto. */
    boolean clock24(Context c) {
        String mode = get(CLOCK);
        return "24h".equals(mode) || "auto".equals(mode) && android.text.format.DateFormat.is24HourFormat(c);
    }

    /** Whether the photo frame comes on by itself (Photo frame is "off" or a delay in minutes). */
    boolean frameOn() {
        return !"off".equals(get(FRAME));
    }

    void setOn(Choice c, boolean on) {
        set(c.key, on ? "on" : "off");
    }

    String get(Choice c) {
        String v = values.get(c.key);
        return v != null && Arrays.asList(c.values).contains(v) ? v : c.fallback;
    }

    void set(String key, String value) {
        if (value == null || value.isEmpty()) values.remove(key);
        else values.put(key, value);
        version++;
        save();
    }

    List<String> list(String key) {
        List<String> out = new ArrayList<>();
        for (String s : get(key, "").split(",")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    void setList(String key, List<String> items) {
        // String.join is Android 8+; TextUtils.join keeps Fire OS 6 (Android 7.1) working.
        set(key, TextUtils.join(",", items));
    }

    void removePrefix(String... prefixes) {
        for (Iterator<String> it = values.keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            for (String p : prefixes) {
                if (k.startsWith(p)) {
                    it.remove();
                    break;
                }
            }
        }
        version++;
        save();
    }

    int accent() {
        String v = values.get(ACCENT.key);
        if (v != null && v.startsWith("#")) {
            try {
                return Color.parseColor(v) | 0xFF000000;
            } catch (IllegalArgumentException ignored) {
                // falls through to the named palette
            }
        }
        return ACCENTS[Arrays.asList(ACCENT.values).indexOf(get(ACCENT))];
    }

    float textScale() {
        return TEXT_SCALES[Arrays.asList(TEXT.values).indexOf(get(TEXT))];
    }

    int background() {
        return BACKGROUNDS[Arrays.asList(BACKGROUND.values).indexOf(get(BACKGROUND))];
    }

    /** Counts returns to the home screen, for the "visit" rotation. */
    void visited() {
        visits++;
    }

    /** Steps the rotation forward by one, so the next wallpaper shows now. */
    void nextWallpaper() {
        set(OFFSET, String.valueOf(offset() + 1));
    }

    private int offset() {
        try {
            return Integer.parseInt(get(OFFSET, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 0 morning (6-13 h), 1 afternoon (13-20 h), 2 night. */
    static int period() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 6 && h < 13 ? 0 : h >= 13 && h < 20 ? 1 : 2;
    }

    private static final String[][] PERIOD_WORDS = {
        {"morning", "day"},
        {"afternoon", "evening"},
        {"night", "midnight"},
    };

    /**
     * Files whose name carries a word for {@code period}; empty when others are tagged but not
     * this one, null when no file is tagged at all (then the period just indexes the pool).
     */
    private static List<File> forPeriod(List<File> pool, int period) {
        List<File> fit = new ArrayList<>();
        boolean tagged = false;
        for (File f : pool) {
            // Whole words only, so "sunday.jpg" or "holiday.jpg" is not a morning picture.
            List<String> words = Arrays.asList(f.getName().toLowerCase(Locale.ROOT).split("[^a-z]+"));
            for (int p = 0; p < PERIOD_WORDS.length; p++) {
                for (String w : PERIOD_WORDS[p]) {
                    if (words.contains(w)) {
                        tagged = true;
                        if (p == period && !fit.contains(f)) fit.add(f);
                    }
                }
            }
        }
        return tagged ? fit : null;
    }

    List<File> wallpapers() {
        List<File> out = new ArrayList<>();
        File[] files = new File(dir, WALLPAPERS).listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                    || n.endsWith(".webp"))) {
                out.add(f);
            }
        }
        Collections.sort(out);
        return out;
    }

    File wallpaper() {
        List<File> pool = wallpapers();
        if (!pool.isEmpty()) {
            long now = System.currentTimeMillis();
            long step;
            String mode = get(ROTATE);
            if ("daytime".equals(mode)) {
                int period = period();
                List<File> fit = forPeriod(pool, period);
                if (fit == null) return pool.get(period % pool.size());
                if (!fit.isEmpty()) pool = fit;
                mode = "visit";
            }
            switch (mode) {
                case "visit": step = visits; break;
                case "hourly": step = now / 3_600_000L; break;
                case "daily": step = (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L; break;
                default: step = 0;
            }
            int n = pool.size();
            return pool.get((int) (((step + offset()) % n + n) % n));
        }
        for (String name : new String[] {"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) {
            File f = new File(dir, name);
            if (f.isFile()) return f;
        }
        return null;
    }
}
