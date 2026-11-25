package dev.goldberry.vector.robustness;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import dev.goldberry.vector.VectorNativesRequirement;
import dev.goldberry.vector.thorvg.VectorPlatform;

/// The sanitizer lane holds itself to what it claims: the sanitizer runtime is
/// in the process, and the library ThorVG runs from is the sanitized one, not
/// the release build a wrong path would quietly load. A lane that tests the
/// wrong library is a green tick over nothing.
///
/// Runs only in `:vector:sanitizerTest`, which says which sanitizers it built
/// with.
@DisplayName("The vector sanitizer lane")
@EnabledIfSystemProperty(named = "goldberry.vector.sanitizer", matches = ".+")
class SanitizerLaneTest {

    @Test
    @DisplayName("runs on the sanitized library, with the sanitizer's runtime in the process")
    void sanitized() throws IOException {
        VectorNativesRequirement.enforce();
        // A document drawn first, so the library is certainly mapped.
        assertTrue(Exercise.run(Corpus.documents().getFirst().bytes(), "lane") instanceof Exercise.Outcome.Drawn);
        var maps = Files.readString(Path.of("/proc/self/maps"));
        var sanitizers = System.getProperty("goldberry.vector.sanitizer");
        if (sanitizers.contains("address")) {
            assertTrue(maps.contains("/libasan.so"), "libasan is mapped");
        }
        if (sanitizers.contains("undefined")) {
            assertTrue(maps.contains("/libubsan.so"), "libubsan is mapped");
        }
        var library = Path.of(System.getProperty("goldberry.vector.libdir"))
                .resolve(System.mapLibraryName(VectorPlatform.LIBRARY_STEM))
                .toRealPath();
        assertTrue(maps.contains(library.toString()), library + " is the library mapped");
    }
}
