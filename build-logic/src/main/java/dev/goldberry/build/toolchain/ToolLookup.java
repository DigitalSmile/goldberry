package dev.goldberry.build.toolchain;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.gradle.api.provider.ProviderFactory;

import dev.goldberry.build.ToolResolver;

/**
 * How a build script finds a native build tool: {@code -Pgoldberry.<name>} (or
 * {@code -D}) when one was given, and otherwise {@link ToolResolver}'s search of
 * the client's {@code PATH} and the conventional install directories.
 *
 * <p>An override is taken at its word. One that quietly fell back to something
 * found on the {@code PATH} would be an override that does not override, so a
 * path that names nothing resolves to nothing and the toolchain check reports it
 * as the override's fault.
 *
 * <p>Every tool comes back as an <em>absolute</em> path, for the reason
 * {@link ToolResolver} gives: a Gradle daemon started by an IDE carries a stale
 * native {@code PATH}, and {@code Exec} cannot be trusted to find a bare name.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/building.html#the-native-superbuild">The
 * native superbuild</a>.
 *
 * @param path      the client's {@code PATH}, as Gradle reports it
 * @param osName    the {@code os.name} system property
 * @param userHome  the {@code user.home} system property
 * @param overrides the value of {@code goldberry.<name>} for a tool name, if set
 */
public record ToolLookup(String path, String osName, String userHome, Function<String, Optional<String>> overrides) {

    /** The prefix of the property that names a tool directly: {@code goldberry.cmake}. */
    public static final String PROPERTY_PREFIX = "goldberry.";

    public ToolLookup {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(userHome, "userHome");
        Objects.requireNonNull(overrides, "overrides");
    }

    /**
     * The lookup a build script uses: the client's {@code PATH}, this JVM's
     * {@code os.name} and {@code user.home}, and {@code -Pgoldberry.<name>} or
     * {@code -Dgoldberry.<name>} as the override.
     *
     * @param providers the project's provider factory
     * @return a lookup over what the build can see
     */
    public static ToolLookup from(ProviderFactory providers) {
        var path = providers.environmentVariable("PATH")
                // Windows spells it differently.
                .orElse(providers.environmentVariable("Path"))
                .getOrElse("");
        return new ToolLookup(
                path,
                System.getProperty("os.name", ""),
                System.getProperty("user.home", ""),
                name -> Optional.ofNullable(providers.gradleProperty(PROPERTY_PREFIX + name)
                        .orElse(providers.systemProperty(PROPERTY_PREFIX + name))
                        .getOrNull()));
    }

    /**
     * What was asked for on the command line, if anything.
     *
     * @param name a tool's bare name, {@code cmake}
     * @return the override's value
     */
    public Optional<String> override(String name) {
        return overrides.apply(name).filter(value -> !value.isBlank());
    }

    /**
     * The tool, as an absolute path.
     *
     * @param name a tool's bare name, {@code cmake}
     * @return where it is, or empty when the search finds nothing
     */
    public Optional<Path> find(String name) {
        return ToolResolver.resolve(override(name).orElse(name), path, osName, userHome);
    }

    /**
     * The tool as a {@link File}, for a Groovy script that wants {@code null}
     * rather than an empty {@link Optional}.
     *
     * @param name a tool's bare name
     * @return the file, or {@code null} when it was not found
     */
    public File fileOrNull(String name) {
        return find(name).map(Path::toFile).orElse(null);
    }
}
