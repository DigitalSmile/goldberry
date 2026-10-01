package dev.goldberry.media.ffi.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffi.FfmpegDowncalls;
import dev.goldberry.media.ffi.FfmpegLibrary;

/// The functions of `libswscale` the Engine calls: the version for the start-up
/// check, and the converter CPU present runs every picture through (phase 3).
///
/// The classic context API (`sws_getContext` → `sws_setColorspaceDetails` →
/// `sws_scale`) rather than `sws_scale_frame`. The frame API wants its source in
/// an `AVFrame`, and a picture from a
/// [dev.goldberry.media.codec.DecoderProvider] is a set of
/// plane pointers and strides that never was one. Wrapping it would mean filling
/// `AVFrame` fields this module otherwise never writes.
///
/// See [FfmpegDowncalls] for why each handle is a `static final` constant.
public record SwScaleCalls(
        Version version,
        GetContext getContext,
        FreeContext freeContext,
        Scale scale,
        SetColorspaceDetails setColorspaceDetails,
        GetCoefficients getCoefficients) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static SwScaleCalls bind(SymbolLookup lookup) {
        return new SwScaleCalls(
                new Version(lookup),
                new GetContext(lookup),
                new FreeContext(lookup),
                new Scale(lookup),
                new SetColorspaceDetails(lookup),
                new GetCoefficients(lookup));
    }

    /// `unsigned swscale_version(void)`
    public static final class Version {

        private static final MethodHandle FD_swscale_version = FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWSCALE, "swscale_version");
        }

        public int call() {
            try {
                return (int) FD_swscale_version.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("swscale_version", t);
            }
        }
    }

    /// `SwsContext *sws_getContext(int srcW, int srcH, enum AVPixelFormat srcFormat,
    /// int dstW, int dstH, enum AVPixelFormat dstFormat, int flags,
    /// SwsFilter *srcFilter, SwsFilter *dstFilter, const double *param)`
    ///
    /// Null when the conversion is not supported. The filters and the parameters
    /// are always null here: the defaults are what every conversion wants.
    public static final class GetContext {

        private static final MethodHandle FD_sws_getContext = FfmpegDowncalls.link(FunctionDescriptor.of(
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS,
                ADDRESS));

        private final MemorySegment address;

        GetContext(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWSCALE, "sws_getContext");
        }

        public MemorySegment call(
                int srcWidth, int srcHeight, int srcFormat, int dstWidth, int dstHeight, int dstFormat, int flags) {
            try {
                return (MemorySegment) FD_sws_getContext.invokeExact(
                        address,
                        srcWidth,
                        srcHeight,
                        srcFormat,
                        dstWidth,
                        dstHeight,
                        dstFormat,
                        flags,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("sws_getContext", t);
            }
        }
    }

    /// `void sws_freeContext(SwsContext *swsContext)`
    ///
    /// A null context is ignored.
    public static final class FreeContext {

        private static final MethodHandle FD_sws_freeContext = FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FreeContext(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWSCALE, "sws_freeContext");
        }

        public void call(MemorySegment context) {
            try {
                FD_sws_freeContext.invokeExact(address, context);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("sws_freeContext", t);
            }
        }
    }

    /// `int sws_scale(SwsContext *c, const uint8_t *const srcSlice[], const int
    /// srcStride[], int srcSliceY, int srcSliceH, uint8_t *const dst[], const int
    /// dstStride[])`
    ///
    /// Answers the height of the output slice, or a negative `AVERROR`.
    public static final class Scale {

        private static final MethodHandle FD_sws_scale = FfmpegDowncalls.link(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Scale(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWSCALE, "sws_scale");
        }

        public int call(
                MemorySegment context,
                MemorySegment srcPlanes,
                MemorySegment srcStrides,
                int sliceY,
                int sliceHeight,
                MemorySegment dstPlanes,
                MemorySegment dstStrides) {
            try {
                return (int) FD_sws_scale.invokeExact(
                        address, context, srcPlanes, srcStrides, sliceY, sliceHeight, dstPlanes, dstStrides);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("sws_scale", t);
            }
        }
    }

    /// `int sws_setColorspaceDetails(SwsContext *c, const int inv_table[4], int
    /// srcRange, const int table[4], int dstRange, int brightness, int contrast,
    /// int saturation)`
    ///
    /// The source matrix and range a frame was tagged with. Brightness, contrast
    /// and saturation are 16.16 fixed point, and the neutral values are `0`,
    /// `1 << 16` and `1 << 16`.
    public static final class SetColorspaceDetails {

        private static final MethodHandle FD_sws_setColorspaceDetails = FfmpegDowncalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetColorspaceDetails(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWSCALE, "sws_setColorspaceDetails");
        }

        public int call(
                MemorySegment context,
                MemorySegment inverseTable,
                int srcRange,
                MemorySegment table,
                int dstRange,
                int brightness,
                int contrast,
                int saturation) {
            try {
                return (int) FD_sws_setColorspaceDetails.invokeExact(
                        address, context, inverseTable, srcRange, table, dstRange, brightness, contrast, saturation);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("sws_setColorspaceDetails", t);
            }
        }
    }

    /// `const int *sws_getCoefficients(int colorspace)`
    ///
    /// A static table of four ints for one of the `SWS_CS_*` matrices, which
    /// [SetColorspaceDetails] takes. Never freed.
    public static final class GetCoefficients {

        private static final MethodHandle FD_sws_getCoefficients =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetCoefficients(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWSCALE, "sws_getCoefficients");
        }

        public MemorySegment call(int colorspace) {
            try {
                return (MemorySegment) FD_sws_getCoefficients.invokeExact(address, colorspace);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("sws_getCoefficients", t);
            }
        }
    }
}
