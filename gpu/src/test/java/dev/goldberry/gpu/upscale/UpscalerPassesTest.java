package dev.goldberry.gpu.upscale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.upscale.Upscaler.Pass;
import dev.goldberry.render.model.PhysicalSize;

/// Which passes an upscale runs, for each filter and pair of sizes: decided
/// before any device is asked. Read more:
/// [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
@DisplayName("an upscaler's passes")
class UpscalerPassesTest {

    private static final PhysicalSize SMALL = new PhysicalSize(640, 360);
    private static final PhysicalSize BIG = new PhysicalSize(1280, 720);

    @Test
    @DisplayName("are one bilinear pass for linear, whatever the sizes")
    void linear() {
        assertEquals(List.of(Pass.LINEAR), Upscaler.passes(UpscaleFilter.LINEAR, 1, SMALL, BIG));
        assertEquals(List.of(Pass.LINEAR), Upscaler.passes(UpscaleFilter.LINEAR, 1, BIG, SMALL));
    }

    @Test
    @DisplayName("are EASU then RCAS for FSR 1 upscaling, EASU alone with no sharpness")
    void fsr1Upscaling() {
        assertEquals(List.of(Pass.EASU, Pass.RCAS), Upscaler.passes(UpscaleFilter.FSR1, 0.87, SMALL, BIG));
        assertEquals(List.of(Pass.EASU), Upscaler.passes(UpscaleFilter.FSR1, 0, SMALL, BIG));
        assertEquals(
                List.of(Pass.EASU, Pass.RCAS),
                Upscaler.passes(UpscaleFilter.FSR1, 0.5, new PhysicalSize(1280, 360), BIG),
                "smaller on one axis and the same on the other");
    }

    @Test
    @DisplayName("are RCAS alone for FSR 1 at the same size, and bilinear for FSR 1 bringing a picture down")
    void fsr1Otherwise() {
        assertEquals(List.of(Pass.RCAS), Upscaler.passes(UpscaleFilter.FSR1, 0.87, BIG, BIG));
        assertEquals(List.of(Pass.LINEAR), Upscaler.passes(UpscaleFilter.FSR1, 0, BIG, BIG), "a plain copy");
        assertEquals(List.of(Pass.LINEAR), Upscaler.passes(UpscaleFilter.FSR1, 0.87, BIG, SMALL));
        assertEquals(
                List.of(Pass.LINEAR),
                Upscaler.passes(UpscaleFilter.FSR1, 0.87, new PhysicalSize(1281, 360), BIG),
                "larger on one axis");
    }
}
