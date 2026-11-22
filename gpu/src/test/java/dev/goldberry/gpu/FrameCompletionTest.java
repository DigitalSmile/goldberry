package dev.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;

/// What a frame tells a copy that waits on it: nothing to wait for while it
/// records, its fence once submitted, and nothing ever once discarded. One
/// completion per frame, shared with its readbacks' fence.
@Tag(GpuTestLauncher.TAG)
@DisplayName("a frame's completion, on a real device")
class FrameCompletionTest {

    private static SdlGpuDevice sdl;
    private static GpuDevice device;

    @BeforeAll
    static void createDevice() {
        sdl = GpuDeviceRequirement.enforce();
        device = GpuDevice.wrap(sdl);
    }

    @AfterAll
    static void destroyDevice() {
        if (sdl == null) {
            return;
        }
        sdl.close();
        Sdl.get().quit();
    }

    @Test
    @DisplayName("answers at once while recording, waits once submitted, and is one per frame")
    void submitted() {
        try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 4, 4));
                var frame = device.beginFrame()) {
            var completion = frame.completion();
            assertSame(completion, frame.completion(), "one per frame");
            assertFalse(completion.awaitIfSubmitted(), "nothing to wait for before the submit");
            assertTrue(completion.isRecording());
            // A readback beside it: the two share the frame's one fence.
            var pixels = frame.readback(texture);
            frame.submit();
            assertTrue(completion.awaitIfSubmitted(), "waited for the fence");
            assertTrue(completion.awaitIfSubmitted(), "and done since");
            pixels.awaitPixels();
        }
    }

    @Test
    @DisplayName("is done at once when its frame is discarded")
    void discarded() {
        FrameCompletion completion;
        try (var frame = device.beginFrame()) {
            completion = frame.completion();
        }
        assertFalse(completion.isRecording());
        assertTrue(completion.awaitIfSubmitted());
    }
}
