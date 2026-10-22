package tv.dumbtv.home;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;

import java.util.Date;
import java.util.Locale;

/** The clock and date as the home screen and the screen saver show them: one format, one place. */
final class Clock {
    private Clock() {}

    /** "14:05", or "2:05" with a small "PM" after it. */
    static CharSequence time(boolean h24, Date now) {
        if (h24) return DateFormat.format("HH:mm", now);
        SpannableString half = new SpannableString(" " + DateFormat.format("a", now));
        half.setSpan(new RelativeSizeSpan(0.36f), 0, half.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return android.text.TextUtils.concat(DateFormat.format("h:mm", now), half);
    }

    /** "Thursday, October 22" in the TV's language. */
    static CharSequence date(Date now) {
        return DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now);
    }
}
