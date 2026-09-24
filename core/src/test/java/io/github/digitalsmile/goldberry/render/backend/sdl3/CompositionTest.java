package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// When a window presents through the GPU, from `goldberry.gpu` and
/// `goldberry.gpu.composite` (ADR-0479).
@DisplayName("the composition policy")
class CompositionTest {

    @Test
    @DisplayName("composites nothing unless asked, since nothing needs it before GPU layers")
    void defaultsToAuto() {
        assertEquals(Composition.AUTO, Composition.of(null, null));
        assertEquals(Composition.AUTO, Composition.of("auto", "auto"));
    }

    @Test
    @DisplayName("composites every window when told always, and none when told never")
    void alwaysAndNever() {
        assertEquals(Composition.ALWAYS, Composition.of(null, "always"));
        assertEquals(Composition.ALWAYS, Composition.of("auto", " ALWAYS "));
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
        assertEquals(Composition.AUTO, Composition.of(null, "alwyas"));
        assertEquals(Composition.AUTO, Composition.of("maybe", null));
    }
}
