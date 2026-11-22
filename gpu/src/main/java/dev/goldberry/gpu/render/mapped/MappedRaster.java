package dev.goldberry.gpu.render.mapped;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuFence;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.natives.sdl.gpu.SdlGpuTransferBuffer;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// A BGRA raster painted where the GPU copies from: one upload transfer
/// buffer the size of the raster, mapped for as long as it is open and lent
/// as a [PixelBuffer] Blend2D paints into, whose damaged regions are copied
/// into a texture with no copy on the CPU.
///
/// **One raster, the copy submitted alone.** [#upload] records the copies in a
/// command buffer of its own and submits it with a fence; [#lend] waits for
/// that fence before handing the pixels back, so a paint never writes what the
/// GPU has yet to read. In steady state the copy has long finished by the next
/// frame and the wait costs nothing. The raster keeps last frame's pixels, so
/// partial repaint works as it does in heap memory.
///
/// **Or the copy recorded into someone else's frame.** [#upload(SdlGpuCommandBuffer.CopyPass, SdlGpuTexture, List,
/// PendingCopy)]
/// records into a copy pass the caller submits, and waits on what the caller
/// says finishes it: the application's frame, for a raster texture.
///
/// Used on one thread, the one that paints and presents.
public final class MappedRaster implements AutoCloseable {

    /// Records and submits the copies of one upload, and answers what to wait
    /// on: the GPU half, apart so the waiting can be tested without a GPU.
    interface Copier {

        /// Submits copies of `regions` of the raster, its rows `stride` bytes
        /// apart, into `texture`.
        ///
        /// @param whole whether the copies cover every texel of the texture,
        ///              so it may take fresh memory rather than wait
        PendingCopy copy(SdlGpuTexture texture, List<SdlGpuRegion> regions, int stride, boolean whole);

        /// Records the same copies into `pass`, which the caller submits.
        void record(
                SdlGpuCommandBuffer.CopyPass pass,
                SdlGpuTexture texture,
                List<SdlGpuRegion> regions,
                int stride,
                boolean whole);

        /// Gives the memory back.
        void close();
    }

    /// A recorded copy, until it has finished.
    public interface PendingCopy {

        /// Blocks until the GPU has finished the copy and lets go of what
        /// tracked it, and answers true; or answers false at once when the copy
        /// has not been submitted yet, so the GPU has not begun reading and the
        /// raster may be painted. True once the copy will never run: its frame
        /// was discarded. Idempotent once true.
        boolean finish();
    }

    private final PhysicalSize size;
    private final InPlaceLayout layout;
    private final PixelBuffer pixels;
    private final Copier copier;
    /// The copies not yet known to have finished, oldest first: one, unless a
    /// caller's frame is still recording.
    private final List<PendingCopy> pending = new ArrayList<>(2);
    private boolean closed;

    MappedRaster(PhysicalSize size, InPlaceLayout layout, ByteBuffer memory, Copier copier) {
        this.size = Objects.requireNonNull(size, "size");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.copier = Objects.requireNonNull(copier, "copier");
        this.pixels = new PixelBuffer(size, PixelFormat.BGRA32_PREMULTIPLIED, layout.stride(size.width()), memory);
    }

