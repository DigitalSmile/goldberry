package dev.goldberry.camera.backend;

import java.nio.ByteBuffer;
import java.util.List;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.VideoPlanes;

/// Three NV12 pictures' worth of memory, written in turn, so a picture handed
/// out keeps its bytes until two more have been handed out after it: the
/// borrowing rule every picture source keeps.
///
/// One thread writes, the camera's; nothing is allocated after the pool is
/// made.
public final class PlanePool {

    /// How many pictures the pool holds: the one shown, the one before it,
    /// and the one being written.
    public static final int SLOTS = 3;

    private final int width;
    private final int height;
    private final int lumaStride;
    private final int chromaStride;
    private final ByteBuffer[] luma = new ByteBuffer[SLOTS];
    private final ByteBuffer[] chroma = new ByteBuffer[SLOTS];
    private int next;

    /// A pool for pictures `width`×`height`.
    public PlanePool(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
        this.width = width;
        this.height = height;
        this.lumaStride = PixelFormat.NV12.planeRowBytes(0, width);
        this.chromaStride = PixelFormat.NV12.planeRowBytes(1, width);
        for (var slot = 0; slot < SLOTS; slot++) {
            luma[slot] = ByteBuffer.allocateDirect(lumaStride * PixelFormat.NV12.planeRows(0, height));
            chroma[slot] = ByteBuffer.allocateDirect(chromaStride * PixelFormat.NV12.planeRows(1, height));
        }
    }

    /// Pixels across.
    public int width() {
        return width;
    }

    /// Pixels down.
    public int height() {
        return height;
    }

    /// Copies `frame` into the next slot and hands it out as a picture.
    ///
    /// @throws IllegalArgumentException when the frame is not this pool's size
    public VideoPlanes copy(Nv12Frame frame, long ptsNanos) {
        if (frame.width() != width || frame.height() != height) {
            throw new IllegalArgumentException(
                    "a " + frame.width() + "×" + frame.height() + " frame in a " + width + "×" + height + " pool");
        }
        var slot = take();
        copyRows(frame.bytes(), frame.lumaOffset(), frame.lumaStride(), luma[slot], lumaStride, lumaStride, height);
        copyRows(
                frame.bytes(),
                frame.chromaOffset(),
                frame.chromaStride(),
                chroma[slot],
                chromaStride,
                chromaStride,
                PixelFormat.NV12.planeRows(1, height));
        return picture(slot, frame.matrix(), frame.fullRange(), ptsNanos);
    }

    /// The next slot's planes, to be written in place and then handed out with
    /// [#written]: a synthetic camera paints into them.
    public Slot next() {
        var slot = take();
        return new Slot(
                slot,
                luma[slot].duplicate().clear(),
                lumaStride,
                chroma[slot].duplicate().clear(),
                chromaStride);
    }

    /// `slot`, written, as a picture.
    public VideoPlanes written(Slot slot, VideoFrame.ColorMatrix matrix, boolean fullRange, long ptsNanos) {
        return picture(slot.index(), matrix, fullRange, ptsNanos);
    }

    /// One slot's planes, writable.
    ///
    /// @param index        which slot
    /// @param luma         the luma plane, from its first row
    /// @param lumaStride   bytes a luma row
    /// @param chroma       the interleaved chroma plane
    /// @param chromaStride bytes a chroma row
    public record Slot(int index, ByteBuffer luma, int lumaStride, ByteBuffer chroma, int chromaStride) {}

    private int take() {
        var slot = next;
        next = (next + 1) % SLOTS;
        return slot;
    }

    private VideoPlanes picture(int slot, VideoFrame.ColorMatrix matrix, boolean fullRange, long ptsNanos) {
        return new VideoPlanes(
                PixelFormat.NV12,
                width,
                height,
                List.of(luma[slot], chroma[slot]),
                List.of(lumaStride, chromaStride),
                matrix,
                fullRange,
                ptsNanos);
    }

    /// `rows` rows of `rowBytes` from `from` at `offset`, `fromStride` apart,
    /// into `to`, `toStride` apart.
    static void copyRows(
            ByteBuffer from, int offset, int fromStride, ByteBuffer to, int toStride, int rowBytes, int rows) {
        for (var row = 0; row < rows; row++) {
            to.put(row * toStride, from, offset + row * fromStride, rowBytes);
        }
    }
}
