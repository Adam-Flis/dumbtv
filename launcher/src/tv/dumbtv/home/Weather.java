package tv.dumbtv.home;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Optional weather from Open-Meteo (free, no account, no key). Off by default: with it off the
 * launcher opens no connection at all. Only the city's coordinates are sent.
 * Every call here blocks: run it off the UI thread.
 */
final class Weather {
    static final String CITY = "weather.city", LAT = "weather.lat", LON = "weather.lon",
            PLACE = "weather.place";

    /** One forecast, already turned into what the screen shows. */
    static final class Report {
        int temp, hi, lo, code;
        boolean day;
        /** The heads-up line ("Rain around 5 PM, take a jacket"), or null when nothing is worth saying. */
        String hint;
    }

    private Weather() {}

    /** Looks a city up; returns {lat, lon, "City, Region"} or null when nothing matches. */
    static String[] geocode(String city) throws IOException, JSONException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name="
                + URLEncoder.encode(city, "UTF-8");
        JSONArray results = new JSONObject(get(url)).optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);
        String place = r.getString("name");
        String region = r.optString("admin1", r.optString("country", ""));
        if (!region.isEmpty() && !region.equals(place)) place += ", " + region;
        return new String[] {String.valueOf(r.getDouble("latitude")), String.valueOf(r.getDouble("longitude")), place};
    }

    static Report fetch(String lat, String lon, boolean fahrenheit, boolean h24) throws IOException, JSONException {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day"
                + "&hourly=precipitation_probability,weather_code&forecast_hours=9"
                + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
                + (fahrenheit ? "&temperature_unit=fahrenheit" : "");
        JSONObject json = new JSONObject(get(url));
        JSONObject now = json.getJSONObject("current");
        JSONObject daily = json.getJSONObject("daily");
        Report r = new Report();
        r.temp = (int) Math.round(now.getDouble("temperature_2m"));
        r.code = now.getInt("weather_code");
        r.day = now.optInt("is_day", 1) == 1;
        r.hi = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        r.lo = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        r.hint = hint(r, json.getJSONObject("hourly"), fahrenheit, h24);
        return r;
    }

    /** A few plain rules, most urgent first. */
    private static String hint(Report r, JSONObject hourly, boolean f, boolean h24) throws JSONException {
        if (isStorm(r.code)) return "Thunderstorms right now, best to stay in";
        if (isSnow(r.code)) return "Snowing right now, dress warm";
        if (isRain(r.code)) return "Raining right now, grab an umbrella";
        JSONArray times = hourly.getJSONArray("time");
        JSONArray chance = hourly.getJSONArray("precipitation_probability");
        JSONArray codes = hourly.getJSONArray("weather_code");
        // index 0 is the current hour, already covered above
        for (int i = 1; i < times.length(); i++) {
            int code = codes.optInt(i);
            boolean likely = chance.optInt(i) >= 50 || isRain(code) || isSnow(code) || isStorm(code);
            if (!likely) continue;
            String at = clock(times.getString(i), h24);
            if (isSnow(code)) return "Snow likely around " + at + ", dress warm";
            if (isStorm(code)) return "Storms likely around " + at + ", take a jacket";
            return "Rain likely around " + at + ", take a jacket";
        }
        int freezing = f ? 32 : 0, chilly = f ? 45 : 7, hot = f ? 90 : 32;
        if (r.temp <= freezing) return "Freezing out, bundle up";
        if (r.temp <= chilly) return "Chilly out, take a jacket";
        if (r.hi >= hot) return "Hot today, stay hydrated";
        return null;
    }

    /** "2026-10-20T17:00" as "5 PM" or "17:00". */
    private static String clock(String iso, boolean h24) {
        int h = Integer.parseInt(iso.substring(11, 13));
        if (h24) return String.format(Locale.ROOT, "%02d:00", h);
        return (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " AM" : " PM");
    }

    private static boolean isRain(int c) {
        return c >= 51 && c <= 67 || c >= 80 && c <= 82;
    }

    private static boolean isSnow(int c) {
        return c >= 71 && c <= 77 || c == 85 || c == 86;
    }

    private static boolean isStorm(int c) {
        return c >= 95;
    }

    /** WMO weather code in words. */
    static String describe(int c, boolean day) {
        if (c == 0) return day ? "Sunny" : "Clear";
        if (c == 1) return day ? "Mostly sunny" : "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 55) return "Drizzle";
        if (c == 56 || c == 57 || c == 66 || c == 67) return "Freezing rain";
        if (c >= 61 && c <= 65) return c == 65 ? "Heavy rain" : "Rain";
        if (c >= 71 && c <= 75 || c == 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorms";
        return "";
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "dumbtv-home");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
