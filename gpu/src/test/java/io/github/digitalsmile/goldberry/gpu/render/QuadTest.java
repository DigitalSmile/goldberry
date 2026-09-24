package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Pixels from the top left, to SDL_GPU's normalised device coordinates (y up)
/// and texture coordinates (from the top left). No device.
@DisplayName("Quad")
class QuadTest {

    @Test
    @DisplayName("covers the whole target with the whole of clip space, and samples the whole texture")
    void wholeTarget() {
        var quad = Quad.of(0, 0, 640, 480, 640, 480);
        assertEquals(new Quad.Edges(-1, 1, 1, -1), quad.destination());
        assertEquals(Quad.WHOLE_TEXTURE, quad.source());
    }

    @Test
    @DisplayName("puts the top-left quarter at the top left, with y up")
    void quarters() {
        assertEquals(new Quad.Edges(-1, 1, 0, 0), Quad.of(0, 0, 50, 40, 100, 80).destination());
        assertEquals(
                new Quad.Edges(0, 0, 1, -1), Quad.of(50, 40, 50, 40, 100, 80).destination());
    }

    @Test
    @DisplayName("is its destination then its source, as the uniform block")
    void uniforms() {
        var quad = Quad.of(0, 0, 10, 10, 10, 10, new Quad.Edges(0.25f, 0.5f, 0.75f, 1f));
        assertArrayEquals(new float[] {-1, 1, 1, -1, 0.25f, 0.5f, 0.75f, 1f}, quad.uniforms());
        assertEquals(6, Quad.VERTICES);
    }

    @Test
    @DisplayName("refuses an empty quad or target")
    void refusesNonsense() {
        assertThrows(IllegalArgumentException.class, () -> Quad.of(0, 0, 0, 1, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> Quad.of(0, 0, 1, 1, 10, 0));
    }
}
