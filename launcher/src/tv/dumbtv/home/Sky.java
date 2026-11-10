package tv.dumbtv.home;

/**
 * Background › Sky: the home screen's background follows the sky through the day, from a
 * starless night blue through a rose sunrise, clear blues, a golden late afternoon and a pink
 * sunset back to night. Colors are blended minute by minute between a few moments, so it never
 * jumps and nothing on screen stays the same for long (kind to OLED). Kept dark enough that
 * white text reads on every one of them.
 */
final class Sky {
    /** Minutes after midnight, then the color at the top of the screen and at the horizon. */
    private static final int[][] MOMENTS = {
        {0, 0xFF0B1026, 0xFF1B2040},
        {330, 0xFF14163A, 0xFF2E2650},   // 5:30, before dawn
        {420, 0xFF2E3260, 0xFF9A5A72},   // 7:00, sunrise
        {540, 0xFF1F4874, 0xFF5F86AE},   // 9:00, morning
        {780, 0xFF1A527F, 0xFF5890BC},   // 13:00, midday
        {1050, 0xFF2D4472, 0xFFB07A52},  // 17:30, golden hour
        {1140, 0xFF35285A, 0xFFB0566A},  // 19:00, sunset
        {1230, 0xFF17163A, 0xFF3C2C5A},  // 20:30, dusk
        {1320, 0xFF0B1026, 0xFF1B2040},  // 22:00, night
        {1440, 0xFF0B1026, 0xFF1B2040},
    };

    private Sky() {}

    /**
     * The sky at {@code minute} after midnight, top to bottom: sky, horizon (mid-screen, behind
     * the clock and the first row), and a darker ground under the rows.
     */
    static int[] colors(int minute) {
        int m = ((minute % 1440) + 1440) % 1440;
        int i = 0;
        while (MOMENTS[i + 1][0] <= m) i++;
        int[] a = MOMENTS[i], b = MOMENTS[i + 1];
        float t = (m - a[0]) / (float) (b[0] - a[0]);
        int top = mix(a[1], b[1], t), horizon = mix(a[2], b[2], t);
        return new int[] {top, horizon, mix(top, 0xFF000000, 0.45f)};
    }

    /** {@code a} blended toward {@code b} by {@code t}; plain Java so the tests can run it. */
    static int mix(int a, int b, float t) {
        int out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int x = (a >> shift) & 0xFF, y = (b >> shift) & 0xFF;
            out |= Math.round(x + (y - x) * t) << shift;
        }
        return out;
    }
}
