package io.github.digitalsmile.goldberry.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;

/// The desktop's reduce-motion switch, through the SPI — [ADR-0383].
///
/// What the *platform* answers is tested in `:natives`, where the platform is.
/// What is here is the seam: three answers, empty by default, and a backend that
/// has never been asked saying so rather than guessing.
class ReducedMotionTest {

    @Test
    @DisplayName("a backend that cannot ask says the desktop does not say")
    void unknownByDefault() {
        try (var backend = new HeadlessBackend()) {
            assertEquals(Optional.empty(), backend.reducedMotion());
        }
    }

    @Test
    @DisplayName("and one that can gives the answer, both ways round")
    void bothAnswers() {
        try (var backend = new HeadlessBackend()) {
            backend.reducedMotion(true);
            assertEquals(Optional.of(true), backend.reducedMotion());

            backend.reducedMotion(false);
            assertEquals(
                    Optional.of(false),
                    backend.reducedMotion(),
                    "\"animate normally\" is an answer, and is not the same as no answer");

            backend.reducedMotion(null);
            assertTrue(backend.reducedMotion().isEmpty());
        }
    }
}
