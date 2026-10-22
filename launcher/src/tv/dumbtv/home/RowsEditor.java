package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Options › Home screen rows: the four rows in their order. OK picks a row up, up/down moves
 * it, OK puts it down. Left/right shows or hides Recently used and Inputs; Favorites and All
 * apps always show, so every app stays one press away.
 */
final class RowsEditor {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Activity a;
    private final Config cfg;
    private final List<String> order;
    private final Map<String, LinearLayout> views = new HashMap<>();
    private final Map<String, TextView> states = new HashMap<>(), names = new HashMap<>();
    private final LinearLayout list, hints;
    private String moving;

    private RowsEditor(Activity a) {
        this.a = a;
        cfg = Config.get(a);
        order = cfg.rowOrder();
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        hints = new LinearLayout(a);
        hints.setGravity(Gravity.CENTER);
    }

    static void show(Activity a, Runnable done) {
        new RowsEditor(a).open(done);
    }

    private void open(Runnable done) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = Help.panel(a);
        TextView head = Ui.text(a, 24, Ui.TEXT, Ui.LIGHT);
        head.setText("Home screen rows");
        head.setPadding(dp(16), 0, 0, dp(4));
        panel.addView(head);
        TextView sub = Ui.text(a, 15, Ui.MUTED, Ui.MEDIUM);
        sub.setText("Top to bottom, the way they appear on the home screen");
        sub.setPadding(dp(16), 0, 0, dp(16));
        panel.addView(sub);
        panel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        hints.setPadding(0, dp(20), 0, 0);
        panel.addView(hints, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));

        for (String kind : order) views.put(kind, row(kind));
        render();
        d.setOnDismissListener(x -> {
            if (done != null) done.run();
        });
        Help.show(a, d, panel, 640);
        views.get(order.get(0)).requestFocus();
    }

    private LinearLayout row(String kind) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        View art = new View(a);
        art.setBackground(Icons.of(icon(kind), dp(22), 0xD9FFFFFF));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(22), dp(22));
        alp.rightMargin = dp(16);
        row.addView(art, alp);
        TextView name = Ui.text(a, 18, Ui.TEXT, Ui.MEDIUM);
        name.setText(title(kind));
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView state = Ui.text(a, 16, Ui.MUTED, Ui.MEDIUM);
        row.addView(state);
        names.put(kind, name);
        states.put(kind, state);
        row.setOnFocusChangeListener((v, has) -> update());
        row.setOnKeyListener((v, code, e) -> key(kind, v, code, e));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    private boolean key(String kind, View v, int code, KeyEvent e) {
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (kind.equals(moving)) {
            // a picked-up row swallows every key until it is put down
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            int at = order.indexOf(kind);
            int to = code == KeyEvent.KEYCODE_DPAD_UP ? at - 1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? at + 1 : at;
            if (to != at && to >= 0 && to < order.size()) {
                order.remove(at);
                order.add(to, kind);
                cfg.set(Config.ROWS, android.text.TextUtils.join(",", order));
                v.playSoundEffect(to < at ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
                render();
                v.requestFocus();
            } else if (ok || code == KeyEvent.KEYCODE_BACK) {
                moving = null;
                update();
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (ok) {
            moving = kind;
            v.playSoundEffect(SoundEffectConstants.CLICK);
            update();
            return true;
        }
        if ((code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            Config.Choice c = toggle(kind);
            if (c != null) {
                cfg.set(c.key, "on".equals(cfg.get(c)) ? "off" : "on");
                v.playSoundEffect(code == KeyEvent.KEYCODE_DPAD_LEFT
                        ? SoundEffectConstants.NAVIGATION_LEFT : SoundEffectConstants.NAVIGATION_RIGHT);
                update();
            }
            return true;
        }
        return false;
    }

    /** Puts the rows back in {@link #order}. */
    private void render() {
        list.removeAllViews();
        for (String kind : order) list.addView(views.get(kind));
        update();
    }

    private void update() {
        int accent = cfg.accent();
        boolean anyFocused = false;
        for (String kind : order) {
            LinearLayout row = views.get(kind);
            boolean focused = row.isFocused(), picked = kind.equals(moving);
            anyFocused |= focused;
            Config.Choice c = toggle(kind);
            boolean shown = c == null || "on".equals(cfg.get(c));
            String text = picked ? "▲ ▼" : c == null ? "Always shown" : shown ? "Shown" : "Hidden";
            states.get(kind).setText(focused && !picked && c != null ? "‹  " + text + "  ›" : text);
            states.get(kind).setTextColor(picked || focused ? Ui.TEXT : Ui.MUTED);
            names.get(kind).setAlpha(shown ? 1f : 0.5f);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[] {android.R.attr.state_focused},
                    picked ? Ui.round(accent, dp(12)) : Ui.round((accent & 0x00FFFFFF) | 0x33000000, dp(12)));
            bg.addState(new int[0], Ui.round(0, dp(12)));
            row.setBackground(bg);
        }
        if (moving != null) Ui.hints(hints, "▲ ▼", "Move the row", "OK", "Put it down");
        else if (anyFocused) Ui.hints(hints, "OK", "Pick up to move", "◀ ▶", "Show or hide", "Back", "Done");
    }

    /** The on/off setting behind a row, or null when the row always shows. */
    private static Config.Choice toggle(String kind) {
        if (Catalog.RECENT.equals(kind)) return Config.RECENT;
        if (Catalog.INPUTS.equals(kind)) return Config.INPUTS;
        return null;
    }

    static String title(String kind) {
        switch (kind) {
            case Catalog.FAVS: return "Favorites";
            case Catalog.RECENT: return "Recently used";
            case Catalog.APPS: return "All apps";
            default: return "Inputs";
        }
    }

    static int icon(String kind) {
        switch (kind) {
            case Catalog.FAVS: return Icons.STAR;
            case Catalog.RECENT: return Icons.CLOCK;
            case Catalog.APPS: return Icons.GRID;
            default: return Icons.INPUT;
        }
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }
}
