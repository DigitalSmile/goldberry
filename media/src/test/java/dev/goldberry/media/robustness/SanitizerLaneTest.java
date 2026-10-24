package dev.goldberry.media.robustness;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.ffm.MediaPlatform;

/// The sanitizer lane holds itself to what it claims: the sanitizer runtime is
/// in the process, and the library the decoders run on is the sanitized one,
/// not the release build a wrong path would quietly load. A lane that tests the
/// wrong library is a green tick over nothing.
///
/// Runs only in `:media:sanitizerTest`, which says which sanitizers it built
/// with.
@DisplayName("The sanitizer lane")
@EnabledIfSystemProperty(named = "goldberry.media.sanitizer", matches = ".+")
class SanitizerLaneTest {

    @Test
    @DisplayName("runs on the sanitized library, with the sanitizer's runtime in the process")
    void sanitized() throws IOException {
        MediaNativesRequirement.enforce();
        var maps = Files.readString(Path.of("/proc/self/maps"));
        var sanitizers = System.getProperty("goldberry.media.sanitizer");
        if (sanitizers.contains("address")) {
            assertTrue(maps.contains("/libasan.so"), "libasan is mapped");
        }
        if (sanitizers.contains("undefined")) {
            assertTrue(maps.contains("/libubsan.so"), "libubsan is mapped");
        }
        var library = Path.of(System.getProperty("goldberry.media.libdir"))
                .resolve(System.mapLibraryName(MediaPlatform.LIBRARY_STEM))
                .toRealPath();
        assertTrue(maps.contains(library.toString()), library + " is the library mapped");
    }
}
