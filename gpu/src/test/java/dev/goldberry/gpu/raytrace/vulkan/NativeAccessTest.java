package dev.goldberry.gpu.raytrace.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `:gpu` drives Vulkan through `:natives`' bindings and takes no restricted
/// step of its own: an application grants native access to
/// `dev.goldberry.natives` alone, and a restricted call here would warn, or
/// fail under `--illegal-native-access=deny`, on the module path, where the
/// tests, on the class path, never would.
@DisplayName("the GPU module's native access")
class NativeAccessTest {

    /// The restricted methods of the foreign API a module could call.
    private static final Pattern RESTRICTED = Pattern.compile(
            "\\.reinterpret\\(|downcallHandle\\(|upcallStub\\(|libraryLookup\\(|@SuppressWarnings\\(\"restricted\"\\)");

    @Test
    @DisplayName("calls no restricted method anywhere in its sources")
    void noneRestricted() throws IOException {
        List<String> found;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            found = files.filter(file -> file.toString().endsWith(".java"))
                    .flatMap(file -> lines(file).stream()
                            .filter(line -> RESTRICTED.matcher(line).find())
                            .map(line -> file + ": " + line.trim()))
                    .toList();
        }
        assertEquals(List.of(), found, "restricted steps belong in :natives, which has native access");
    }

    @Test
    @DisplayName("finds its own sources, so the check above checks something")
    void walksTheSources() throws IOException {
        try (var files = Files.walk(Path.of("src/main/java/dev/goldberry/gpu/raytrace/vulkan"))) {
            assertTrue(files.filter(file -> file.toString().endsWith(".java")).count() > 5);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
