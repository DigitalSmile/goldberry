package dev.goldberry.vector.thorvg;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.vector.VectorNativesRequirement;

/// ThorVG through the bare bindings: a document parsed, sized and drawn into
/// memory Java owns, as the module's renderer will do it.
@DisplayName("ThorVG, through the bindings")
class ThorvgDrawTest {

    /// `TVG_COLORSPACE_ARGB8888`: premultiplied, `0xAARRGGBB` per pixel.
    private static final int ARGB8888 = 1;

    /// `TVG_ENGINE_OPTION_DEFAULT`.
    private static final int DEFAULT = 1;

    private static final String HALF_RED = """
            <svg xmlns="http://www.w3.org/2000/svg" width="8" height="4" viewBox="0 0 8 4">
              <rect x="0" y="0" width="8" height="2" fill="#ff0000"/>
              <rect x="0" y="2" width="8" height="2" fill="#0000ff" fill-opacity="0.5"/>
            </svg>
            """;

    private static VectorLibrary library;

    @BeforeAll
    static void engine() {
        VectorNativesRequirement.enforce();
        library = VectorNatives.get();
        assertEquals(0, library.engine().init().call(0));
    }

    @AfterAll
    static void stop() {
        if (library != null) {
            assertEquals(0, library.engine().term().call());
        }
    }

    @Test
    @DisplayName("reads a document's size and draws it, premultiplied, at twice that size")
    void draws() {
        var picture = library.picture();
        var canvas = library.canvas();
        try (var arena = Arena.ofConfined()) {
            var document = arena.allocateFrom(HALF_RED, StandardCharsets.UTF_8);
            var parsed = picture.newPicture().call();
            assertNotEquals(MemorySegment.NULL, parsed);
            assertEquals(
                    0,
                    picture.loadData()
                            .call(
                                    parsed,
                                    document,
                                    (int) document.byteSize() - 1,
                                    arena.allocateFrom("svg"),
                                    MemorySegment.NULL,
                                    true));

            var width = arena.allocate(JAVA_FLOAT);
            var height = arena.allocate(JAVA_FLOAT);
            assertEquals(0, picture.getSize().call(parsed, width, height));
            assertEquals(8f, width.get(JAVA_FLOAT, 0));
            assertEquals(4f, height.get(JAVA_FLOAT, 0));

            var copy = picture.duplicate().call(parsed);
            assertEquals(0, picture.setSize().call(copy, 16, 8));
            var pixels = arena.allocate(JAVA_INT, 16 * 8);
            var target = canvas.create().call(DEFAULT);
            assertEquals(0, canvas.setTarget().call(target, pixels, 16, 16, 8, ARGB8888));
            assertEquals(0, canvas.add().call(target, copy));
            assertEquals(0, canvas.draw().call(target, true));
            assertEquals(0, canvas.sync().call(target));
            assertEquals(0, canvas.destroy().call(target));
            assertEquals(0, picture.release().call(parsed));

            // Half-transparent blue, premultiplied: alpha and blue both about 128.
            var blue = pixels.getAtIndex(JAVA_INT, 4 * 16);
            assertAll(
                    () -> assertEquals(0xFFFF0000, pixels.getAtIndex(JAVA_INT, 0)),
                    () -> assertEquals(0xFFFF0000, pixels.getAtIndex(JAVA_INT, 3 * 16 + 15)),
                    () -> assertEquals(128, blue >>> 24, 1, Integer.toHexString(blue)),
                    () -> assertEquals(128, blue & 0xFF, 1, Integer.toHexString(blue)),
                    () -> assertEquals(0, blue & 0x00FFFF00, Integer.toHexString(blue)));
        }
    }

    @Test
    @DisplayName("refuses bytes that are not SVG, with a result rather than a crash")
    void refusesGarbage() {
        var picture = library.picture();
        try (var arena = Arena.ofConfined()) {
            var garbage = arena.allocateFrom("<svg <<<< not a document", StandardCharsets.UTF_8);
            var parsed = picture.newPicture().call();
            var result = picture.loadData()
                    .call(
                            parsed,
                            garbage,
                            (int) garbage.byteSize() - 1,
                            arena.allocateFrom("svg"),
                            MemorySegment.NULL,
                            true);
            assertNotEquals(0, result);
            assertEquals(0, picture.release().call(parsed));
        }
    }
}
