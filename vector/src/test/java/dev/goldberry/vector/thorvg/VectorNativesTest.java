package dev.goldberry.vector.thorvg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.vector.VectorNativesRequirement;

@DisplayName("VectorNatives")
class VectorNativesTest {

    private static final VectorPlatform PLATFORM = VectorPlatform.current();

    private static String properties(int abi, String target) {
        return BuildPropertiesTest.WRITTEN
                .replace("abi=1", "abi=" + abi)
                .replace("target=linux-x64", "target=" + target);
    }

    private static String reason(VectorNatives.State state) {
        return assertInstanceOf(VectorNatives.State.Unavailable.class, state).reason();
    }

    @Nested
    @DisplayName("the superbuild's library")
    class Built {

        @Test
        @DisplayName("loads, at the ABI these bindings are written against, and names its build")
        void loads() {
            VectorNativesRequirement.enforce();
            var library = VectorNatives.get();
            assertEquals(VectorLibrary.ABI, library.vector().abi().call());
            assertEquals(VectorLibrary.ABI, library.build().abi());
            assertEquals(PLATFORM.target(), library.build().target());
            assertEquals("v1.1.4", library.build().thorvg());
            assertTrue(library.versions().startsWith("ThorVG 1.1.4"), library.versions());
            assertEquals(library.build().simd(), library.versions().endsWith("simd on"), library.versions());
        }
    }

    @Nested
    @DisplayName("a directory that is wrong")
    class Wrong {

        @Test
        @DisplayName("with nothing in it is a reason, not an exception")
        void empty(@TempDir Path directory) {
            assertTrue(reason(VectorNatives.load(directory, PLATFORM)).contains("no vector.properties"));
        }

        @Test
        @DisplayName("for another ABI is refused before the library is mapped")
        void otherAbi(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve(BuildProperties.FILE_NAME), properties(2, PLATFORM.target()));
            assertTrue(reason(VectorNatives.load(directory, PLATFORM)).contains("describes ABI 2"));
        }

        @Test
        @DisplayName("for another target is refused")
        void otherTarget(@TempDir Path directory) throws IOException {
            var other = PLATFORM.target().equals("linux-x64") ? "macos-aarch64" : "linux-x64";
            Files.writeString(directory.resolve(BuildProperties.FILE_NAME), properties(1, other));
            assertTrue(reason(VectorNatives.load(directory, PLATFORM)).contains("for " + other));
        }

        @Test
        @DisplayName("with the properties and no library says which file is missing")
        void noLibrary(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve(BuildProperties.FILE_NAME), properties(1, PLATFORM.target()));
            assertTrue(reason(VectorNatives.load(directory, PLATFORM)).contains("no " + PLATFORM.fileName()));
        }

        @Test
        @DisplayName("with a file that is not a library says it would not load")
        void notALibrary(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve(BuildProperties.FILE_NAME), properties(1, PLATFORM.target()));
            Files.writeString(directory.resolve(PLATFORM.fileName()), "not a shared object");
            assertTrue(reason(VectorNatives.load(directory, PLATFORM)).contains("would not load"));
        }
    }
}
