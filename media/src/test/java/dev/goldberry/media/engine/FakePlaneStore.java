package dev.goldberry.media.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.picture.PlaneBuffer;
import dev.goldberry.media.picture.PlaneStore;

/// A [PlaneStore] of direct buffers, as many as it is given, with rows padded
/// to 256 bytes as a GPU's are: counts what it hands out and gets back, and can
/// be made to lose its memory, as a device does.
final class FakePlaneStore implements PlaneStore {

    /// One buffer of the store's.
    final class Buffer implements PlaneBuffer {
        private final PixelFormat format;
        private final int width;
        private final int height;
        private final List<ByteBuffer> planes = new ArrayList<>();
        private final int[] strides;
        private volatile boolean released;

        Buffer(PixelFormat format, int width, int height) {
            this.format = format;
            this.width = width;
            this.height = height;
            this.strides = new int[format.planes()];
            for (var plane = 0; plane < format.planes(); plane++) {
                strides[plane] = (format.planeRowBytes(plane, width) + 255) / 256 * 256;
                planes.add(ByteBuffer.allocateDirect(strides[plane] * format.planeRows(plane, height))
                        .order(ByteOrder.LITTLE_ENDIAN));
            }
        }

        @Override
        public PixelFormat format() {
            return format;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int stride(int index) {
            return strides[index];
        }

        @Override
        public ByteBuffer plane(int index) {
            if (lost) {
                throw new IllegalStateException("the store's memory is gone");
            }
            return planes.get(index).duplicate().order(ByteOrder.LITTLE_ENDIAN).clear();
        }

        @Override
        public void release() {
            if (!released) {
                released = true;
                returned.incrementAndGet();
                available.incrementAndGet();
            }
        }

        boolean released() {
            return released;
        }
    }

    final AtomicInteger available;
    final AtomicInteger handedOut = new AtomicInteger();
    final AtomicInteger returned = new AtomicInteger();
    volatile boolean lost;

    FakePlaneStore(int buffers) {
        this.available = new AtomicInteger(buffers);
    }

    @Override
    public Optional<PlaneBuffer> obtain(PixelFormat format, int width, int height) {
        if (lost || available.getAndUpdate(n -> Math.max(0, n - 1)) == 0) {
            return Optional.empty();
        }
        handedOut.incrementAndGet();
        return Optional.of(new Buffer(format, width, height));
    }
}
