package tv.dumbtv.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A full-screen grid of every installed app, for choosing Favorites or what shows in All apps.
 * OK ticks an app in or out at once (saved straight to home.conf); Back closes.
 */
public final class Picker extends Activity {
    static final String MODE = "mode", FAVS = "favs", LIBRARY = "library";
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int COLUMNS = 5;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Tile> tiles = new ArrayList<>();
    private Config cfg;
    private boolean favs;
    private TextView count;

    static void open(Activity from, String mode) {
        from.startActivity(new Intent(from, Picker.class).putExtra(MODE, mode));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        Ui.scale = cfg.textScale();
        favs = FAVS.equals(getIntent().getStringExtra(MODE));
        int accent = cfg.accent();
        List<Item> all = Catalog.apps(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {
            Ui.mix(accent, Config.APP_BASE, 0.78f), Config.APP_BASE, Config.APP_BASE}));
        ScrollView scroll = new Ui.SoundScroll(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        root.addView(scroll);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        int edge = dp(56);
        column.setPadding(edge, dp(40), edge, dp(110));
        scroll.addView(column);

        TextView title = Ui.text(this, 32, Ui.TEXT, Ui.LIGHT);
        title.setText(favs ? "Favorites" : "App library");
        column.addView(title);
        TextView hint = Ui.text(this, 17, Ui.MUTED, Ui.MEDIUM);
        hint.setLineSpacing(0, 1.35f);
        hint.setPadding(0, dp(10), 0, 0);
        hint.setText(favs
                ? "Pick the apps for your Favorites row. The number on an app is its place in the row.\n"
                        + "To change the order: on the home screen, hold OK on a favorite and choose Move."
                : "Ticked apps show in All apps. New TV apps show up there on their own;\n"
                        + "apps made for phones start hidden, so tick the ones you want.");
        column.addView(hint);
        count = Ui.text(this, 16, Ui.TEXT, Ui.MEDIUM);
        count.setPadding(0, dp(10), 0, dp(18));
        column.addView(count);

        int width = getResources().getDisplayMetrics().widthPixels - 2 * edge;
        int gap = dp(22);
        int w = (width - gap * (COLUMNS - 1)) / COLUMNS, h = w * 9 / 16;
        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setPadding(0, dp(10), 0, dp(4));
                column.addView(row);
            }
            Item it = all.get(i);
            Tile t = new Tile(this, it, Catalog.APPS, Catalog.label(cfg, it), w, h, "always", accent);
            t.setOnClickListener(v -> toggle((Tile) v));
            t.setOnFocusChangeListener((v, has) -> dim((Tile) v));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            if (i % COLUMNS != COLUMNS - 1) lp.rightMargin = gap;
            row.addView(t, lp);
            tiles.add(t);
        }
        // the keys for this screen, always visible at the bottom
        LinearLayout keys = new LinearLayout(this);
        keys.setGravity(Gravity.CENTER_VERTICAL);
        keys.setPadding(dp(20), dp(12), 0, dp(12));
        keys.setBackground(Ui.round(0xF0141519, dp(26)));
        Ui.hints(keys, "OK", favs ? "Add or remove" : "Show or hide", "\u25B2 \u25BC \u25C0 \u25B6", "Move around",
                "Back", "Done");
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        klp.bottomMargin = dp(28);
        root.addView(keys, klp);
        setContentView(root);
        refresh();
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();

        // banners load one by one in the background; each tile redraws when its art arrives
        PackageManager pm = getPackageManager();
        io.execute(() -> {
            for (Tile t : tiles) {
                if (Thread.currentThread().isInterrupted()) return;
                Catalog.art(pm, t.item);
                ui.post(t::invalidate);
            }
        });
    }

    /** HOME or an app closes the grid, so HOME lands on the home screen, not back here. */
    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private boolean picked(Item it) {
        return favs ? Catalog.isFavorite(cfg, it) : Catalog.shown(cfg, Catalog.APPS, it);
    }

    private void toggle(Tile t) {
        if (favs) Catalog.setFavorite(cfg, t.item, !Catalog.isFavorite(cfg, t.item));
        else Catalog.setShown(cfg, Catalog.APPS, t.item, !picked(t.item));
        refresh();
    }

    private void refresh() {
        List<String> order = cfg.list(Catalog.FAVORITES);
        int on = 0;
        for (Tile t : tiles) {
            boolean in = picked(t.item);
            if (in) on++;
            t.setBadge(!in ? null : favs ? String.valueOf(order.indexOf(t.item.id) + 1) : "✓");
            dim(t);
        }
        count.setText(favs
                ? (on == 0 ? "No favorites yet" : on == 1 ? "1 favorite" : on + " favorites")
                : on + " of " + tiles.size() + " apps in All apps");
    }

    /** Apps that are not picked fade back, unless they are the one in focus. */
    private void dim(Tile t) {
        t.setAlpha(picked(t.item) || t.isFocused() ? 1f : 0.45f);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
