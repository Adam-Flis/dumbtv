package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Options › Home screen rows: the four rows in their order. OK picks a row up, up/down moves
 * it, OK puts it down. Left/right shows or hides a row; All apps always shows, so every app
 * stays one press away.
 */
final class RowsEditor {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Activity a;
    private final Config cfg;
    private final List<String> order;
    private final Map<String, LinearLayout> views = new HashMap<>();
    private final Map<String, ValueRow> rows = new HashMap<>();
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
        Dialog d = Panel.dialog(a);
        LinearLayout panel = Panel.box(a);
        Popup.title(panel, "Home screen layout");
        Popup.subtitle(panel, "Top to bottom");
        panel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));
        hints.setPadding(0, dp(20), 0, 0);
        panel.addView(hints, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP));

        for (String kind : order) views.put(kind, row(kind));
        render();
        d.setOnDismissListener(x -> {
            if (done != null) done.run();
        });
        Panel.show(a, d, panel, 640);
        views.get(order.get(0)).requestFocus();
    }

    private LinearLayout row(String kind) {
        ValueRow row = new ValueRow(a, Catalog.rowTitle(kind), Catalog.rowIcon(kind));
        rows.put(kind, row);
        row.view.setOnFocusChangeListener((v, has) -> update());
        row.view.setOnKeyListener((v, code, e) -> key(kind, v, code, e));
        return row.view;
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
                cfg.set(Config.ROW_ORDER, android.text.TextUtils.join(",", order));
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
            Config.Choice c = Catalog.rowSwitch(kind);
            if (c != null) {
                cfg.setOn(c, !cfg.on(c));
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
        boolean anyFocused = false;
        for (String kind : order) {
            ValueRow row = rows.get(kind);
            boolean picked = kind.equals(moving);
            anyFocused |= row.view.isFocused();
            Config.Choice c = Catalog.rowSwitch(kind);
            boolean shown = c == null || cfg.on(c);
            row.show(picked ? "\u25B2 \u25BC" : c == null ? "Always shown" : shown ? "Shown" : "Hidden", shown, c != null);
            row.strong(picked);
        }
        if (moving != null) Ui.hints(hints, "\u25B2 \u25BC", "Move the row", "OK", "Put it down");
        else if (anyFocused) Ui.hints(hints, "OK", "Reorder", "\u25C0 \u25B6", "Show or hide", "Back", "Done");
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }
}
