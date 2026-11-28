package dev.goldberry.media.container.matroska;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class MatroskaCodecsTest {

    @Test
    void readsCompleteVp9FeaturesBeforeAnIncompleteField() {
        var features = new byte[] {3, 1, 10, 4, 1, 2, 4, 1};

        assertEquals(Optional.of("yuv422p10le"), MatroskaCodecs.vp9PixelFormat(features));
        assertEquals(Optional.empty(), MatroskaCodecs.vp9PixelFormat(new byte[] {4, 1}));
    }
}
