package dev.goldberry.gpu.raster;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.composite.ApiAccess;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

/// A raster texture on a real device, both ways: painted in place where the
/// device's check says so (lavapipe and the GPUs it has run on), and staged.
/// What is painted is what the texture holds, region by region, and the two
/// ways hold the same bytes.
@Tag(GpuTestLauncher.TAG)
@DisplayName("a raster texture, on the GPU")
class RasterTextureTest {

    private static final PhysicalSize SIZE = new PhysicalSize(48, 32);
    private static final int RED = 0xFFBF616A;
    private static final int GREEN = 0xFFA3BE8C;
    private static final int TRANSLUCENT = 0x8088C0D0;

    private static CompositeHarness harness;
    private static GpuDevice device;
    private static int baseline;

    @BeforeAll
    static void open() {
        harness = CompositeHarness.open();
        device = harness.device();
        // One of each first, so the device's staging buffer is in the baseline.
        for (var tile : List.of(RasterTexture.create(device, SIZE), RasterTexture.staged(device, SIZE))) {
            tile.paint(frame -> frame.fill(RED));
            readBack(tile);
            tile.close();
        }
        baseline = harness.openResources();
    }

    @AfterAll
    static void close() {
        if (harness != null) {
            harness.close();
        }
    }

    @AfterEach
    void nothingLeaks() {
        assertEquals(baseline, harness.openResources(), "a test left a resource open");
    }

    /// The two ways a tile is made.
    private static List<BiFunction<GpuDevice, PhysicalSize, RasterTexture>> ways() {
        return List.of(RasterTexture::create, RasterTexture::staged);
    }

    @Test
    @DisplayName("is painted in place as the device decided, and staged when asked to be")
    void path() {
        try (var made = RasterTexture.create(device, SIZE);
                var staged = RasterTexture.staged(device, SIZE)) {
            assertEquals(ApiAccess.inPlace(device).on(), made.inPlace(), made::toString);
            assertFalse(staged.inPlace());
            assertEquals(SIZE, made.size());
            assertEquals(SIZE.width(), made.texture().width());
        }
    }

    @Test
    @DisplayName("uploads the whole of a new raster first, transparent where nothing was painted")
    void firstUploadIsWhole() {
        for (var way : ways()) {
            try (var tile = way.apply(device, SIZE)) {
                assertEquals(List.of(PhysicalRect.of(SIZE)), tile.damage(), "a new raster is damaged whole");
                tile.paint(List.of(PhysicalRect.of(2, 2, 8, 8)), frame -> frame.fill(RED));
                assertEquals(List.of(PhysicalRect.of(SIZE)), tile.damage(), "and stays so until it goes up");
                var pixels = readBack(tile);
                assertTrue(tile.damage().isEmpty(), "the upload forgets what it copied");
                assertEquals(RED, pixel(pixels, 3, 3), tile::toString);
                assertEquals(RED, pixel(pixels, 9, 9), tile::toString);
                assertEquals(0, pixel(pixels, 10, 10), "outside the paint, nothing: " + tile);
                assertEquals(0, pixel(pixels, 0, 0), tile::toString);
            }
        }
    }

    @Test
    @DisplayName("uploads a later paint's regions alone, and the texture keeps the rest")
    void laterPaintsGoUpByRegion() {
        for (var way : ways()) {
            try (var tile = way.apply(device, SIZE)) {
                tile.paint(frame -> frame.fill(RED));
                readBack(tile);
                var regions = List.of(PhysicalRect.of(4, 4, 6, 6), PhysicalRect.of(30, 20, 10, 8));
                tile.paint(regions, frame -> frame.fill(GREEN));
                assertEquals(regions, tile.damage());
                var pixels = readBack(tile);
                assertEquals(GREEN, pixel(pixels, 4, 4));
                assertEquals(GREEN, pixel(pixels, 39, 27));
                assertEquals(RED, pixel(pixels, 10, 10), "kept from the first upload: " + tile);
                assertEquals(RED, pixel(pixels, 47, 31), tile::toString);
            }
        }
    }

