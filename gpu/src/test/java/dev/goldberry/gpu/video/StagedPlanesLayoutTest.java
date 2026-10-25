package dev.goldberry.gpu.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Where [StagedPlanes] puts a picture's planes in upload memory, with rows
/// padded for a driver whose uploads take padded rows and packed for one whose
/// do not: the arithmetic, with no device.
@DisplayName("StagedPlanes' layout in upload memory")
class StagedPlanesLayoutTest {

    private static final int PADDED = 256;
    private static final int PACKED = 1;

    @Test
    @DisplayName("padded: every row a multiple of 256 bytes, every plane's start of 512")
    void padded() {
        assertEquals(256, StagedPlanes.stride(PlaneLayout.NV12, 0, 16, PADDED));
        assertEquals(256, StagedPlanes.stride(PlaneLayout.NV12, 1, 16, PADDED));
        assertEquals(List.of(0, 2048), StagedPlanes.offsets(PlaneLayout.NV12, 16, 8, PADDED));
        assertEquals(2048 + 256 * 4, StagedPlanes.size(PlaneLayout.NV12, 16, 8, PADDED));
    }

    @Test
    @DisplayName("packed: a row is as long as its pixels, and the planes still start on 512")
    void packed() {
        assertEquals(16, StagedPlanes.stride(PlaneLayout.NV12, 0, 16, PACKED));
        assertEquals(16, StagedPlanes.stride(PlaneLayout.NV12, 1, 16, PACKED));
        assertEquals(List.of(0, 512), StagedPlanes.offsets(PlaneLayout.NV12, 16, 8, PACKED));
        assertEquals(512 + 16 * 4, StagedPlanes.size(PlaneLayout.NV12, 16, 8, PACKED));
    }

    @Test
    @DisplayName("packed, an odd size: luma as wide as the picture, chroma rounded up, samples of two bytes")
    void packedOdd() {
        assertEquals(15, StagedPlanes.stride(PlaneLayout.NV12, 0, 15, PACKED));
        assertEquals(16, StagedPlanes.stride(PlaneLayout.NV12, 1, 15, PACKED));
        assertEquals(30, StagedPlanes.stride(PlaneLayout.P010, 0, 15, PACKED));
        assertEquals(32, StagedPlanes.stride(PlaneLayout.P010, 1, 15, PACKED));
        assertEquals(List.of(0, 512), StagedPlanes.offsets(PlaneLayout.P010, 15, 7, PACKED));
        assertEquals(512 + 32 * 4, StagedPlanes.size(PlaneLayout.P010, 15, 7, PACKED));
    }

    @Test
    @DisplayName("three planes: each starts on 512, after the last one's rows")
    void threePlanes() {
        assertEquals(List.of(0, 512, 1024), StagedPlanes.offsets(PlaneLayout.I420, 16, 8, PACKED));
        assertEquals(List.of(0, 2048, 3072), StagedPlanes.offsets(PlaneLayout.I420, 16, 8, PADDED));
    }
}
