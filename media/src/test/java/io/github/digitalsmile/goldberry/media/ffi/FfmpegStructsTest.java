package io.github.digitalsmile.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The hand-written layouts against the probe's output: `docs/goldberry-media.md`
/// §2's layout test, runnable anywhere.
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
}
