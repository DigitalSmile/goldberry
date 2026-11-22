package dev.goldberry.gpu.render.mapped;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.gpu.render.staging.UploadAlignment;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.render.model.PhysicalSize;

/// The raster's bookkeeping, with the GPU replaced by a copy whose fence the
/// test signals: a paint waits for the copy in flight, an upload records what
/// the layout says, and closing waits too.
@DisplayName("a raster painted in place")
class MappedRasterTest {

    private static final PhysicalSize SIZE = new PhysicalSize(100, 40);
    private static final InPlaceLayout BANDS = new InPlaceLayout(UploadAlignment.PACKED, CopyShape.BANDS);

    /// A copy that finishes when the test says so.
    private static final class HeldCopy implements MappedRaster.PendingCopy {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch signalled = new CountDownLatch(1);
        int finished;

        @Override
        public void finish() {
            entered.countDown();
            try {
                signalled.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            finished++;
        }
    }

    /// What the raster asked the GPU to do.
    private static final class Recorder implements MappedRaster.Submit, MappedRaster.Copier {
        final List<List<SdlGpuRegion>> copies = new ArrayList<>();
        final List<Boolean> whole = new ArrayList<>();
        final List<HeldCopy> pending = new ArrayList<>();
        final AtomicBoolean closed = new AtomicBoolean();
        int stride;

        @Override
        public MappedRaster.PendingCopy submit(List<SdlGpuRegion> regions, int rowBytes, boolean all) {
            copies.add(regions);
            whole.add(all);
            stride = rowBytes;
            var copy = new HeldCopy();
            pending.add(copy);
            return copy;
        }

        @Override
        public MappedRaster.PendingCopy copy(
                SdlGpuTexture texture, List<SdlGpuRegion> regions, int rowBytes, boolean all) {
            return submit(regions, rowBytes, all);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static MappedRaster raster(InPlaceLayout layout, Recorder recorder) {
        return new MappedRaster(
                SIZE, layout, ByteBuffer.allocateDirect(layout.stride(SIZE.width()) * SIZE.height()), recorder);
    }

    @Test
    @Timeout(10)
    @DisplayName(
            "lends its pixels only once the last copy has finished: a fence that has not signalled holds the paint")
    void paintWaitsForTheCopy() throws Exception {
        var recorder = new Recorder();
        try (var raster = raster(BANDS, recorder)) {
            var pixels = raster.lend();
            raster.upload(SIZE.width(), SIZE.height(), List.of(new SdlGpuRegion(3, 4, 5, 6)), recorder);
            assertTrue(raster.copyInFlight());
            var copy = recorder.pending.getFirst();
            var lent = CompletableFuture.supplyAsync(raster::lend);
            copy.entered.await();
            assertFalse(lent.isDone(), "the paint went ahead of an unfinished copy");
            copy.signalled.countDown();
            assertSame(pixels, lent.join(), "the same memory, holding last frame's pixels");
            assertFalse(raster.copyInFlight());
            assertEquals(1, recorder.pending.getFirst().finished);
        }
    }

    @Test
    @DisplayName("copies what its layout says, rows a stride apart, and whole only when every texel is copied")
    void copiesByLayout() {
        var recorder = new Recorder();
        try (var raster = raster(BANDS, recorder)) {
            raster.upload(
                    SIZE.width(),
                    SIZE.height(),
                    List.of(new SdlGpuRegion(3, 4, 5, 6), new SdlGpuRegion(50, 8, 5, 6)),
                    recorder);
            recorder.pending.getLast().signalled.countDown();
            raster.upload(SIZE.width(), SIZE.height(), List.of(new SdlGpuRegion(0, 0, 100, 40)), recorder);
            recorder.pending.getLast().signalled.countDown();
        }
        assertEquals(List.of(new SdlGpuRegion(0, 4, 100, 10)), recorder.copies.get(0), "one joined band");
        assertEquals(List.of(new SdlGpuRegion(0, 0, 100, 40)), recorder.copies.get(1));
        assertEquals(List.of(false, true), recorder.whole);
        assertEquals(400, recorder.stride);
    }

    @Test
    @DisplayName("on Vulkan copies the rectangles themselves")
    void rectangles() {
        var recorder = new Recorder();
        var damage = List.of(new SdlGpuRegion(3, 4, 5, 6), new SdlGpuRegion(50, 8, 5, 6));
        try (var raster = raster(InPlaceLayout.of("vulkan", true), recorder)) {
            raster.upload(SIZE.width(), SIZE.height(), damage, recorder);
            recorder.pending.getLast().signalled.countDown();
        }
        assertEquals(damage, recorder.copies.getFirst());
    }

    @Test
    @DisplayName("submits nothing for no damage, and refuses damage outside it and a texture of another size")
    void refuses() {
        var recorder = new Recorder();
        try (var raster = raster(BANDS, recorder)) {
            raster.upload(SIZE.width(), SIZE.height(), List.of(), recorder);
            assertFalse(raster.copyInFlight());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> raster.upload(
                            SIZE.width(), SIZE.height(), List.of(new SdlGpuRegion(90, 0, 20, 1)), recorder));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> raster.upload(
                            SIZE.width() + 1, SIZE.height(), List.of(new SdlGpuRegion(0, 0, 1, 1)), recorder));
        }
        assertTrue(recorder.copies.isEmpty());
    }

    @Test
    @Timeout(10)
    @DisplayName("waits for the copy in flight when closed, then gives the memory back, once")
    void closeWaits() throws Exception {
        var recorder = new Recorder();
        var raster = raster(BANDS, recorder);
        raster.upload(SIZE.width(), SIZE.height(), List.of(new SdlGpuRegion(0, 0, 1, 1)), recorder);
        var copy = recorder.pending.getFirst();
        var closing = CompletableFuture.runAsync(raster::close);
        copy.entered.await();
        assertFalse(closing.isDone());
        assertFalse(recorder.closed.get(), "the memory went while the GPU could still read it");
        copy.signalled.countDown();
        closing.join();
        assertTrue(recorder.closed.get());
        raster.close();
        assertThrows(IllegalStateException.class, raster::lend);
    }

    @Test
    @DisplayName("lends the pixels as BGRA at its layout's stride")
    void pixels() {
        try (var raster = raster(new InPlaceLayout(UploadAlignment.DIRECT3D12, CopyShape.BANDS), new Recorder())) {
            var pixels = raster.lend();
            assertEquals(SIZE, pixels.size());
            assertEquals(512, pixels.stride(), "100 texels, 400 bytes, rounded up to 256");
        }
    }
}
