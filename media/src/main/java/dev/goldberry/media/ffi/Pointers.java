package dev.goldberry.media.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/// The two restricted operations every read of FFmpeg's memory needs: giving a
/// pointer FFmpeg returned the size of the struct it points at, and reading a C
/// string.
///
/// Both are `reinterpret`, which trusts the caller about a size the JVM cannot
/// check. That is why they are here, in one place, where the size always comes
/// from a layout [FfmpegLayoutCheck] has verified.
final class Pointers {

    /// The longest C string read from FFmpeg: codec and format names, and error
    /// text. A string that long is a pointer to something that is not a string.
    private static final long MAX_STRING = 4096;

    private Pointers() {}

    /// `pointer`, sized to `layout`.
    ///
    /// @throws NullPointerException when `pointer` is null, which is a missing
    ///                              struct rather than an empty one
    @SuppressWarnings("restricted")
    static MemorySegment struct(MemorySegment pointer, StructLayout layout) {
        if (pointer.equals(MemorySegment.NULL)) {
            throw new NullPointerException("a null " + layout.name().orElse("struct") + " pointer");
        }
        return pointer.reinterpret(layout.byteSize());
    }

    /// `pointer`, sized to `count` elements of `element`: a C array.
    @SuppressWarnings("restricted")
    static MemorySegment array(MemorySegment pointer, MemoryLayout element, long count) {
        if (count == 0) {
            return MemorySegment.NULL;
        }
        if (pointer.equals(MemorySegment.NULL)) {
            throw new NullPointerException("a null array pointer with " + count + " elements");
        }
        return pointer.reinterpret(element.byteSize() * count);
    }

    /// Calls `free` with a pointer to a slot holding `pointer`: the shape of every
    /// FFmpeg function that frees an object and nulls the caller's reference to it
    /// (`av_packet_free(AVPacket **)`, `avcodec_free_context(AVCodecContext **)`).
    static void freeThrough(MemorySegment pointer, Consumer<MemorySegment> free) {
        if (pointer.equals(MemorySegment.NULL)) {
            return;
        }
        try (var arena = Arena.ofConfined()) {
            var slot = arena.allocate(ValueLayout.ADDRESS);
            slot.set(ValueLayout.ADDRESS, 0, pointer);
            free.accept(slot);
        }
    }

    /// The NUL-terminated UTF-8 string at `pointer`, or null for a null pointer.
    @SuppressWarnings("restricted")
    static @Nullable String string(MemorySegment pointer) {
        if (pointer.equals(MemorySegment.NULL)) {
            return null;
        }
        return pointer.reinterpret(MAX_STRING).getString(0);
    }
}
