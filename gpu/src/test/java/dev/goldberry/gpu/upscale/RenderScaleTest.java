package dev.goldberry.gpu.upscale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.model.PhysicalSize;

/// What a render scale allows, and the size it draws a box at. Read more:
/// [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
@DisplayName("a render scale")
class RenderScaleTest {

    @Test
    @DisplayName("draws a box at each axis times the factor, rounded, never below a pixel")
    void drawSize() {
        var box = new PhysicalSize(1920, 1080);
        assertSame(box, RenderScale.NATIVE.drawSize(box), "native is the box itself");
        assertEquals(new PhysicalSize(960, 540), RenderScale.of(0.5).drawSize(box));
        assertEquals(new PhysicalSize(1286, 724), RenderScale.of(0.67).drawSize(box), "rounded, not cut");
        assertEquals(new PhysicalSize(3840, 2160), RenderScale.of(2).drawSize(box));
        assertEquals(new PhysicalSize(1, 1), RenderScale.of(0.5).drawSize(new PhysicalSize(1, 1)));
    }

    @Test
    @DisplayName("is native at 1 alone, linear and AMD's sharpness by default")
    void defaults() {
        assertTrue(RenderScale.NATIVE.isNative());
        assertFalse(RenderScale.of(0.99).isNative());
        var scale = RenderScale.of(0.75);
        assertEquals(UpscaleFilter.LINEAR, scale.filter());
        assertEquals(Math.pow(2, -0.2), scale.sharpness(), 0.005, "FSR's 0.2 stops");
        assertEquals(
                new RenderScale(0.6, UpscaleFilter.FSR1, 0.2),
                scale.withFactor(0.6).withFilter(UpscaleFilter.FSR1).withSharpness(0.2));
    }

    @Test
    @DisplayName("refuses a factor outside 0.5 to 2, a sharpness outside 0 to 1, and NaN")
    void refuses() {
        for (var factor : List.of(0.49, 2.01, Double.NaN, 0.0, -1.0)) {
            assertThrows(IllegalArgumentException.class, () -> RenderScale.of(factor), "factor " + factor);
        }
        for (var sharpness : List.of(-0.1, 1.1, Double.NaN)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RenderScale.NATIVE.withSharpness(sharpness),
                    "sharpness " + sharpness);
        }
        assertThrows(NullPointerException.class, () -> RenderScale.NATIVE.withFilter(null));
        RenderScale.of(RenderScale.MIN_FACTOR);
        RenderScale.of(RenderScale.MAX_FACTOR);
    }

    @Test
    @DisplayName("names its filters as the markup does")
    void filterNames() {
        assertEquals(UpscaleFilter.LINEAR, UpscaleFilter.named(null));
        assertEquals(UpscaleFilter.FSR1, UpscaleFilter.named(" FSR1 "));
        assertEquals(UpscaleFilter.LINEAR, UpscaleFilter.named("linear"));
        assertEquals("fsr1", UpscaleFilter.FSR1.toString());
        var refused = assertThrows(IllegalArgumentException.class, () -> UpscaleFilter.named("dlss"));
        assertTrue(refused.getMessage().contains("linear or fsr1"), refused.getMessage());
    }
}
