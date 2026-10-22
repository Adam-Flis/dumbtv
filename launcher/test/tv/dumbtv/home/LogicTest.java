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

    private static void minuteSteps() {
        eq(5, TimeChooser.stepMinute(0, 1));
        eq(55, TimeChooser.stepMinute(0, -1));
        eq(0, TimeChooser.stepMinute(55, 1));
        eq(50, TimeChooser.stepMinute(47, 1));
        eq(45, TimeChooser.stepMinute(47, -1));
        eq(40, TimeChooser.stepMinute(45, -1));
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
