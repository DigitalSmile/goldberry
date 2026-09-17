package io.github.digitalsmile.goldberry.build.nativeimage;

/**
 * Which SDL video driver {@code :example:nativeImageMetadata} runs the showcase
 * under while GraalVM's agent watches it (ADR-0337, ADR-0338).
 *
 * <p>{@link #DEFAULT} is {@code dummy}: headless, so a trace can be recorded on
 * a build machine with no display, which is how the checked-in Linux trace was
 * made. On a macOS runner that default is wrong twice over. SDL's macOS tray is
 * Cocoa's status bar, and under {@code dummy} no Cocoa application was ever
 * started, so the first status-bar call aborts the process from inside
 * CoreGraphics -- the trace died with no Java frame in sight. The toolkit now
 * declines to create a tray in that state, which keeps the process alive and
 * leaves the trace with no tray calls in it. A macOS runner has a window server,
 * so {@code showcase.yml} asks for {@link #MACOS_CI} there and the trace records
 * what a real run does, tray included.
 */
public final class TraceVideoDriver {

    /** The Gradle property that chooses the driver: {@code -Pgoldberry.trace.videoDriver=cocoa}. */
    public static final String PROPERTY = "goldberry.trace.videoDriver";

    /** Headless. What the trace runs under unless told otherwise. */
    public static final String DEFAULT = "dummy";

    /** What the macOS leg of {@code showcase.yml} asks for. */
    public static final String MACOS_CI = "cocoa";

    private TraceVideoDriver() {
    }

    /**
     * The driver to run under.
     *
     * @param requested the property's value, or {@code null} when it was not set
     * @return {@code requested} when it says something, else {@link #DEFAULT}
     */
    public static String choose(String requested) {
        return requested == null || requested.isBlank() ? DEFAULT : requested.strip();
    }
}
