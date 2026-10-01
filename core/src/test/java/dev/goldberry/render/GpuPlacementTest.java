package dev.goldberry.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.model.PhysicalRect;

/// Where a GPU layer was placed (ADR-0481).
@DisplayName("a GPU layer's placement")
class GpuPlacementTest {

    private static final GpuContent LAYER = new GpuContent() {};

    @Test
    @DisplayName("has a scissor that is a non-empty part of its target")
    void scissorInsideTarget() {
        var target = PhysicalRect.of(10, 10, 20, 20);
        assertDoesNotThrow(() -> new GpuPlacement(LAYER, target, PhysicalRect.of(10, 15, 20, 15)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GpuPlacement(LAYER, target, PhysicalRect.of(5, 10, 20, 20)),
                "reaching left of it");
        assertThrows(
                IllegalArgumentException.class,
                () -> new GpuPlacement(LAYER, target, PhysicalRect.of(10, 10, 20, 21)),
                "reaching below it");
        assertThrows(
                IllegalArgumentException.class,
                () -> new GpuPlacement(LAYER, target, PhysicalRect.of(10, 10, 0, 5)),
                "empty");
    }

    @Test
    @DisplayName("overlaps damage by its visible part, and not by the part the clips hide")
    void overlapsByItsScissor() {
        var placement = new GpuPlacement(LAYER, PhysicalRect.of(0, 0, 100, 100), PhysicalRect.of(0, 0, 50, 50));
        assertTrue(placement.overlaps(new DamageRect(49, 49, 1, 1)));
        assertFalse(placement.overlaps(new DamageRect(50, 0, 10, 10)), "touching is not overlapping");
        assertFalse(placement.overlaps(new DamageRect(60, 60, 10, 10)), "inside the target, outside the scissor");
    }
}
