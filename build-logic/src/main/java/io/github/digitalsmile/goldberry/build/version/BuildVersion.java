package io.github.digitalsmile.goldberry.build.version;

/**
 * The version a build stamps on what it produces, decided from three inputs: the
 * release line {@code gradle.properties} declares, whether this is a release, and
 * the tag a release was pushed as (ADR-0333).
 *
 * <p>{@code gradle.properties} names the release being <em>worked towards</em> --
 * {@code goldberryVersion=2026.1} -- and never a {@code -SNAPSHOT}. Every build is
 * a {@link Snapshot} of that line unless {@code -Pgoldberry.release=true} says
 * otherwise, so nobody has to remember to add a suffix and nobody has to remember
 * to remove one. The release workflow is the only thing that sets the flag, and it
 * hands over the tag it was triggered by: a {@code v2026.2} tag on a commit that
 * still declares {@code 2026.1} fails here, before a single jar is built, rather
 * than publishing {@code 2026.1} to Maven Central for good.
 */
public sealed interface BuildVersion permits BuildVersion.Snapshot, BuildVersion.Release {

    /** What a snapshot appends to its release line. */
    String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    /** What a release tag puts in front of its version. */
    String TAG_PREFIX = "v";

    /** The release line this build belongs to. */
    CalendarVersion line();

    /** A build of the line on its way to release. */
    record Snapshot(CalendarVersion line) implements BuildVersion {
        @Override
        public String toString() {
            return line + SNAPSHOT_SUFFIX;
        }
    }

    /** The line itself, published once and never again. */
    record Release(CalendarVersion line) implements BuildVersion {
        @Override
        public String toString() {
            return line.toString();
        }
    }

    /** The tag this version is, or would be, released under. */
    default String tag() {
        return TAG_PREFIX + line();
    }

    /** Whether this build may be overwritten by a later one of the same name. */
    default boolean isSnapshot() {
        return switch (this) {
            case Snapshot _ -> true;
            case Release _ -> false;
        };
    }

    /**
     * Decides the version.
     *
     * @param declared the {@code goldberryVersion} property, a {@link CalendarVersion}
     * @param release  whether {@code -Pgoldberry.release=true} was passed
     * @param tag      the tag a release was triggered by, or {@code null} outside one
     * @throws IllegalArgumentException when the inputs disagree, saying how
     */
    static BuildVersion resolve(String declared, boolean release, String tag) {
        if (declared.strip().endsWith(SNAPSHOT_SUFFIX)) {
            throw new IllegalArgumentException(
                    "goldberryVersion=" + declared + " carries its own " + SNAPSHOT_SUFFIX
                            + "; declare the release line alone and let the build add it (ADR-0333)");
        }
        var line = CalendarVersion.parse(declared);
        var hasTag = tag != null && !tag.isBlank();

        if (!release) {
            if (hasTag) {
                throw new IllegalArgumentException(
                        "a release tag (" + tag + ") was given without -Pgoldberry.release=true");
            }
            return new Snapshot(line);
        }
        if (!hasTag) {
            throw new IllegalArgumentException(
                    "-Pgoldberry.release=true needs -Pgoldberry.releaseTag=" + TAG_PREFIX + line
                            + ": a release is published from a tag, never from a branch");
        }
        var stripped = tag.strip();
        if (!stripped.startsWith(TAG_PREFIX)) {
            throw new IllegalArgumentException(
                    "release tag '" + tag + "' must start with '" + TAG_PREFIX + "', as in " + TAG_PREFIX + line);
        }
        var tagged = CalendarVersion.parse(stripped.substring(TAG_PREFIX.length()));
        if (!tagged.equals(line)) {
            throw new IllegalArgumentException(
                    "release tag " + stripped + " does not match goldberryVersion=" + line
                            + " in gradle.properties -- tag the commit that declares it, or bump the property first");
        }
        return new Release(line);
    }
}
