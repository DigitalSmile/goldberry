package dev.goldberry.gpu.video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.UploadBuffer;

/// One picture's planes in a [VideoLayer]'s upload memory, from
/// [PlaneStaging#obtain]: written where they are decoded, and uploaded by the
/// layer with no copy on its own thread.
///
/// The planes lie one after another in one [UploadBuffer], each at an offset
/// of a multiple of 512 bytes with rows a multiple of 256 bytes apart, which
/// is what every driver copies from as it is. While the picture is held, its
/// planes are mapped: the holder writes them, and reads them, through
/// [#plane]. [#release] gives the memory back to the staging, which maps it
/// afresh before it is handed out again, so the next picture is never written
/// where the GPU is still reading the last.
///
/// **Two threads.** [#plane] and [#release] may be called from any thread; the
/// layer unmaps the buffer on its own for the instant it records the upload,
/// and maps the same memory again after. A read on another thread at that
/// instant, or after the layer's device is gone, throws
/// [IllegalStateException]; it never reaches unmapped memory.
public final class StagedPlanes {

    private final PlaneStaging staging;
    private final UploadBuffer buffer;
    private final PlaneLayout layout;
    private final int width;
    private final int height;
    private final List<Integer> offsets;
    private final List<Integer> strides;
    /// The mapping planes are read through; null while unmapped. Guarded by
    /// the staging's lock.
    private @Nullable ByteBuffer mapping;

    StagedPlanes(
            PlaneStaging staging,
            UploadBuffer buffer,
            PlaneLayout layout,
            int width,
            int height,
            List<Integer> offsets,
            List<Integer> strides) {
        this.staging = staging;
        this.buffer = buffer;
        this.layout = layout;
        this.width = width;
        this.height = height;
        this.offsets = List.copyOf(offsets);
        this.strides = List.copyOf(strides);
    }

    /// The bytes a picture of `layout`, `width` by `height`, takes staged: each
    /// plane's rows rounded up to 256 bytes, each plane's start to 512.
    static int size(PlaneLayout layout, int width, int height) {
        var total = 0L;
        for (var plane = 0; plane < layout.planes(); plane++) {
            total = align(total, 512) + (long) stride(layout, plane, width) * layout.planeHeight(plane, height);
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a " + width + "×" + height + " " + layout + " picture is past 2 GiB");
        }
        return (int) total;
    }

    /// The bytes from one row of `plane` to the next, staged.
    static int stride(PlaneLayout layout, int plane, int width) {
        var row = (long) layout.planeWidth(plane, width)
                * layout.textureFormats().get(plane).bytesPerPixel();
        return (int) align(row, 256);
    }

    /// Where each plane starts, staged.
    static List<Integer> offsets(PlaneLayout layout, int width, int height) {
        var offsets = new ArrayList<Integer>(layout.planes());
        var at = 0L;
        for (var plane = 0; plane < layout.planes(); plane++) {
            at = align(at, 512);
            offsets.add((int) at);
            at += (long) stride(layout, plane, width) * layout.planeHeight(plane, height);
        }
        return offsets;
    }

    private static long align(long value, int to) {
        return (value + to - 1) / to * to;
    }

    /// How the planes are laid out.
    public PlaneLayout layout() {
        return layout;
    }

    /// Width in pixels.
    public int width() {
        return width;
    }

    /// Height in pixels.
    public int height() {
        return height;
    }

    /// Bytes from one row of plane `index` to the next.
    public int stride(int index) {
        return strides.get(index);
    }

    /// Plane `index`, little-endian, from its first row: a fresh view of the
    /// mapped memory each call, its limit the plane's end.
    ///
    /// @throws IllegalStateException when the memory is not mapped now: the
    ///                               layer is recording its upload, or the
    ///                               layer's device is gone
    public ByteBuffer plane(int index) {
        Objects.checkIndex(index, offsets.size());
        var lock = staging.lock();
        lock.lock();
        try {
            var current = mapping;
            if (current == null) {
                throw new IllegalStateException(this + " is not mapped now");
            }
            var length = strides.get(index) * layout.planeHeight(index, height);
            return current.slice(offsets.get(index), length).order(ByteOrder.LITTLE_ENDIAN);
        } finally {
            lock.unlock();
        }
    }

    /// Whether the planes can still be read and drawn: the layer's device is
    /// the one they were staged on, and they have not been released.
    public boolean isAlive() {
        var lock = staging.lock();
        lock.lock();
        try {
            return !buffer.isClosed() && staging.holds(this);
        } finally {
            lock.unlock();
        }
    }

    /// Gives the memory back to the staging. Idempotent; any thread.
    public void release() {
        staging.release(this);
    }

    /// Whether `staging` handed these out.
    boolean stagedBy(PlaneStaging staging) {
        return this.staging == staging;
    }

    UploadBuffer buffer() {
        return buffer;
    }

    List<Integer> offsets() {
        return offsets;
    }

    /// Sets the mapping planes are read through, or none. Under the lock.
    void mapping(@Nullable ByteBuffer mapped) {
        this.mapping = mapped;
    }

    @Override
    public String toString() {
        return "StagedPlanes[" + layout + " " + width + "×" + height + " in " + buffer + "]";
    }
}
