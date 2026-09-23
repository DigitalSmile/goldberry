package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.MemorySegment;

/// The one field of `AVIOContext` the Engine reads ([FfmpegStructs#AV_IO_CONTEXT]).
///
/// `avio_alloc_context`'s documentation says the buffer it is handed "may be freed
/// and replaced with a new buffer by libavformat", so the buffer to free is
/// whichever one the context holds when it is freed, not the one that was passed
/// in.
final class AvIoContextView {

    private static final long BUFFER = FfmpegStructs.AV_IO_CONTEXT.byteOffset(groupElement("buffer"));

    private AvIoContextView() {}

    /// `context`, sized to the struct.
    static MemorySegment of(MemorySegment context) {
        return Pointers.struct(context, FfmpegStructs.AV_IO_CONTEXT);
    }

    /// The buffer the context holds now.
    static MemorySegment buffer(MemorySegment context) {
        return context.get(ADDRESS, BUFFER);
    }
}
