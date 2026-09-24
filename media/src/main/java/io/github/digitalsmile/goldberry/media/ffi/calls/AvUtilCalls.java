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
/// error strings, the log level, format names, frames, channel layouts, reading
/// a metadata dictionary, and hardware devices and the copy back from them
/// (phase 5).
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
        ChannelLayoutDefault channelLayoutDefault,
        DictGet dictGet,
        HwDeviceFindTypeByName hwDeviceFindTypeByName,
        HwDeviceCtxCreate hwDeviceCtxCreate,
        HwFrameTransferData hwFrameTransferData,
        FrameCopyProps frameCopyProps,
        BufferUnref bufferUnref) {

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
                new ChannelLayoutDefault(lookup),
                new DictGet(lookup),
                new HwDeviceFindTypeByName(lookup),
                new HwDeviceCtxCreate(lookup),
                new HwFrameTransferData(lookup),
                new FrameCopyProps(lookup),
                new BufferUnref(lookup));
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

    /// `AVDictionaryEntry *av_dict_get(const AVDictionary *m, const char *key,`
    /// `const AVDictionaryEntry *prev, int flags)`
    ///
    /// The entry named `key`, matched without regard to case with flags 0, or
    /// null. A null dictionary has no entries.
    public static final class DictGet {

        private static final MethodHandle FD_av_dict_get =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        DictGet(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_dict_get");
        }

        public MemorySegment call(MemorySegment dictionary, MemorySegment key, MemorySegment previous, int flags) {
            try {
                return (MemorySegment) FD_av_dict_get.invokeExact(address, dictionary, key, previous, flags);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_dict_get", t);
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

    /// `enum AVHWDeviceType av_hwdevice_find_type_by_name(const char *name)`
    ///
    /// The device type called `name` (`videotoolbox`, `d3d11va`, `vaapi`), or
    /// `AV_HWDEVICE_TYPE_NONE`. Asked by name so that no enum value is written
    /// down here.
    public static final class HwDeviceFindTypeByName {

        private static final MethodHandle FD_av_hwdevice_find_type_by_name =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        HwDeviceFindTypeByName(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_hwdevice_find_type_by_name");
        }

        public int call(MemorySegment name) {
            try {
                return (int) FD_av_hwdevice_find_type_by_name.invokeExact(address, name);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_hwdevice_find_type_by_name", t);
            }
        }
    }

    /// `int av_hwdevice_ctx_create(AVBufferRef **device_ctx, enum AVHWDeviceType type, const char *device, AVDictionary
    /// *opts, int flags)`
    ///
    /// Opens the system's default device of `type`. The reference written to
    /// `*device_ctx` is handed to the codec context, which unreferences it when
    /// it is freed.
    public static final class HwDeviceCtxCreate {

        private static final MethodHandle FD_av_hwdevice_ctx_create =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        HwDeviceCtxCreate(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_hwdevice_ctx_create");
        }

        public int call(MemorySegment deviceContext, int type, MemorySegment device, MemorySegment options, int flags) {
            try {
                return (int)
                        FD_av_hwdevice_ctx_create.invokeExact(address, deviceContext, type, device, options, flags);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_hwdevice_ctx_create", t);
            }
        }
    }

    /// `int av_hwframe_transfer_data(AVFrame *dst, const AVFrame *src, int flags)`
    ///
    /// Copy-back: a hardware surface into system memory. `dst` is empty, so
    /// FFmpeg allocates it in the device's preferred software format, which is
    /// NV12 for 8-bit and P010 for 10-bit video.
    public static final class HwFrameTransferData {

        private static final MethodHandle FD_av_hwframe_transfer_data =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        HwFrameTransferData(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_hwframe_transfer_data");
        }

        public int call(MemorySegment destination, MemorySegment source, int flags) {
            try {
                return (int) FD_av_hwframe_transfer_data.invokeExact(address, destination, source, flags);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_hwframe_transfer_data", t);
            }
        }
    }

    /// `int av_frame_copy_props(AVFrame *dst, const AVFrame *src)`
    ///
    /// The timestamps and colour tags, which a transfer does not copy.
    public static final class FrameCopyProps {

        private static final MethodHandle FD_av_frame_copy_props =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        FrameCopyProps(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_frame_copy_props");
        }

        public int call(MemorySegment destination, MemorySegment source) {
            try {
                return (int) FD_av_frame_copy_props.invokeExact(address, destination, source);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_frame_copy_props", t);
            }
        }
    }

    /// `void av_buffer_unref(AVBufferRef **buf)`
    ///
    /// Drops a device reference that was never handed to a codec context.
    public static final class BufferUnref {

        private static final MethodHandle FD_av_buffer_unref = FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        BufferUnref(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVUTIL, "av_buffer_unref");
        }

        public void call(MemorySegment buffer) {
            try {
                FD_av_buffer_unref.invokeExact(address, buffer);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_buffer_unref", t);
            }
        }
    }
}
