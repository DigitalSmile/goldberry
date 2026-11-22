package dev.goldberry.vector.thorvg;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;
import java.util.Properties;

/// What the superbuild writes beside the library, `vector.properties`: the ABI,
/// the target and how ThorVG was built.
///
/// Read before the library is loaded, so that a directory holding a library of
/// another ABI is refused without mapping it.
///
/// @param abi       the ABI the library was built for
/// @param target    the target id, `linux-x64`
/// @param thorvg    the ThorVG tag, `v1.1.4`
/// @param commit    the commit that tag named when it was built
/// @param simd      whether ThorVG was compiled with SIMD
/// @param sanitized whether this is a sanitizer build, which is never shipped
public record BuildProperties(int abi, String target, String thorvg, String commit, boolean simd, boolean sanitized) {

    /// The file's name.
    public static final String FILE_NAME = "vector.properties";

    public BuildProperties {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(thorvg, "thorvg");
        Objects.requireNonNull(commit, "commit");
    }

    /// Reads the file's contents.
    ///
    /// @throws IOException              when it cannot be read
    /// @throws IllegalArgumentException when a key is missing or a value is not
    ///                                  what the superbuild writes
    public static BuildProperties parse(Reader reader) throws IOException {
        var properties = new Properties();
        properties.load(reader);
        var abiText = required(properties, "abi");
        int abi;
        try {
            abi = Integer.parseInt(abiText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(FILE_NAME + " says abi=" + abiText + ", which is not a number", e);
        }
        return new BuildProperties(
                abi,
                required(properties, "target"),
                required(properties, "thorvg"),
                required(properties, "thorvg.commit"),
                onOrOff(properties, "simd"),
                !required(properties, "sanitize").equals("none"));
    }

    private static String required(Properties properties, String key) {
        var value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(FILE_NAME + " has no " + key);
        }
        return value.trim();
    }

    private static boolean onOrOff(Properties properties, String key) {
        return switch (required(properties, key)) {
            case "on" -> true;
            case "off" -> false;
            case String other ->
                throw new IllegalArgumentException(
                        FILE_NAME + " says " + key + "=" + other + "; the superbuild writes on or off");
        };
    }
}
