package dev.goldberry.media.ffi.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffi.FfmpegDowncalls;
import dev.goldberry.media.ffi.FfmpegLibrary;

/// The functions of `libavformat` the Engine calls: opening a demuxer over a
/// custom `AVIOContext`, reading packets, seeking, and listing demuxers.
///
/// No function that opens a URL itself is bound, and none could work: the library
/// is built with `--disable-network` and no protocols.
///
/// See [FfmpegDowncalls] for why each handle is a `static final` constant.
public record AvFormatCalls(
        Version version,
        AllocContext allocContext,
        OpenInput openInput,
        FindStreamInfo findStreamInfo,
        CloseInput closeInput,
        ReadFrame readFrame,
        SeekFile seekFile,
        DemuxerIterate demuxerIterate,
        IoAllocContext ioAllocContext,
        IoContextFree ioContextFree) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static AvFormatCalls bind(SymbolLookup lookup) {
        return new AvFormatCalls(
                new Version(lookup),
                new AllocContext(lookup),
                new OpenInput(lookup),
                new FindStreamInfo(lookup),
                new CloseInput(lookup),
                new ReadFrame(lookup),
                new SeekFile(lookup),
                new DemuxerIterate(lookup),
                new IoAllocContext(lookup),
                new IoContextFree(lookup));
    }

    /// `unsigned avformat_version(void)`
    public static final class Version {

        private static final MethodHandle FD_avformat_version = FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "avformat_version");
        }

        public int call() {
            try {
                return (int) FD_avformat_version.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avformat_version", t);
            }
        }
    }

    /// `AVFormatContext *avformat_alloc_context(void)`
    ///
    /// Allocated by the caller rather than inside `avformat_open_input`, because
    /// the custom `pb` has to be set on it **before** the open.
    public static final class AllocContext {

        private static final MethodHandle FD_avformat_alloc_context =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        AllocContext(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "avformat_alloc_context");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_avformat_alloc_context.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avformat_alloc_context", t);
            }
        }
    }

    /// `int avformat_open_input(AVFormatContext **ps, const char *url,`
    /// `const AVInputFormat *fmt, AVDictionary **options)`
    ///
    /// **On failure the context is freed and `*ps` set to null.** The custom I/O
    /// context is not freed, and stays the caller's to free.
    public static final class OpenInput {

        private static final MethodHandle FD_avformat_open_input =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        OpenInput(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "avformat_open_input");
        }

        public int call(MemorySegment contextPointer, MemorySegment url, MemorySegment format, MemorySegment options) {
            try {
                return (int) FD_avformat_open_input.invokeExact(address, contextPointer, url, format, options);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avformat_open_input", t);
            }
        }
    }

    /// `int avformat_find_stream_info(AVFormatContext *ic, AVDictionary **options)`
    ///
    /// Reads, and may decode, the first packets of each stream to fill in what the
    /// container header left out.
    public static final class FindStreamInfo {

        private static final MethodHandle FD_avformat_find_stream_info =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        FindStreamInfo(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "avformat_find_stream_info");
        }

        public int call(MemorySegment context, MemorySegment options) {
            try {
                return (int) FD_avformat_find_stream_info.invokeExact(address, context, options);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avformat_find_stream_info", t);
            }
        }
    }

    /// `void avformat_close_input(AVFormatContext **s)`
    ///
    /// Frees the context and sets `*s` to null. A custom I/O context
    /// (`AVFMT_FLAG_CUSTOM_IO`) is left alone.
    public static final class CloseInput {

        private static final MethodHandle FD_avformat_close_input =
                FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        CloseInput(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "avformat_close_input");
        }

        public void call(MemorySegment contextPointer) {
            try {
                FD_avformat_close_input.invokeExact(address, contextPointer);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avformat_close_input", t);
            }
        }
    }

    /// `int av_read_frame(AVFormatContext *s, AVPacket *pkt)`
    ///
    /// The next packet of any stream that is not discarded. The packet is
    /// reference-counted and belongs to the caller until `av_packet_free`.
    public static final class ReadFrame {

        private static final MethodHandle FD_av_read_frame =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReadFrame(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "av_read_frame");
        }

        public int call(MemorySegment context, MemorySegment packet) {
            try {
                return (int) FD_av_read_frame.invokeExact(address, context, packet);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_read_frame", t);
            }
        }
    }

    /// `int avformat_seek_file(AVFormatContext *s, int stream_index, int64_t min_ts,`
    /// `int64_t ts, int64_t max_ts, int flags)`
    ///
    /// With `stream_index` -1 the timestamps are in `AV_TIME_BASE` units. The
    /// demuxer lands on a keyframe within `[min_ts, max_ts]`, as near `ts` as it can.
    public static final class SeekFile {

        private static final MethodHandle FD_avformat_seek_file = FfmpegDowncalls.link(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT));

        private final MemorySegment address;

        SeekFile(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "avformat_seek_file");
        }

        public int call(
                MemorySegment context,
                int streamIndex,
                long minTimestamp,
                long timestamp,
                long maxTimestamp,
                int flags) {
            try {
                return (int) FD_avformat_seek_file.invokeExact(
                        address, context, streamIndex, minTimestamp, timestamp, maxTimestamp, flags);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avformat_seek_file", t);
            }
        }
    }

    /// `const AVInputFormat *av_demuxer_iterate(void **opaque)`
    ///
    /// Every demuxer compiled in, one per call, then null. `*opaque` must start null.
    public static final class DemuxerIterate {

        private static final MethodHandle FD_av_demuxer_iterate =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        DemuxerIterate(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "av_demuxer_iterate");
        }

        public MemorySegment call(MemorySegment opaque) {
            try {
                return (MemorySegment) FD_av_demuxer_iterate.invokeExact(address, opaque);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_demuxer_iterate", t);
            }
        }
    }

    /// `AVIOContext *avio_alloc_context(unsigned char *buffer, int buffer_size,`
    /// `int write_flag, void *opaque,`
    /// `int (*read_packet)(void *opaque, uint8_t *buf, int buf_size),`
    /// `int (*write_packet)(void *opaque, const uint8_t *buf, int buf_size),`
    /// `int64_t (*seek)(void *opaque, int64_t offset, int whence))`
    ///
    /// The buffer must come from `av_malloc`, and FFmpeg may replace it. A null
    /// `seek` makes the context unseekable.
    public static final class IoAllocContext {

        private static final MethodHandle FD_avio_alloc_context = FfmpegDowncalls.link(
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        IoAllocContext(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "avio_alloc_context");
        }

        public MemorySegment call(
                MemorySegment buffer,
                int bufferSize,
                int writeFlag,
                MemorySegment opaque,
                MemorySegment readPacket,
                MemorySegment writePacket,
                MemorySegment seek) {
            try {
                return (MemorySegment) FD_avio_alloc_context.invokeExact(
                        address, buffer, bufferSize, writeFlag, opaque, readPacket, writePacket, seek);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avio_alloc_context", t);
            }
        }
    }

    /// `void avio_context_free(AVIOContext **s)`
    ///
    /// Frees the context and sets `*s` to null. It does **not** free the buffer,
    /// which is why the Engine reads the context's `buffer` field first.
    public static final class IoContextFree {

        private static final MethodHandle FD_avio_context_free =
                FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        IoContextFree(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVFORMAT, "avio_context_free");
        }

        public void call(MemorySegment contextPointer) {
            try {
                FD_avio_context_free.invokeExact(address, contextPointer);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avio_context_free", t);
            }
        }
    }
}
