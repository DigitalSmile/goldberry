package dev.goldberry.vector.thorvg;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// `libgoldberry-vector` found the way an application finds it: in
/// `goldberry-vector`'s `natives-<target>` jar on the class path, with nothing
/// naming a directory.
///
/// Run only by `:vector:testNativesJar`, in a JVM of its own, because the loader
/// loads once per process.
@Tag("natives-jar")
@DisplayName("goldberry-vector's natives classifier, on the class path")
class VectorNativesJarTest {

    @Test
    @DisplayName("loads from the jar, unpacked into a temporary directory")
    void loadsFromTheJar() {
        assertNull(System.getProperty(VectorNatives.LIBRARY_DIRECTORY_PROPERTY));
        assertTrue(
                VectorNatives.unavailableReason().isEmpty(),
                () -> "not loaded from the jar: "
                        + VectorNatives.unavailableReason().orElse(""));

        var library = VectorNatives.get();
        var directory = library.directory();
        assertTrue(directory.getFileName().toString().startsWith("goldberry-vector"), directory.toString());
        assertTrue(Files.isRegularFile(directory.resolve(BuildProperties.FILE_NAME)));
        assertTrue(
                Files.isRegularFile(directory.resolve(VectorPlatform.current().fileName())));
        assertTrue(library.versions().startsWith("ThorVG"), library.versions());
    }

    @Test
    @DisplayName("carries the notice ThorVG's licence asks a binary to travel with")
    void carriesNotices() {
        var loader = VectorNativesJarTest.class.getClassLoader();
        assertNotNull(loader.getResource("META-INF/vector-NOTICE.txt"));
        assertNotNull(loader.getResource("META-INF/licenses/thorvg.txt"));
    }
}
