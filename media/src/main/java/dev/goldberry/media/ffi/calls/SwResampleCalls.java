package dev.goldberry.media.ffi.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffi.FfmpegDowncalls;
import dev.goldberry.media.ffi.FfmpegLibrary;

/// The functions of `libswresample` the Engine calls: one conversion from
/// whatever a decoder produces to what the audio device plays.
///
/// See [FfmpegDowncalls] for why each handle is a `static final` constant.
public record SwResampleCalls(
        Version version,
        AllocSetOpts2 allocSetOpts2,
        Init init,
        Convert convert,
        GetOutSamples getOutSamples,
        Free free) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static SwResampleCalls bind(SymbolLookup lookup) {
        return new SwResampleCalls(
                new Version(lookup),
                new AllocSetOpts2(lookup),
                new Init(lookup),
                new Convert(lookup),
                new GetOutSamples(lookup),
                new Free(lookup));
    }

    /// `unsigned swresample_version(void)`
    public static final class Version {

        private static final MethodHandle FD_swresample_version = FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWRESAMPLE, "swresample_version");
        }

        public int call() {
            try {
                return (int) FD_swresample_version.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("swresample_version", t);
            }
        }
    }

    /// `int swr_alloc_set_opts2(struct SwrContext **ps,`
    /// `const AVChannelLayout *out_ch_layout, enum AVSampleFormat out_sample_fmt, int out_sample_rate,`
    /// `const AVChannelLayout *in_ch_layout, enum AVSampleFormat in_sample_fmt, int in_sample_rate,`
    /// `int log_offset, void *log_ctx)`
    public static final class AllocSetOpts2 {

        private static final MethodHandle FD_swr_alloc_set_opts2 = FfmpegDowncalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        AllocSetOpts2(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWRESAMPLE, "swr_alloc_set_opts2");
        }

        public int call(
                MemorySegment contextPointer,
                MemorySegment outLayout,
                int outFormat,
                int outRate,
                MemorySegment inLayout,
                int inFormat,
                int inRate,
                int logOffset,
                MemorySegment logContext) {
            try {
                return (int) FD_swr_alloc_set_opts2.invokeExact(
                        address,
                        contextPointer,
                        outLayout,
                        outFormat,
                        outRate,
                        inLayout,
                        inFormat,
                        inRate,
                        logOffset,
                        logContext);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("swr_alloc_set_opts2", t);
            }
        }
    }

    /// `int swr_init(struct SwrContext *s)`
    public static final class Init {

        private static final MethodHandle FD_swr_init = FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Init(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWRESAMPLE, "swr_init");
        }

        public int call(MemorySegment context) {
            try {
                return (int) FD_swr_init.invokeExact(address, context);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("swr_init", t);
            }
        }
    }

    /// `int swr_convert(struct SwrContext *s, uint8_t * const *out, int out_count,`
    /// `const uint8_t * const *in, int in_count)`
    ///
    /// Answers the samples per channel written. A null `in` flushes what the
    /// resampler holds back.
    public static final class Convert {

        private static final MethodHandle FD_swr_convert =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        Convert(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWRESAMPLE, "swr_convert");
        }

        public int call(MemorySegment context, MemorySegment out, int outCount, MemorySegment in, int inCount) {
            try {
                return (int) FD_swr_convert.invokeExact(address, context, out, outCount, in, inCount);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("swr_convert", t);
            }
        }
    }

    /// `int swr_get_out_samples(struct SwrContext *s, int in_samples)`
    ///
    /// An upper bound on the output of converting `in_samples`, for sizing the buffer.
    public static final class GetOutSamples {

        private static final MethodHandle FD_swr_get_out_samples =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetOutSamples(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWRESAMPLE, "swr_get_out_samples");
        }

        public int call(MemorySegment context, int inSamples) {
            try {
                return (int) FD_swr_get_out_samples.invokeExact(address, context, inSamples);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("swr_get_out_samples", t);
            }
        }
    }

    /// `void swr_free(struct SwrContext **s)`
    public static final class Free {

        private static final MethodHandle FD_swr_free = FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Free(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWRESAMPLE, "swr_free");
        }

        public void call(MemorySegment contextPointer) {
            try {
                FD_swr_free.invokeExact(address, contextPointer);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("swr_free", t);
            }
        }
    }
}
