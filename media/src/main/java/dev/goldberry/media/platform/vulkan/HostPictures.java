package dev.goldberry.media.platform.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayDeque;
import java.util.Deque;

import dev.goldberry.media.codec.PixelFormat;

/// Decoded pictures copied out of the read-back buffer into memory of the
/// decoder's own, where they wait for their turn to be output: the read-back
/// buffer holds one picture, and a picture may wait behind several.
///
/// Buffers of one size are reused: a picture handed out is given back by the
/// next call into the decoder, which is as long as the SPI lends a frame. One
/// decoder's thread uses it.
public final class HostPictures implements AutoCloseable {

    /// One decoded picture in host memory: NV12 or P010, luma then chroma,
    /// rows `stride` bytes apart.
    ///
    /// @param format the frame contract's layout
    /// @param width  the visible width
    /// @param height the visible height
    /// @param stride bytes a row of either plane
    /// @param memory both planes, which go back to the pool together
    public record Picture(PixelFormat format, int width, int height, int stride, MemorySegment memory) {

        /// The luma plane.
        public MemorySegment luma() {
            return memory.asSlice(0, (long) stride * height);
        }

        /// The interleaved chroma plane.
        public MemorySegment chroma() {
            return memory.asSlice((long) stride * height, (long) stride * ((height + 1) / 2));
        }
    }

    private final Arena arena = Arena.ofShared();
    private final Deque<MemorySegment> free = new ArrayDeque<>();
    private long size;

    /// `planes`, `width` by `height` in `format`, copied out.
    public Picture copy(VideoSession.Planes planes, PixelFormat format, int width, int height) {
        var stride = planes.stride();
        var lumaBytes = (long) stride * height;
        var chromaBytes = (long) stride * ((height + 1) / 2);
        var needed = lumaBytes + chromaBytes;
        if (needed != size) {
            free.clear();
            size = needed;
        }
        var memory = free.pollFirst();
        if (memory == null) {
            memory = arena.allocate(needed, 64);
        }
        MemorySegment.copy(planes.luma(), 0, memory, 0, lumaBytes);
        MemorySegment.copy(planes.chroma(), 0, memory, lumaBytes, chromaBytes);
        return new Picture(format, width, height, stride, memory);
    }

    /// Takes a picture's memory back once it has been handed out and given up.
    public void recycle(Picture picture) {
        if (picture.memory().byteSize() == size) {
            free.addLast(picture.memory());
        }
    }

    @Override
    public void close() {
        free.clear();
        arena.close();
    }
}
