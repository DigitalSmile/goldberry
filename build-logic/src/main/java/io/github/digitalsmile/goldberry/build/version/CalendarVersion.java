package io.github.digitalsmile.goldberry.build.version;

import java.time.Year;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * A Goldberry version: the year, which release of that year, and an optional
 * patch -- {@code 2026.1}, {@code 2026.2}, {@code 2026.2.1}. The same shape
 * JetBrains ships IntelliJ IDEA under (ADR-0333).
 *
 * <h2>One spelling per version</h2>
 *
 * {@code 2026.1} and {@code 2026.1.0} would be the same release under two names,
 * and a tag, a POM and a {@code gradle.properties} line that disagree about which
 * one they mean is exactly the mismatch the release check exists to catch. So a
 * zero patch is not written, a release counts from one, and neither part takes a
 * leading zero: {@code 2026.1.0} and {@code 2026.01} are refused rather than
 * normalised.
 *
 * @param year    the calendar year the release line belongs to, four digits
 * @param release which release of that year, from 1
 * @param patch   the patch on that release, 0 for the release itself
 */
public record CalendarVersion(int year, int release, int patch) implements Comparable<CalendarVersion> {

    /**
     * The first year a Goldberry version can carry. Anything earlier is a typo
     * rather than a version: the project did not exist.
     */
    public static final int FIRST_YEAR = 2026;

    private static final Pattern SHAPE = Pattern.compile("(\\d{4})\\.([1-9]\\d*)(?:\\.([1-9]\\d*))?");

    private static final Comparator<CalendarVersion> ORDER = Comparator
            .comparingInt(CalendarVersion::year)
            .thenComparingInt(CalendarVersion::release)
            .thenComparingInt(CalendarVersion::patch);

    public CalendarVersion {
        if (year < FIRST_YEAR || year > 9999) {
            throw new IllegalArgumentException(
                    "year " + year + " is outside " + FIRST_YEAR + "..9999");
        }
        if (release < 1) {
            throw new IllegalArgumentException("release " + release + " must be at least 1");
        }
        if (patch < 0) {
            throw new IllegalArgumentException("patch " + patch + " must not be negative");
        }
    }

    /**
     * Reads {@code YYYY.N} or {@code YYYY.N.P}.
     *
     * @throws IllegalArgumentException for anything else, naming the expected shape
     */
    public static CalendarVersion parse(String text) {
        var matcher = SHAPE.matcher(text.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "'" + text + "' is not a Goldberry version: expected YEAR.RELEASE or "
                            + "YEAR.RELEASE.PATCH, such as 2026.1 or 2026.1.1 (ADR-0333)");
        }
        var patch = matcher.group(3);
        return new CalendarVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                patch == null ? 0 : Integer.parseInt(patch));
    }

    /**
     * The release that follows this line. A new year starts counting again, which
     * is the whole point of putting the year in the version: {@code 2026.3} is
     * followed by {@code 2027.1} once 2027 has begun, and by {@code 2026.4} if it
     * has not.
     */
    public CalendarVersion nextRelease(Year now) {
        return now.getValue() > year
                ? new CalendarVersion(now.getValue(), 1, 0)
                : new CalendarVersion(year, release + 1, 0);
    }

    /** The next patch on this release line. */
    public CalendarVersion nextPatch() {
        return new CalendarVersion(year, release, patch + 1);
    }

    /** Whether this is a patch rather than the release itself. */
    public boolean isPatch() {
        return patch > 0;
    }

    @Override
    public int compareTo(CalendarVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return isPatch() ? year + "." + release + "." + patch : year + "." + release;
    }
}
