package tv.dumbtv.home;

import java.util.Arrays;
import java.util.List;

/**
 * Checks the launcher's pure logic on a plain JVM, no TV needed: ./launcher/test.sh
 * Only code that touches no Android runtime can be tested here (android.jar is stubs).
 */
public final class LogicTest {
    private static int failed, passed;

    public static void main(String[] args) {
        sleepLabels();
        sleepChip();
        rowOrder();
        minuteSteps();
        rowSwitches();
        pictures();
        distinctNames();
        sky();
        antenna();
        spring();
        screenSaverTimers();
        screenSaverChoices();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void sleepLabels() {
        eq("Off", Sleep.label(0));
        eq("15 min", Sleep.label(15));
        eq("1 hour", Sleep.label(60));
        eq("1 hr 30 min", Sleep.label(90));
        eq("2 hours", Sleep.label(120));
        eq("9 hr 55 min", Sleep.label(595));
    }

    private static void sleepChip() {
        eq("Sleep timer", Sleep.chip(0));
        eq("Sleep in 45 min", Sleep.chip(45));
        eq("Sleep in 1:00", Sleep.chip(60));
        eq("Sleep in 1:55", Sleep.chip(115));
    }

    private static void rowOrder() {
        List<String> standard = Arrays.asList(Catalog.ROWS);
        eq(standard, Config.parseRowOrder(""));
        eq(Arrays.asList("recent", "favs", "apps", "inputs"), Config.parseRowOrder("recent,favs,apps,inputs"));
        // missing kinds are appended in default order; unknown and repeated names dropped
        eq(Arrays.asList("inputs", "favs", "recent", "apps"), Config.parseRowOrder("inputs"));
        eq(Arrays.asList("apps", "favs", "recent", "inputs"), Config.parseRowOrder(" apps , bogus, apps"));
    }

    private static void rowSwitches() {
        // every row but All apps can be hidden, so no app is ever unreachable
        eq(true, Catalog.rowSwitch(Catalog.APPS) == null);
        eq(Config.FAVS, Catalog.rowSwitch(Catalog.FAVS));
        eq(Config.RECENT, Catalog.rowSwitch(Catalog.RECENT));
        eq(Config.INPUTS, Catalog.rowSwitch(Catalog.INPUTS));
    }

    private static void minuteSteps() {
        eq(5, TimeChooser.stepMinute(0, 1));
        eq(55, TimeChooser.stepMinute(0, -1));
        eq(0, TimeChooser.stepMinute(55, 1));
        eq(50, TimeChooser.stepMinute(47, 1));
        eq(45, TimeChooser.stepMinute(47, -1));
        eq(40, TimeChooser.stepMinute(45, -1));
    }

    private static void pictures() {
        eq(true, Images.isPicture("Beach.JPG"));
        eq(true, Images.isPicture("a.webp"));
        eq(false, Images.isPicture("notes.txt"));
        // a 4K photo shrinks to the 1080p screen; a 1080p one stays as is
        eq(2, Images.sampleSize(3840, 2160, 1920, 1080));
        eq(1, Images.sampleSize(1920, 1080, 1920, 1080));
        eq(2, Images.sampleSize(4000, 3000, 1920, 1080));
        // a panorama shrinks by its long side too, instead of decoding 20000 px wide
        eq(8, Images.sampleSize(20000, 1000, 1920, 1080));
    }

    private static void distinctNames() {
        List<String> names = Arrays.asList("Prime Video", "Netflix", "prime video ", "YouTube", "Hulu", "Max", "Plex");
        eq(Arrays.asList("Prime Video", "Netflix", "YouTube", "Hulu", "Max"), Catalog.distinct(names, s -> s, 5));
        eq(Arrays.asList("Prime Video"), Catalog.distinct(names, s -> s, 1));
    }

    private static void sky() {
        // the moments themselves, then a blend halfway between midday and golden hour
        eq(0xFF0B1026, Sky.colors(0)[0]);
        eq(0xFF5890BC, Sky.colors(780)[1]);
        eq(Sky.mix(0xFF1A527F, 0xFF2D4472, 0.5f), Sky.colors(915)[0]);
        // wraps around midnight, and the ground under the rows is darker than the sky
        eq(Sky.colors(0)[0], Sky.colors(1440)[0]);
        eq(Sky.colors(1439)[0], Sky.colors(-1)[0]);
        eq(true, (Sky.colors(780)[2] & 0xFF) < (Sky.colors(780)[0] & 0xFF));
        eq(0xFF808080, Sky.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
    }

    private static void antenna() {
        // the TV's own tuner has a hardware id; an app's channels input does not
        eq(true, Catalog.isAntenna("com.mstar.android.tv.inputservice/com.amazon.android.tv.inputservice.TunerInputService/HW1", false, 0));
        eq(false, Catalog.isAntenna("com.hulu.plus/dev.cobalt.epg.TvInputService", false, 0));
        eq(false, Catalog.isAntenna("com.mstar.android.tv.inputservice/.Hdmi1InputService/HW23", true, 1007));
        List<String> numbers = new java.util.ArrayList<>(Arrays.asList("19.1", "5.2", "5.1", "43", "8.10", "8.2", "3"));
        java.util.Collections.sort(numbers, Catalog::compareChannelNumbers);
        eq(Arrays.asList("3", "5.1", "5.2", "8.2", "8.10", "19.1", "43"), numbers);
    }

    private static void spring() {
        eq(true, Math.abs(Tween.springOut(0f)) < 1e-5f);
        eq(true, Math.abs(Tween.springOut(1f) - 1f) < 1e-5f);
        float max = 0;
        for (int i = 0; i <= 100; i++) max = Math.max(max, Tween.springOut(i / 100f));
        // overshoots a little, never a lot
        eq(true, max > 1.03f && max < 1.12f);
        // Tile motion › Bounce: a springier spring, still settling exactly at the end
        float bounce = 0;
        for (int i = 0; i <= 100; i++) bounce = Math.max(bounce, Tween.springOut(i / 100f, 3f));
        eq(true, bounce > 1.15f && bounce < 1.35f);
        eq(true, Math.abs(Tween.springOut(1f, 3f) - 1f) < 1e-5f);
        // every title color but Accent has its own color; Accent comes last
        eq("accent", Config.TITLE_COLOR.values[Config.TITLE_COLOR.values.length - 1]);
        eq(Config.TITLE_COLOR.values.length, Config.TITLE_COLOR.names.length);
        eq(Config.STYLE.values.length, Config.STYLE.names.length);
        eq(Config.MOTION.values.length, Config.MOTION.names.length);
    }

    private static void screenSaverTimers() {
        // Fire TV sleeps 5 min after the screen saver's own end; with it off, at the start time
        eq(140, SystemSaver.sleepMinutes(15, 120));
        eq(37, SystemSaver.sleepMinutes(2, 30));
        eq(5, SystemSaver.sleepMinutes(5, 0));
    }

    private static void screenSaverChoices() {
        // every value but Off is a whole number of minutes (SystemSaver and Home parse them)
        for (String v : Config.FRAME.values) {
            if (!Config.OFF.equals(v)) eq(true, Integer.parseInt(v) > 0);
        }
        for (String v : Config.FRAME_FOR.values) eq(true, Integer.parseInt(v) > 0 && Integer.parseInt(v) <= 120);
        eq("5 min", Config.FRAME.name("5"));
        eq("1.5 hours", Config.FRAME_FOR.name("90"));
    }

    private static void eq(Object want, Object got) {
        if (want.equals(got)) {
            passed++;
        } else {
            failed++;
            StackTraceElement at = new Throwable().getStackTrace()[1];
            System.out.println("FAIL " + at.getMethodName() + ":" + at.getLineNumber() + "  want " + want + "  got " + got);
        }
    }
}
