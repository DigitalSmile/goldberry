package dev.goldberry.vector.thorvg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BuildProperties")
class BuildPropertiesTest {

    /// What package.cmake writes, line for line.
    static final String WRITTEN = """
            # libgoldberry-vector for linux-x64, written by vector/src/main/cmake/package.cmake.
            abi=1
            target=linux-x64
            library=libgoldberry-vector.so
            thorvg=v1.1.4
            thorvg.commit=47658a80842f3b3182aded036190edf459beabe7
            simd=off
            sanitize=none
            """;

    @Test
    @DisplayName("reads what the superbuild writes")
    void readsTheSuperbuild() throws IOException {
        var build = BuildProperties.parse(new StringReader(WRITTEN));
        assertAll(
                () -> assertEquals(1, build.abi()),
                () -> assertEquals("linux-x64", build.target()),
                () -> assertEquals("v1.1.4", build.thorvg()),
                () -> assertEquals("47658a80842f3b3182aded036190edf459beabe7", build.commit()),
                () -> assertFalse(build.simd()),
                () -> assertFalse(build.sanitized()));
    }

    @Test
    @DisplayName("knows a sanitizer build and a SIMD build")
    void flavours() throws IOException {
        var build = BuildProperties.parse(
                new StringReader(WRITTEN.replace("simd=off", "simd=on").replace("sanitize=none", "sanitize=address")));
        assertTrue(build.simd());
        assertTrue(build.sanitized());
    }

    @ParameterizedTest(name = "without {0}")
    @ValueSource(strings = {"abi", "target", "thorvg", "thorvg.commit", "simd", "sanitize"})
    @DisplayName("refuses a file missing a key, naming it")
    void missingKey(String key) {
        var without = WRITTEN.lines()
                .filter(line -> !line.startsWith(key + "="))
                .reduce("", (all, line) -> all + line + "\n");
        var e = assertThrows(IllegalArgumentException.class, () -> BuildProperties.parse(new StringReader(without)));
        assertTrue(e.getMessage().contains("has no " + key), e.getMessage());
    }

    @Test
    @DisplayName("refuses an ABI that is not a number, and a SIMD that is not on or off")
    void wrongValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildProperties.parse(new StringReader(WRITTEN.replace("abi=1", "abi=one"))));
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> BuildProperties.parse(new StringReader(WRITTEN.replace("simd=off", "simd=avx2"))));
        assertTrue(e.getMessage().contains("simd=avx2"), e.getMessage());
    }
}
