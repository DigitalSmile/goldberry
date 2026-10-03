package dev.goldberry.build.testing;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Function;

/**
 * The properties a forked test JVM is handed from the {@code gradlew} command
 * line, as {@code -D} or {@code -P}.
 *
 * <p>A property set on the command line reaches the Gradle daemon and stops
 * there unless the build hands it on. Each module used to hand on a list of its
 * own, the lists disagreed, and the symptom every time was a flag that looked as
 * if it did nothing: {@code -Dgoldberry.golden.update=true} rewrote no golden in
 * the module whose list had left it out. There is one list now, and every test
 * task and every GPU lane receives all of it. A property a module never reads
 * costs nothing there.
 *
 * <p>{@code goldberry.native.library} is not on it: which library a test loads is
 * decided by {@link NativeTests}, because "none given" means the one this build
 * made.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/testing.html#with-and-without-the-library">With
 * and without the library</a>.
 */
public final class ForwardedProperties {

    /** What every test JVM is handed when the command line sets it. */
    public static final List<String> TO_TESTS = List.of(
            // The goldens: rewrite them, and the scale sweep's two switches.
            "goldberry.golden.update",
            "goldberry.golden.scales",
            "goldberry.golden.scales.report",
            // A job whose purpose is a library turns its skip into a failure.
            "goldberry.native.required",
            "goldberry.webview.required",
            "goldberry.media.required",
            "goldberry.platform.required",
            "goldberry.gpu.required",
            // The GPU lane's video driver, `offscreen` for lavapipe.
            "goldberry.gpu.videoDriver",
            // Read into a static final when the renderer initialises.
            "goldberry.trace.frames",
            // How much room a test's clock bounds get on a loaded machine.
            "goldberry.timing.slack");

    private ForwardedProperties() {
    }

    /**
     * The values to hand on, a {@code -D} winning over a {@code -P} of the same name.
     *
     * @param names  the properties to look for
     * @param system a system property's value by name, if set
     * @param gradle a Gradle property's value by name, if set
     * @return the names that were set, with their values, in {@code names}' order
     */
    public static SequencedMap<String, String> resolve(
            Collection<String> names,
            Function<String, Optional<String>> system,
            Function<String, Optional<String>> gradle) {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(gradle, "gradle");
        var values = new LinkedHashMap<String, String>();
        for (var name : names) {
            system.apply(name).or(() -> gradle.apply(name)).ifPresent(value -> values.put(name, value));
        }
        return values;
    }
}
