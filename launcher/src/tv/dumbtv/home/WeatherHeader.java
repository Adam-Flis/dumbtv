package tv.dumbtv.home;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The weather beside the home screen's clock: now (icon, temperature, conditions), the next
 * hours after a thin line, and under them the next sunrise/sunset and tomorrow. How much shows
 * follows Options › Weather details. It only draws a forecast; Home fetches it.
 */
final class WeatherHeader {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Context c;
    /** One forecast hour's width; the row shows as many hours as fit beside the weather. */
    private static final int CELL_DP = 60;

    private final LinearLayout now, hours, column;
    private final View icon, rule;
    private final TextView temp, sky, feels, sunLine;
    private final LinearLayout forecast;
    /** What is on screen now, so the once-a-minute clock tick does not rebuild the same views. */
    private Weather.Report shown;
    private String shownDetail;

    WeatherHeader(Context c) {
        this.c = c;
        // icon, temperature, then conditions and feels-like stacked beside them: two short lines
        now = new LinearLayout(c);
        now.setGravity(Gravity.CENTER_VERTICAL);
        icon = new View(c);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(10);
        now.addView(icon, ilp);
        temp = Ui.text(c, 40, Ui.TEXT, Ui.PLAIN_LIGHT);
        now.addView(temp);
        LinearLayout details = new LinearLayout(c);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(14), 0, 0, 0);
        sky = Ui.text(c, 19, Ui.TEXT, Ui.MEDIUM);
        sky.setSingleLine();
        feels = Ui.text(c, 18, 0xB3FFFFFF, Ui.MEDIUM);
        feels.setSingleLine();
        feels.setPadding(0, dp(5), 0, 0);
        details.addView(sky);
        details.addView(feels);
        now.addView(details);
        now.setVisibility(View.GONE);
        // current weather, and under it the next sunrise/sunset and tomorrow
        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(24), 0, 0, 0);
        // its own width (a vertical LinearLayout would give it the widest sibling's and clip it)
        column.addView(now, new LinearLayout.LayoutParams(WRAP, WRAP));
        // two short lines (the next sunrise or sunset, then tomorrow) rather than one long one
        sunLine = Ui.text(c, 18, 0xB3FFFFFF, Ui.MEDIUM);
        sunLine.setMaxLines(2);
        sunLine.setLineSpacing(Ui.dp(c, 4), 1f);
        sunLine.setPadding(dp(2), dp(10), 0, 0);
        sunLine.setVisibility(View.GONE);
        column.addView(sunLine, new LinearLayout.LayoutParams(WRAP, WRAP));
        // the next hours sit right after the current weather, split by a thin line
        rule = new View(c);
        rule.setBackgroundColor(0x2EFFFFFF);
        rule.setVisibility(View.GONE);
        forecast = new LinearLayout(c);
        forecast.setOrientation(LinearLayout.VERTICAL);
        hours = new LinearLayout(c);
        hours.setVisibility(View.GONE);
        forecast.addView(hours, new LinearLayout.LayoutParams(WRAP, WRAP));
        // padding, not a margin: a centered child's margin shifts it and cut off the last line
        forecast.setPadding(0, dp(16), 0, 0); // clear of the top bar when a button opens up
    }

    /** Adds the current weather, the line and the next hours to the clock's line, in that order. */
    void addTo(LinearLayout line) {
        line.addView(column);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(56));
        rlp.leftMargin = dp(20);
        rlp.rightMargin = dp(8);
        rlp.topMargin = dp(16);
        line.addView(rule, rlp);
        line.addView(forecast, new LinearLayout.LayoutParams(WRAP, WRAP));
    }

    /** Shows {@code w} with {@code detail} (Config.W_DETAIL); null hides the weather. */
    void show(Weather.Report w, String detail) {
        if (w == shown && detail.equals(shownDetail)) return;
        shown = w;
        shownDetail = detail;
        if (w == null) {
            now.setVisibility(View.GONE);
            hours.setVisibility(View.GONE);
            rule.setVisibility(View.GONE);
            sunLine.setVisibility(View.GONE);
            return;
        }
        temp.setText(w.temp + "°");
        String words = Weather.describe(w.code, w.day);
        boolean full = "full".equals(detail);
        // Everything: compact, room for feels/wind and tomorrow. The shorter choices use the room
        // for a bigger temperature with the conditions and today's high/low beside it.
        float big = full ? 1f : 1.4f;
        temp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40 * big * Ui.scale);
        sky.setTextSize(TypedValue.COMPLEX_UNIT_SP, (full ? 19 : 22) * Ui.scale);
        feels.setTextSize(TypedValue.COMPLEX_UNIT_SP, (full ? 18 : 22) * Ui.scale);
        int iconSize = dp(40 * big);
        ViewGroup.LayoutParams ip = icon.getLayoutParams();
        if (ip.width != iconSize) {
            ip.width = iconSize;
            ip.height = iconSize;
            icon.setLayoutParams(ip);
        }
        icon.setBackground(Icons.of(Icons.forCode(w.code, w.day), iconSize, 0));
        String highLow = "H " + w.hi + "°  L " + w.lo + "°";
        if (full) {
            sky.setText((words.isEmpty() ? "" : words + "  ·  ") + highLow);
            feels.setText("Feels " + w.feels + "°  ·  Wind " + w.wind + " " + w.windUnit);
        } else {
            sky.setText(words.isEmpty() ? highLow : words);
            feels.setText(words.isEmpty() ? "" : highLow);
        }
        boolean minimal = "minimal".equals(detail);
        sky.setVisibility(minimal ? View.GONE : View.VISIBLE);
        feels.setVisibility(minimal || feels.length() == 0 ? View.GONE : View.VISIBLE);
        now.setVisibility(View.VISIBLE);
        showHours(w, detail);
        if (full && w.sun != null) {
            String t = Weather.describe(w.tomorrowCode, true);
            sunLine.setText(w.sun + "\nTomorrow " + w.tomorrowHi + "° / "
                    + w.tomorrowLo + "°" + (t.isEmpty() ? "" : "  " + t)
                    + (w.tomorrowRain >= 30 ? "  " + w.tomorrowRain + "%" : ""));
            sunLine.setVisibility(View.VISIBLE);
        } else {
            sunLine.setVisibility(View.GONE);
        }
    }

    /** The next five hours: time, icon, temperature, chance of rain. */
    private void showHours(Weather.Report w, String detail) {
        hours.removeAllViews();
        if ("minimal".equals(detail) || "today".equals(detail) || w.hours.isEmpty()) {
            hours.setVisibility(View.GONE);
            rule.setVisibility(View.GONE);
            return;
        }
        // the rain line only takes room when one of the hours has a real chance of rain
        boolean anyRain = false;
        for (Weather.Hour h : w.hours) anyRain |= h.rain >= 20;
        for (Weather.Hour h : w.hours) {
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView at = Ui.text(c, 17, 0xB3FFFFFF, Ui.MEDIUM);
            at.setText(h.at);
            View hourIcon = new View(c);
            hourIcon.setBackground(Icons.of(Icons.forCode(h.code, h.day), dp(28), 0));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(28));
            ilp.topMargin = dp(4);
            ilp.bottomMargin = dp(4);
            TextView t = Ui.text(c, 20, Ui.TEXT, Ui.MEDIUM);
            t.setText(h.temp + "°");
            TextView rain = Ui.text(c, 15, 0xB3FFFFFF, Ui.MEDIUM);
            rain.setPadding(0, dp(2), 0, 0);
            rain.setText(h.rain >= 20 ? h.rain + "%" : "");
            rain.setVisibility(anyRain ? View.VISIBLE : View.GONE);
            // a vertical LinearLayout gives children full width by default: center their text too
            for (TextView line : new TextView[] {at, t, rain}) line.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.addView(at);
            cell.addView(hourIcon, ilp);
            cell.addView(t);
            cell.addView(rain);
            hours.addView(cell, new LinearLayout.LayoutParams(dp(CELL_DP), WRAP));
        }
        hours.setVisibility(View.VISIBLE);
        rule.setVisibility(now.getVisibility());
    }

    /**
     * Shows as many of the next hours as fit: {@code spare} is the room left on the clock's line
     * (negative when it overflows). Returns true when it changed, so the line is measured again.
     */
    boolean fitHours(int spare) {
        int n = hours.getChildCount(), shown = 0, cell = dp(CELL_DP);
        for (int i = 0; i < n; i++) if (hours.getChildAt(i).getVisibility() == View.VISIBLE) shown++;
        // grows only with a little to spare, so a pass never undoes the one before it
        int want = spare < 0 ? shown - (-spare + cell - 1) / cell
                : Math.min(n, shown + Math.max(0, spare - dp(8)) / cell);
        want = Math.max(0, want);
        if (want == shown || hours.getVisibility() != View.VISIBLE) return false;
        for (int i = 0; i < n; i++) hours.getChildAt(i).setVisibility(i < want ? View.VISIBLE : View.GONE);
        return true;
    }

    private int dp(float v) {
        return Ui.dp(c, v);
    }
}
