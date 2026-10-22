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
        int temp, hi, lo, code, feels, wind;
        boolean day;
        String windUnit;
        /** The next hours, nearest first. */
        final java.util.List<Hour> hours = new java.util.ArrayList<>();
        /** "Sunset 6:31 PM" or "Sunrise 7:40 AM", whichever comes next. */
        String sun;
        int tomorrowHi, tomorrowLo, tomorrowCode, tomorrowRain;
        /** The heads-up line ("Rain around 5 PM, take a jacket"), or null when nothing is worth saying. */
        String hint;
    }

    static final class Hour {
        String at;
        int temp, code, rain;
        boolean day;
    }

    private Weather() {}

    /** One geocoding match: what is saved ({@code name}) and what the list shows ({@code full}). */
    static final class Place {
        final String lat, lon, name, full;

        Place(String lat, String lon, String name, String full) {
            this.lat = lat;
            this.lon = lon;
            this.name = name;
            this.full = full;
        }
    }

    /**
     * Places matching a city name or a postal code, best first, at most {@code max}; empty when
     * nothing matches. Same-named results in one region are listed once.
     */
    static java.util.List<Place> geocode(String query, int max) throws IOException, JSONException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=" + (max * 2)
                + "&language=en&format=json&name=" + URLEncoder.encode(query, "UTF-8");
        JSONArray results = new JSONObject(get(url)).optJSONArray("results");
        java.util.List<Place> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; results != null && i < results.length() && out.size() < max; i++) {
            JSONObject r = results.getJSONObject(i);
            String city = r.getString("name"), region = r.optString("admin1", ""), country = r.optString("country", "");
            String name = region.isEmpty() || region.equals(city) ? city : city + ", " + region;
            String full = country.isEmpty() ? name : name + ", " + country;
            if (!seen.add(full)) continue;
            out.add(new Place(String.valueOf(r.getDouble("latitude")), String.valueOf(r.getDouble("longitude")), name, full));
        }
        return out;
    }

    private static volatile Report latest;
    private static volatile long latestAt;

    /** The last forecast this process fetched, if under an hour old (the screen saver dream reuses it). */
    static Report latest() {
        return System.currentTimeMillis() - latestAt < 60 * 60_000L ? latest : null;
    }

    /** "Sunny  ·  H 62°  L 39°": conditions and today's range, as the screen saver shows them. */
    static String summary(Report r) {
        String words = describe(r.code, r.day), range = "H " + r.hi + "\u00B0  L " + r.lo + "\u00B0";
        return words.isEmpty() ? range : words + "  \u00B7  " + range;
    }

    static Report fetch(String lat, String lon, boolean fahrenheit, boolean h24) throws IOException, JSONException {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,apparent_temperature,wind_speed_10m,weather_code,is_day"
                + "&hourly=temperature_2m,precipitation_probability,weather_code,is_day&forecast_hours=9"
                + "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,"
                + "sunrise,sunset&forecast_days=2&timezone=auto"
                + (fahrenheit ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "");
        JSONObject json = new JSONObject(get(url));
        JSONObject now = json.getJSONObject("current");
        JSONObject daily = json.getJSONObject("daily");
        Report r = new Report();
        r.temp = (int) Math.round(now.getDouble("temperature_2m"));
        r.code = now.getInt("weather_code");
        r.day = now.optInt("is_day", 1) == 1;
        r.hi = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        r.lo = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        r.feels = (int) Math.round(now.optDouble("apparent_temperature", r.temp));
        r.wind = (int) Math.round(now.optDouble("wind_speed_10m", 0));
        r.windUnit = fahrenheit ? "mph" : "km/h";
        JSONObject hourly = json.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        for (int i = 1; i < times.length() && r.hours.size() < 5; i++) {
            Hour h = new Hour();
            h.at = clock(times.getString(i), h24);
            h.temp = (int) Math.round(hourly.getJSONArray("temperature_2m").optDouble(i));
            h.code = hourly.getJSONArray("weather_code").optInt(i);
            h.rain = hourly.getJSONArray("precipitation_probability").optInt(i);
            h.day = hourly.getJSONArray("is_day").optInt(i, 1) == 1;
            r.hours.add(h);
        }
        // the next of sunrise / sunset; ISO local times compare as strings
        String time = now.getString("time");
        JSONArray rise = daily.getJSONArray("sunrise"), set = daily.getJSONArray("sunset");
        if (time.compareTo(rise.getString(0)) < 0) r.sun = "Sunrise " + minutes(rise.getString(0), h24);
        else if (time.compareTo(set.getString(0)) < 0) r.sun = "Sunset " + minutes(set.getString(0), h24);
        else r.sun = "Sunrise " + minutes(rise.optString(1, rise.getString(0)), h24);
        r.tomorrowHi = (int) Math.round(daily.getJSONArray("temperature_2m_max").optDouble(1));
        r.tomorrowLo = (int) Math.round(daily.getJSONArray("temperature_2m_min").optDouble(1));
        r.tomorrowCode = daily.getJSONArray("weather_code").optInt(1);
        r.tomorrowRain = daily.getJSONArray("precipitation_probability_max").optInt(1);
        r.hint = hint(r, hourly, fahrenheit, h24);
        latest = r;
        latestAt = System.currentTimeMillis();
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

    /** "2026-10-20T18:31" as "6:31 PM" or "18:31". */
    private static String minutes(String iso, boolean h24) {
        int h = Integer.parseInt(iso.substring(11, 13));
        String m = iso.substring(14, 16);
        if (h24) return String.format(Locale.ROOT, "%02d:%s", h, m);
        return (h % 12 == 0 ? 12 : h % 12) + ":" + m + (h < 12 ? " AM" : " PM");
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
