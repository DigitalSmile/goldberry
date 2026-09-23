package io.github.digitalsmile.goldberry.media.ffi.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.media.ffi.FfmpegDowncalls;
import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibrary;

/// The functions of `libavutil` the Engine calls: the version, the allocator,
/// error strings, the log level, format names, frames, and channel layouts.
///
/// See [FfmpegDowncalls] for why each handle is a `static final` constant.
public record AvUtilCalls(
        Version version,
        Malloc malloc,
        Free free,
        StrError strError,
        LogSetLevel logSetLevel,
        PixFmtName pixFmtName,
        SampleFmtName sampleFmtName,
        FrameAlloc frameAlloc,
        FrameFree frameFree,
        FrameUnref frameUnref,
        ChannelLayoutDefault channelLayoutDefault) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static AvUtilCalls bind(SymbolLookup lookup) {
        return new AvUtilCalls(
                new Version(lookup),
                new Malloc(lookup),
                new Free(lookup),
                new StrError(lookup),
                new LogSetLevel(lookup),
                new PixFmtName(lookup),
                new SampleFmtName(lookup),
                new FrameAlloc(lookup),
                new FrameFree(lookup),
                new FrameUnref(lookup),
                new ChannelLayoutDefault(lookup));
    }

    /// `unsigned avutil_version(void)`
    ///
    /// Answers `major << 16 | minor << 8 | micro`.
    public static final class Version {

        private static final MethodHandle FD_avutil_version = FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "avutil_version");
        }

        public int call() {
            try {
                return (int) FD_avutil_version.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avutil_version", t);
            }
        }
    }

    /// `void *av_malloc(size_t size)`
    ///
    /// The allocator every buffer FFmpeg may later free or reallocate has to come
    /// from, such as the one given to `avio_alloc_context`. `size_t` is the
    /// pointer-width scalar on every target here. Answers null when out of memory.
    public static final class Malloc {

        private static final MethodHandle FD_av_malloc =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, JAVA_LONG));

        private final MemorySegment address;

        Malloc(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_malloc");
        }

        public MemorySegment call(long size) {
            try {
                return (MemorySegment) FD_av_malloc.invokeExact(address, size);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_malloc", t);
            }
        }
    }

    /// `void av_free(void *ptr)`
    public static final class Free {

        private static final MethodHandle FD_av_free = FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Free(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_free");
        }

        public void call(MemorySegment pointer) {
            try {
                FD_av_free.invokeExact(address, pointer);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_free", t);
            }
        }
    }

    /// `int av_strerror(int errnum, char *errbuf, size_t errbuf_size)`
    public static final class StrError {

        private static final MethodHandle FD_av_strerror =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG));

        private final MemorySegment address;

        StrError(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_strerror");
        }

        public int call(int error, MemorySegment buffer, long size) {
            try {
                return (int) FD_av_strerror.invokeExact(address, error, buffer, size);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_strerror", t);
            }
        }
    }

    /// `void av_log_set_level(int level)`
    ///
    /// The only logging control used. `av_log_set_callback` would need a variadic
    /// upcall, which FFM cannot express, so FFmpeg's own log is quieted and the
    /// Engine reports failures from return codes instead (`docs/goldberry-media.md` §10).
    public static final class LogSetLevel {

        private static final MethodHandle FD_av_log_set_level =
                FfmpegDowncalls.link(FunctionDescriptor.ofVoid(JAVA_INT));

        private final MemorySegment address;

        LogSetLevel(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_log_set_level");
        }

        public void call(int level) {
            try {
                FD_av_log_set_level.invokeExact(address, level);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_log_set_level", t);
            }
        }
    }

    /// `const char *av_get_pix_fmt_name(enum AVPixelFormat pix_fmt)`
    ///
    /// A static string, or null for an unknown format.
    public static final class PixFmtName {

        private static final MethodHandle FD_av_get_pix_fmt_name =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        PixFmtName(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_get_pix_fmt_name");
        }

        public MemorySegment call(int format) {
            try {
                return (MemorySegment) FD_av_get_pix_fmt_name.invokeExact(address, format);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_get_pix_fmt_name", t);
            }
        }
    }

    /// `const char *av_get_sample_fmt_name(enum AVSampleFormat sample_fmt)`
    ///
    /// A static string, or null for an unknown format.
    public static final class SampleFmtName {

        private static final MethodHandle FD_av_get_sample_fmt_name =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        SampleFmtName(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_get_sample_fmt_name");
        }

        public MemorySegment call(int format) {
            try {
                return (MemorySegment) FD_av_get_sample_fmt_name.invokeExact(address, format);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_get_sample_fmt_name", t);
            }
        }
    }

    /// `AVFrame *av_frame_alloc(void)`
    ///
    /// Allocates a frame shell, with no data. Null when out of memory.
    public static final class FrameAlloc {

        private static final MethodHandle FD_av_frame_alloc = FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        FrameAlloc(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_frame_alloc");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_av_frame_alloc.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_frame_alloc", t);
            }
        }
    }

    /// `void av_frame_free(AVFrame **frame)`
    ///
    /// Unreferences and frees the frame, and sets `*frame` to null.
    public static final class FrameFree {

        private static final MethodHandle FD_av_frame_free = FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FrameFree(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_frame_free");
        }

        public void call(MemorySegment framePointer) {
            try {
                FD_av_frame_free.invokeExact(address, framePointer);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_frame_free", t);
            }
        }
    }

    /// `void av_frame_unref(AVFrame *frame)`
    ///
    /// Releases the frame's buffers, keeping the shell for the next decode.
    public static final class FrameUnref {

        private static final MethodHandle FD_av_frame_unref = FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FrameUnref(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_frame_unref");
        }

        public void call(MemorySegment frame) {
            try {
                FD_av_frame_unref.invokeExact(address, frame);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_frame_unref", t);
            }
        }
    }

    /// `void av_channel_layout_default(AVChannelLayout *ch_layout, int nb_channels)`
    ///
    /// FFmpeg's default layout for a channel count: what [io.github.digitalsmile.goldberry.media.codec.AudioFrame]
    /// promises its channels are in.
    public static final class ChannelLayoutDefault {

        private static final MethodHandle FD_av_channel_layout_default =
                FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ChannelLayoutDefault(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_channel_layout_default");
        }

        public void call(MemorySegment layout, int channels) {
            try {
                FD_av_channel_layout_default.invokeExact(address, layout, channels);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_channel_layout_default", t);
            }
        }
    }
}
