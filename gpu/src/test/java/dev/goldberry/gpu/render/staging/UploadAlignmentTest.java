package dev.goldberry.gpu.render.staging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// Which drivers stage padded rows, and what padding a row comes to.
@DisplayName("how uploads are aligned per driver")
class UploadAlignmentTest {

    @Test
    @DisplayName("Direct3D 12 pads rows to 256 bytes and places regions at multiples of 512")
    void direct3d12() {
        var alignment = UploadAlignment.of("direct3d12");
        assertSame(UploadAlignment.DIRECT3D12, alignment);
        assertEquals(256, alignment.rowBytes());
        assertEquals(512, alignment.regionBytes());
        assertTrue(alignment.padsRows());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"vulkan", "metal", "something new"})
    @DisplayName("every other driver stays packed")
    void othersArePacked(String driver) {
        var alignment = UploadAlignment.of(driver);
        assertSame(UploadAlignment.PACKED, alignment);
        assertFalse(alignment.padsRows());
        assertEquals(41 * 4, alignment.pitch(41 * 4), "a row is as long as it is");
        assertEquals(1234, alignment.place(1234), "a region starts where the last ended");
    }

    @ParameterizedTest(name = "a row of {0} bytes is {1} apart")
    @CsvSource({"4, 256", "256, 256", "257, 512", "7680, 7680", "15360, 15360", "5124, 5376"})
    @DisplayName("the pitch is the row rounded up to 256, and no longer, which is the one SDL copies as it is")
    void pitch(long row, int pitch) {
        assertEquals(pitch, UploadAlignment.DIRECT3D12.pitch(row));
    }

    @ParameterizedTest(name = "byte {0} places at {1}")
    @CsvSource({"0, 0", "1, 512", "512, 512", "513, 1024", "4096, 4096"})
    @DisplayName("a region starts at the next multiple of 512")
    void place(long at, long placed) {
        assertEquals(placed, UploadAlignment.DIRECT3D12.place(at));
    }

    @Test
    @DisplayName("refuses alignments that are not powers of two, and rows that are empty or past 2 GiB")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> new UploadAlignment(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new UploadAlignment(256, 500));
        assertThrows(IllegalArgumentException.class, () -> UploadAlignment.DIRECT3D12.pitch(0));
        assertThrows(IllegalArgumentException.class, () -> UploadAlignment.DIRECT3D12.pitch(Integer.MAX_VALUE));
        assertThrows(NullPointerException.class, () -> UploadAlignment.of(null));
    }
}
