package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;

/// The fields of `AVFormatContext` the Engine reads and writes
/// ([FfmpegStructs#AV_FORMAT_CONTEXT]).
///
/// Static methods over a segment sized to the struct, rather than an object per
/// context: a view holds nothing a segment does not.
final class AvFormatContextView {

    private static final long PB = offset("pb");
    private static final long NB_STREAMS = offset("nb_streams");
    private static final long STREAMS = offset("streams");
    private static final long DURATION = offset("duration");

    private AvFormatContextView() {}

    /// `context`, sized to the struct.
    static MemorySegment of(MemorySegment context) {
        return Pointers.struct(context, FfmpegStructs.AV_FORMAT_CONTEXT);
    }

    /// Sets the custom I/O context. It must be set **before**
    /// `avformat_open_input`, which then marks the context
    /// `AVFMT_FLAG_CUSTOM_IO` and leaves the I/O context for its owner to free.
    static void pb(MemorySegment context, MemorySegment io) {
        context.set(ADDRESS, PB, io);
    }

    /// The number of streams. An `unsigned int` in C, and never near 2³¹.
    static int streamCount(MemorySegment context) {
        return context.get(JAVA_INT, NB_STREAMS);
    }

    /// The `index`th `AVStream*`, sized to the struct.
    static MemorySegment stream(MemorySegment context, int index) {
        var count = streamCount(context);
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("stream " + index + " of " + count);
        }
        var streams = Pointers.array(context.get(ADDRESS, STREAMS), ADDRESS, count);
        return AvStreamView.of(streams.getAtIndex(ADDRESS, index));
    }

    /// The whole presentation's duration in `AV_TIME_BASE` units, or
    /// `AV_NOPTS_VALUE`.
    static long duration(MemorySegment context) {
        return context.get(JAVA_LONG, DURATION);
    }

    private static long offset(String field) {
        return FfmpegStructs.AV_FORMAT_CONTEXT.byteOffset(groupElement(field));
    }
}