    @Test
    @DisplayName("holds the same bytes painted in place as staged, translucent paint over earlier paint included")
    void bothWaysAgree() {
        var pictures = new ArrayList<int[]>();
        for (var way : ways()) {
            try (var tile = way.apply(device, SIZE)) {
                tile.paint(frame -> {
                    frame.fill(RED);
                    frame.fillRect(5, 3, 30, 20, TRANSLUCENT);
                });
                readBack(tile);
                tile.paint(List.of(PhysicalRect.of(8, 8, 20, 12), PhysicalRect.of(0, 0, 3, 3)), frame -> {
                    frame.fillRect(0, 0, 48, 32, TRANSLUCENT);
                    frame.fillRect(10, 10, 4, 4, GREEN);
                });
                pictures.add(readBack(tile));
            }
        }
        assertArrayEquals(pictures.get(0), pictures.get(1));
    }

    @Test
    @DisplayName(
            "is painted again before its upload's frame is submitted without waiting, and the next upload holds it")
    void paintedBeforeTheSubmit() {
        for (var way : ways()) {
            try (var tile = way.apply(device, SIZE)) {
                tile.paint(frame -> frame.fill(RED));
                try (var frame = device.beginFrame()) {
                    frame.copyPass(tile::upload);
                    // The GPU has not begun: this must not wait for it.
                    tile.paint(List.of(PhysicalRect.of(0, 0, 4, 4)), painted -> painted.fill(GREEN));
                    frame.submit();
                }
                assertEquals(List.of(PhysicalRect.of(0, 0, 4, 4)), tile.damage(), "still owed to the texture");
                // After the submit: the paint waits for that frame, then goes on.
                tile.paint(List.of(PhysicalRect.of(4, 0, 4, 4)), painted -> painted.fill(GREEN));
                var pixels = readBack(tile);
                assertEquals(GREEN, pixel(pixels, 0, 0), tile::toString);
                assertEquals(GREEN, pixel(pixels, 7, 3), tile::toString);
                assertEquals(RED, pixel(pixels, 8, 0), tile::toString);
            }
        }
    }

    @Test
    @DisplayName("keeps its damage as bounds once there are too many regions, and the whole when one covers it")
    void damageStaysSmall() {
        try (var tile = RasterTexture.create(device, SIZE)) {
            readBack(tile);
            var many = new ArrayList<PhysicalRect>();
            for (var i = 0; i < 40; i++) {
                many.add(PhysicalRect.of(i, i % 30, 1, 1));
            }
            tile.paint(many, frame -> frame.fill(GREEN));
            assertEquals(List.of(PhysicalRect.of(0, 0, 40, 30)), tile.damage());
            tile.paint(List.of(PhysicalRect.of(1, 1, 1, 1)), frame -> frame.fill(RED));
            tile.paint(frame -> frame.fill(RED));
            assertEquals(List.of(PhysicalRect.of(SIZE)), tile.damage());
            tile.paint(List.of(PhysicalRect.of(0, 0, 0, 5)), frame -> {
                throw new AssertionError("an empty region is not painted");
            });
            readBack(tile);
        }
    }

    @Test
    @DisplayName("refuses a region outside it, an empty size, and use once closed; closing twice is closing")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> RasterTexture.create(device, new PhysicalSize(0, 4)));
        assertThrows(IllegalArgumentException.class, () -> RasterTexture.staged(device, new PhysicalSize(4, 0)));
        for (var way : ways()) {
            var tile = way.apply(device, SIZE);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> tile.paint(List.of(PhysicalRect.of(40, 0, 9, 1)), frame -> {}));
            tile.close();
            tile.close();
            assertTrue(tile.isClosed());
            assertTrue(tile.texture().isClosed(), "the texture goes with it");
            assertThrows(IllegalStateException.class, () -> tile.paint(frame -> {}));
            try (var frame = device.beginFrame()) {
                frame.copyPass(copy -> assertThrows(IllegalStateException.class, () -> tile.upload(copy)));
                frame.submit();
            }
        }
    }

    /// Uploads `tile`'s damage in a frame of its own and reads the texture
    /// back, as ARGB ints row after row.
    private static int[] readBack(RasterTexture tile) {
        try (var frame = device.beginFrame()) {
            frame.copyPass(tile::upload);
            var readback = frame.readback(tile.texture());
            frame.submit();
            var pixels = readback.awaitPixels();
            var width = pixels.size().width();
            var height = pixels.size().height();
            var bytes = pixels.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            var argb = new int[width * height];
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    argb[y * width + x] = bytes.getInt(y * pixels.stride() + x * 4);
                }
            }
            return argb;
        }
    }

    private static int pixel(int[] argb, int x, int y) {
        return argb[y * SIZE.width() + x];
    }
}
