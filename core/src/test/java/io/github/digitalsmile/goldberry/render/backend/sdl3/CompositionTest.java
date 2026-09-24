package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// When a window presents through the GPU, from `goldberry.gpu` and
/// `goldberry.gpu.composite` (ADR-0479, ADR-0480).
@DisplayName("the composition policy")
class CompositionTest {

    @Test
    @DisplayName("composites every window by default, the GPU first and the CPU where it cannot (ADR-0480)")
    void defaultsToAlways() {
        assertEquals(Composition.ALWAYS, Composition.of(null, null));
        assertEquals(Composition.ALWAYS, Composition.of("auto", " ALWAYS "));
    }

    @Test
    @DisplayName("composites only for GPU layers when told auto, and none when told never")
    void autoAndNever() {
        assertEquals(Composition.AUTO, Composition.of(null, "auto"));
        assertEquals(Composition.NEVER, Composition.of(null, "never"));
    }

    @Test
    @DisplayName("turns the GPU off whatever the composite property says")
    void gpuOffWins() {
        assertEquals(Composition.NEVER, Composition.of("off", "always"));
        assertEquals(Composition.NEVER, Composition.of(" Off", null));
    }

    @Test
    @DisplayName("reads a value it does not understand as the default, rather than failing")
    void typosAreTheDefault() {
        assertEquals(Composition.ALWAYS, Composition.of(null, "alwyas"));
        assertEquals(Composition.ALWAYS, Composition.of("maybe", null));
    }

    @Test
    @DisplayName("says where windows will present, and how to change it")
    void describes() {
        assertTrue(Composition.ALWAYS.describe().contains("-Dgoldberry.gpu=off"));
        assertTrue(Composition.NEVER.describe().contains("on the CPU"));
        assertTrue(Composition.AUTO.describe().contains("GPU layer"));
    }
}