    /// A raster of `size` on `device`, laid out as `layout` says: an upload
    /// buffer of the stride times the height, mapped until [#close].
    ///
    /// @throws IllegalArgumentException when the raster is past 2 GiB
    /// @throws dev.goldberry.natives.sdl.SdlException when SDL refuses the
    ///         memory
    public static MappedRaster create(SdlGpuDevice device, PhysicalSize size, InPlaceLayout layout) {
        Objects.requireNonNull(device, "device");
        var bytes = (long) layout.stride(size.width()) * size.height();
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a " + size + " raster is past SDL's 2 GiB");
        }
        var buffer = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, (int) bytes);
        try {
            return new MappedRaster(size, layout, buffer.mapPersistently(), new SdlCopier(device, buffer));
        } catch (RuntimeException e) {
            buffer.close();
            throw e;
        }
    }

    /// Its size in pixels.
    public PhysicalSize size() {
        return size;
    }

    /// How it is laid out and copied.
    public InPlaceLayout layout() {
        return layout;
    }

    /// The pixels to paint, once the last upload's copy has finished, or while
    /// its frame has not been submitted: they still hold what was painted
    /// last.
    ///
    /// @throws IllegalStateException when it is closed
    public PixelBuffer lend() {
        requireOpen();
        finishPending();
        return pixels;
    }

    /// Whether an upload's copy has been recorded and not yet waited for.
    public boolean copyInFlight() {
        return !pending.isEmpty();
    }

    /// Copies the regions of `damage` into `texture`, a BGRA texture of the
    /// raster's size, as [InPlaceLayout#copies] widens them, and submits the
    /// copy with a fence the next [#lend] waits for. Empty damage submits
    /// nothing.
    ///
    /// @return what was copied: the damage, or the bands that hold it
    /// @throws IllegalArgumentException when the texture is not the raster's
    ///                                  size, or a region is outside it
    /// @throws IllegalStateException    when it is closed
    public List<SdlGpuRegion> upload(SdlGpuTexture texture, List<SdlGpuRegion> damage) {
        Objects.requireNonNull(texture, "texture");
        return upload(
                texture.width(),
                texture.height(),
                damage,
                (copies, stride, whole) -> copier.copy(texture, copies, stride, whole));
    }

    /// Records the copies of `damage` into `pass`, a copy pass of the
    /// caller's, as [#upload(SdlGpuTexture, List)] submits them; the next
    /// [#lend] waits on `finished`, which says when the caller's frame has
    /// run. Empty damage records nothing.
    ///
    /// @return what was copied
    /// @throws IllegalArgumentException as [#upload(SdlGpuTexture, List)]
    /// @throws IllegalStateException    when it is closed
    public List<SdlGpuRegion> upload(
            SdlGpuCommandBuffer.CopyPass pass, SdlGpuTexture texture, List<SdlGpuRegion> damage, PendingCopy finished) {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(finished, "finished");
        return upload(texture.width(), texture.height(), damage, (copies, stride, whole) -> {
            copier.record(pass, texture, copies, stride, whole);
            return finished;
        });
    }

    /// How one upload's copies are submitted: [#upload(SdlGpuTexture, List)]'s
    /// to the device, a test's to a fake.
    interface Submit {

        /// Submits `copies`, rows `stride` bytes apart.
        PendingCopy submit(List<SdlGpuRegion> copies, int stride, boolean whole);
    }

    /// [#upload(SdlGpuTexture, List)] into a target `width` by `height`.
    List<SdlGpuRegion> upload(int width, int height, List<SdlGpuRegion> damage, Submit submit) {
        requireOpen();
        if (width != size.width() || height != size.height()) {
            throw new IllegalArgumentException(
                    "a " + width + "x" + height + " texture is not the size of a " + size + " raster");
        }
        for (var region : damage) {
            if (!region.fitsIn(size.width(), size.height())) {
                throw new IllegalArgumentException(region + " is outside a " + size + " raster");
            }
        }
        if (damage.isEmpty()) {
            return List.of();
        }
        finishPending();
        var copies = layout.copies(damage, size.width());
        var whole = copies.size() == 1
                && copies.getFirst().width() == size.width()
                && copies.getFirst().height() == size.height();
        pending.add(submit.submit(copies, pixels.stride(), whole));
        return copies;
    }

    /// Waits for the copy in flight, then gives the memory back. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            finishPending();
        } finally {
            copier.close();
        }
    }

    /// Waits for every copy that has been submitted, and keeps the ones
    /// still to be.
    private void finishPending() {
        pending.removeIf(PendingCopy::finish);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("the raster is closed");
        }
    }

    /// The copies recorded on SDL's device, each in a command buffer of its
    /// own behind a fence.
    private record SdlCopier(SdlGpuDevice device, SdlGpuTransferBuffer buffer) implements Copier {

        @Override
        public PendingCopy copy(SdlGpuTexture texture, List<SdlGpuRegion> regions, int stride, boolean whole) {
            var commands = device.acquireCommandBuffer();
            try {
                try (var pass = commands.beginCopyPass()) {
                    record(pass, texture, regions, stride, whole);
                }
                var fence = commands.submitWithFence();
                return new FencedCopy(fence);
            } finally {
                if (!commands.isFinished()) {
                    // Recording threw. SDL requires the buffer be submitted
                    // or cancelled; what was recorded is harmless to submit.
                    commands.submit();
                }
            }
        }

        @Override
        public void record(
                SdlGpuCommandBuffer.CopyPass pass,
                SdlGpuTexture texture,
                List<SdlGpuRegion> regions,
                int stride,
                boolean whole) {
            for (var region : regions) {
                pass.upload(buffer, region.y() * stride + region.x() * 4, stride / 4, texture, region, whole);
            }
        }

        @Override
        public void close() {
            buffer.close();
        }
    }

    /// A copy behind its fence.
    private static final class FencedCopy implements PendingCopy {

        private @Nullable SdlGpuFence fence;

        FencedCopy(SdlGpuFence fence) {
            this.fence = fence;
        }

        @Override
        public boolean finish() {
            var current = fence;
            if (current == null) {
                return true;
            }
            fence = null;
            try (current) {
                current.await();
            }
            return true;
        }
    }
}
