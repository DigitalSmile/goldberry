package io.github.digitalsmile.goldberry.build.nativeimage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A GraalVM release, as its {@code release} file names it --
 * {@code GRAALVM_VERSION="25.3.4.1"} -- and the one line of them CI builds native
 * images with (ADR-0337).
 *
 * <p>Since 25.1 GraalVM versions itself apart from the JDK it is built on:
 * GraalVM 25.3.4.1 is JDK 25.0.4.1, and so was not-quite-25.2.4 before it. The Java
 * version therefore says little about which native-image compiled a binary, and a
 * local build on one GraalVM line and a CI build on another can disagree about
 * what an image needs. {@link #CI_LINE} is the pin; {@code showcase.yml} is held to
 * it by test, and {@code :example:nativeImage} warns when a local GraalVM is not
 * on it.
 *
 * @param components the dotted numbers, at least a major and a minor
 */
public record GraalVmRelease(List<Integer> components) {

    /** The GraalVM line {@code showcase.yml} installs, as {@code setup-graalvm}'s {@code version}. */
    public static final String CI_LINE = "25.3";

    private static final Pattern VERSION = Pattern.compile("\\d+(?:\\.\\d+)+");

    private static final Pattern RELEASE_FILE_ENTRY =
            Pattern.compile("(?m)^GRAALVM_VERSION=\"?(\\d+(?:\\.\\d+)+)\"?\\s*$");

    public GraalVmRelease {
        components = List.copyOf(components);
        if (components.size() < 2) {
            throw new IllegalArgumentException("a GraalVM version has at least a major and a minor: " + components);
        }
    }

    /**
     * Reads {@code 25.3.4.1}.
     *
     * @throws IllegalArgumentException for anything that is not dotted numbers
     */
    public static GraalVmRelease parse(String text) {
        var stripped = text.strip();
        if (!VERSION.matcher(stripped).matches()) {
            throw new IllegalArgumentException("'" + text + "' is not a GraalVM version such as 25.3.4.1");
        }
        return new GraalVmRelease(Arrays.stream(stripped.split("\\.")).map(Integer::valueOf).toList());
    }

    /**
     * The release a GraalVM home's {@code release} file declares, or empty for a
     * JDK that is not a GraalVM -- or a GraalVM older than the entry.
     */
    public static Optional<GraalVmRelease> fromReleaseFile(String releaseFile) {
        return RELEASE_FILE_ENTRY.matcher(releaseFile).results()
                .findFirst()
                .map(match -> parse(match.group(1)));
    }

    /** {@code 25.3} of {@code 25.3.4.1}: what {@code setup-graalvm} is pinned to. */
    public String line() {
        return components.get(0) + "." + components.get(1);
    }

    /** Whether this is the line CI builds with. */
    public boolean isCiLine() {
        return line().equals(CI_LINE);
    }

    /**
     * What to tell someone building an image on this release, if anything: nothing
     * on the CI line, a warning naming both lines off it.
     */
    public Optional<String> mismatchWarning() {
        return isCiLine()
                ? Optional.empty()
                : Optional.of("GraalVM " + this + " is not on the " + CI_LINE + " line CI builds native images with"
                        + " (showcase.yml, ADR-0337). An image that works here may not in CI, and the reverse.");
    }

    @Override
    public String toString() {
        return String.join(".", components.stream().map(String::valueOf).toList());
    }
}
