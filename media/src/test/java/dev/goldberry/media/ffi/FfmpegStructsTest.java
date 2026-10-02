package dev.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.MemoryLayout.PathElement;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The hand-written layouts against the probe's output: the layout test,
/// runnable anywhere.
@DisplayName("FfmpegStructs")
class FfmpegStructsTest {

    @Test
    @DisplayName("agree with the probe on every struct, field, offset, size and major")
    void agreeWithTheProbe() {
        assertEquals(List.of(), FfmpegLayoutCheck.verify(LayoutFixture.load(), FfmpegStructs.ALL));
    }

    @Test
    @DisplayName("every layout is named for its C type")
    void named() {
        for (var struct : FfmpegStructs.ALL) {
            assertEquals(true, struct.name().isPresent(), struct.toString());
        }
    }

    @Test
    @DisplayName("AVIOContext is 8 bytes shorter where C's long is 4 bytes, as on Windows, and its buffer stays put")
    void avIoContextFollowsTheLong() {
        var lp64 = FfmpegStructs.avIoContext(8);
        var llp64 = FfmpegStructs.avIoContext(4);
        var buffer = PathElement.groupElement("buffer");
        assertAll(
                () -> assertEquals(208, lp64.byteSize()),
                () -> assertEquals(200, llp64.byteSize()),
                () -> assertEquals(8, lp64.byteOffset(buffer)),
                () -> assertEquals(8, llp64.byteOffset(buffer)),
                () -> assertEquals(FfmpegStructs.avIoContext(FfmpegStructs.C_LONG_SIZE), FfmpegStructs.AV_IO_CONTEXT));
    }
}
