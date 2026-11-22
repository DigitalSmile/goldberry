package dev.goldberry.camera.backend;

import java.nio.ByteBuffer;

import dev.goldberry.media.picture.VideoPicture;

/// Three BGRA pictures' worth of memory, written in turn, so a picture handed
/// out keeps its bytes until two more have been handed out after it: the
/// borrowing rule [PlanePool] keeps for planes, for pictures decoded from
/// Motion JPEG and for mirrored ones.
///
/// [#next()] names the slot to write without taking it, and [#written] takes
/// it: a decode that fails between the two leaves every picture handed out as
/// it was. One thread writes; nothing is allocated after the pool is made.
public final class BgraPool {

    /// How many pictures the pool holds: the one shown, the one before it,
    /// and the one being written.
    public static final int SLOTS = 3;

    private final int width;
    private final int height;
    private final int stride;
    private final ByteBuffer[] pixels = new ByteBuffer[SLOTS];
    private int next;

    /// A pool for pictures `width`×`height`, rows packed four bytes a pixel.
    public BgraPool(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
        this.width = width;
        this.height = height;
        this.stride = Math.multiplyExact(width, 4);
        for (var slot = 0; slot < SLOTS; slot++) {
            pixels[slot] = ByteBuffer.allocateDirect(Math.multiplyExact(stride, height));
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

    /// Bytes from one row to the next.
    public int stride() {
        return stride;
    }

    /// Whether the pool holds pictures `width`×`height`.
    public boolean fits(int width, int height) {
        return this.width == width && this.height == height;
    }

    /// The memory of the slot to write next, from its first row; the same
    /// slot until [#written] takes it.
    public ByteBuffer next() {
        return pixels[next].duplicate().clear();
    }

    /// The slot [#next()] named, written, as an opaque picture; the next call
    /// to [#next()] names another.
    public VideoPicture written(long ptsNanos) {
        return written(ptsNanos, true);
    }

    /// The same, opaque or with premultiplied alpha.
    public VideoPicture written(long ptsNanos, boolean opaque) {
        var slot = next;
        next = (next + 1) % SLOTS;
        return new VideoPicture(width, height, stride, pixels[slot], ptsNanos, opaque);
    }
}
