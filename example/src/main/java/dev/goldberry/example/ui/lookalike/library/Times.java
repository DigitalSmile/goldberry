package dev.goldberry.example.ui.lookalike.library;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/// How a music player writes time and counts: `3:41` for a song, `34 min 12 sec`
/// for a playlist, `182,331,004` for a play count.
///
/// The root locale, for the reason every label in the toolkit uses it: a golden
/// image of a count formatted in the machine's language passes in one country.
public final class Times {

    private static final Pattern CLOCK = Pattern.compile("(\\d+):([0-5]\\d)");

    private Times() {}

    /// `m:ss` as a duration.
    ///
    /// @throws IllegalArgumentException for anything that is not `m:ss`
    public static Duration parse(String clock) {
        var matched = CLOCK.matcher(clock);
        if (!matched.matches()) {
            throw new IllegalArgumentException("a track's length is written m:ss, not \"" + clock + "\"");
        }
        return Duration.ofMinutes(Long.parseLong(matched.group(1))).plusSeconds(Long.parseLong(matched.group(2)));
    }

    /// A position or a length as a player's clock: `0:07`, `3:41`, `1:02:09`.
    ///
    /// Whole seconds, rounded down, so a position never reads past the length it
    /// is a position in.
    public static String clock(Duration time) {
        var seconds = Math.max(0, time.toSeconds());
        var hours = seconds / 3600;
        var minutes = seconds / 60 % 60;
        var rest = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }

    /// A playlist's length the way its header says it: `34 min 12 sec`, or
    /// `1 hr 5 min` once it runs past an hour.
    public static String spoken(Duration time) {
        return time.toHours() > 0
                ? String.format(Locale.ROOT, "%d hr %d min", time.toHours(), time.toMinutesPart())
                : String.format(Locale.ROOT, "%d min %d sec", time.toMinutes(), time.toSecondsPart());
    }

    /// A count with its thousands grouped.
    public static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
