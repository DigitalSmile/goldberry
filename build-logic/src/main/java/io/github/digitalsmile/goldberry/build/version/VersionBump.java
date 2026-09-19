package io.github.digitalsmile.goldberry.build.version;

import java.time.Year;
import java.util.regex.Pattern;

/**
 * The rewrite of {@code gradle.properties} that follows a tag: the release line
 * master moves to once the one it declares is out (ADR-0333, ADR-0426).
 *
 * <h2>Why this is a value and not a line of shell</h2>
 *
 * <p>The step that does this by hand is the one ADR-0333 listed as a consequence
 * and nothing enforced: <em>"someone has to bump {@code gradle.properties} after
 * every release, or master keeps publishing snapshots of a version that is
 * already out"</em> -- which Maven orders <em>below</em> the release, so a
 * consumer who followed the snapshot silently goes backwards. Forgetting is
 * invisible until somebody resolves the wrong jar.
 *
 * <p>So the arithmetic lives here, where a test can state it, rather than in a
 * workflow's shell. Two things about it are decisions rather than mechanics:
 *
 * <ul>
 *   <li><b>A patch line bumps its patch, not its release.</b> {@code 2026.1.1}
 *       lives on a {@code release/2026.1} branch, and the next thing that branch
 *       can cut is {@code 2026.1.2}. Bumping it to {@code 2026.2} would point a
 *       maintenance branch at the next feature release, which is the one number
 *       it must never claim.
 *   <li><b>Only the value is rewritten.</b> The four comment lines above the
 *       property say why it is never a snapshot, and a {@link java.util.Properties}
 *       round trip drops every comment in the file. So this is a single-line
 *       substitution and the rest of the text survives byte for byte.
 * </ul>
 *
 * @param from       the line {@code gradle.properties} declares now
 * @param to         the line it should declare after the release
 * @param properties the whole file, rewritten
 */
public record VersionBump(CalendarVersion from, CalendarVersion to, String properties) {

    /** The property the release line is declared in. */
    public static final String PROPERTY = "goldberryVersion";

    /**
     * The declaration, anchored to the start of its own line -- so a commented-out
     * copy, or a longer property whose name ends in this one, cannot match.
     */
    private static final Pattern DECLARATION = Pattern.compile("(?m)^" + PROPERTY + "=(.*)$");

    /**
     * Reads {@code properties}, works out what follows the line it declares, and
     * returns both together with the rewritten text.
     *
     * @param properties the whole {@code gradle.properties}, as text
     * @param now        the year the bump happens in -- January is what makes
     *                   {@code 2026.3} become {@code 2027.1} rather than
     *                   {@code 2026.4}
     * @throws IllegalArgumentException if the file declares no version, declares
     *                                 one twice, or declares something that is not
     *                                 a Goldberry version
     */
    public static VersionBump of(String properties, Year now) {
        var matcher = DECLARATION.matcher(properties);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "gradle.properties declares no " + PROPERTY + "=, so there is nothing to bump");
        }
        var declared = matcher.group(1);
        var start = matcher.start();
        var end = matcher.end();
        if (matcher.find()) {
            // Two declarations mean the build reads one and this rewrites the
            // other, which is worse than not bumping at all.
            throw new IllegalArgumentException(
                    "gradle.properties declares " + PROPERTY + "= twice; which one the build reads is"
                            + " not this task's to guess");
        }
        var from = CalendarVersion.parse(declared);
        var to = from.isPatch() ? from.nextPatch() : from.nextRelease(now);
        var rewritten = properties.substring(0, start) + PROPERTY + "=" + to + properties.substring(end);
        return new VersionBump(from, to, rewritten);
    }

    /**
     * The branch the bump is opened on, named for the version it moves <em>to</em>
     * -- so re-running the job for the same tag reuses one branch rather than
     * opening a second pull request.
     */
    public String branch() {
        return "bump/" + to;
    }

    /** {@code 2026.1 -> 2026.2}, for a commit message and a run's summary. */
    @Override
    public String toString() {
        return from + " -> " + to;
    }
}
