package dev.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// How the staging memory grows, which needs no device: it is arithmetic.
@DisplayName("the upload staging memory")
class StagingBufferTest {

    @Test
    @DisplayName("starts at 64 KiB, however small the first upload")
    void startsAtTheMinimum() {
        assertEquals(StagingBuffer.MINIMUM_CAPACITY, StagingBuffer.grow(0, 1));
        assertEquals(64 * 1024, StagingBuffer.MINIMUM_CAPACITY);
    }

    @Test
    @DisplayName("at least doubles, so growing a pixel at a time does not allocate a pixel at a time")
    void doubles() {
        assertEquals(2 * 100_000, StagingBuffer.grow(100_000, 100_001));
        assertEquals(1_000_000, StagingBuffer.grow(100_000, 1_000_000), "or jumps straight to what is needed");
    }

    @Test
    @DisplayName("stops at 2 GiB, and refuses an upload past it")
    void capsAtSdlsLimit() {
        assertEquals(Integer.MAX_VALUE, StagingBuffer.grow(1_500_000_000, 1_500_000_001));
        assertThrows(IllegalArgumentException.class, () -> StagingBuffer.grow(0, Integer.MAX_VALUE + 1L));
    }
}
