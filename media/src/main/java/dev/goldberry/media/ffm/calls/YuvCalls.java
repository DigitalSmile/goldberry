package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;

/// The conversions of libyuv the CPU present path uses: planar and semi-planar
/// YUV in 8 and 10 bits to 32-bit BGRA (libyuv's "ARGB", which is the byte order
/// B, G, R, A in memory), each with a matrix handed in and chroma upsampled by the
/// filter named, and the subsamplings to 4:2:0. The matrices are libyuv's own
/// constants, found by name in `YuvConstants`; the filter is `kFilterBilinear`,
/// read from the layout file.
public record YuvCalls(
        I420ToArgbMatrixFilter i420ToArgbMatrixFilter,
        I010ToArgbMatrixFilter i010ToArgbMatrixFilter,
        P010ToArgbMatrixFilter p010ToArgbMatrixFilter,
        I420AlphaToArgbMatrixFilter i420AlphaToArgbMatrixFilter,
        SplitUvPlane splitUvPlane,
        I444ToI420 i444ToI420,
        I422ToI420 i422ToI420,
        I410ToI010 i410ToI010,
        I210ToI010 i210ToI010) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static YuvCalls bind(SymbolLookup lookup) {
        return new YuvCalls(
                new I420ToArgbMatrixFilter(lookup),
                new I010ToArgbMatrixFilter(lookup),
                new P010ToArgbMatrixFilter(lookup),
                new I420AlphaToArgbMatrixFilter(lookup),
                new SplitUvPlane(lookup),
                new I444ToI420(lookup),
                new I422ToI420(lookup),
                new I410ToI010(lookup),
                new I210ToI010(lookup));
    }

    /// `int I420ToARGBMatrixFilter(const uint8_t *src_y, int src_stride_y, const uint8_t *src_u,
    /// int src_stride_u, const uint8_t *src_v, int src_stride_v, uint8_t *dst_argb, int
    /// dst_stride_argb, const struct YuvConstants *yuvconstants, int width, int height, enum
    /// FilterMode filter)`
    ///
    /// `kFilterBilinear` interpolates each chroma sample at its centre, 9:3:3:1, which is what a
    /// GPU sampler does with the planes.
    public static final class I420ToArgbMatrixFilter {

        private static final MethodHandle FD_I420ToARGBMatrixFilter = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I420ToArgbMatrixFilter(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I420ToARGBMatrixFilter");
        }

        public int call(
                MemorySegment srcY,
                int strideY,
                MemorySegment srcU,
                int strideU,
                MemorySegment srcV,
                int strideV,
                MemorySegment dst,
                int dstStride,
                MemorySegment constants,
                int width,
                int height,
                int filter) {
            try {
                return (int) FD_I420ToARGBMatrixFilter.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, dst, dstStride, constants, width, height,
                        filter);
            } catch (Throwable t) {
                throw Downcalls.failure("I420ToARGBMatrixFilter", t);
            }
        }
    }

    /// `int I010ToARGBMatrixFilter(const uint16_t *src_y, int src_stride_y, const uint16_t *src_u,
    /// int src_stride_u, const uint16_t *src_v, int src_stride_v, uint8_t *dst_argb, int
    /// dst_stride_argb, const struct YuvConstants *yuvconstants, int width, int height, enum
    /// FilterMode filter)`
    ///
    /// The strides are in samples (16-bit elements), not bytes: libyuv advances a `uint16_t *` by
    /// them. A `VideoFrame` stride in bytes is halved on the way in.
    public static final class I010ToArgbMatrixFilter {

        private static final MethodHandle FD_I010ToARGBMatrixFilter = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I010ToArgbMatrixFilter(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I010ToARGBMatrixFilter");
        }

        public int call(
                MemorySegment srcY,
                int strideY,
                MemorySegment srcU,
                int strideU,
                MemorySegment srcV,
                int strideV,
                MemorySegment dst,
                int dstStride,
                MemorySegment constants,
                int width,
                int height,
                int filter) {
            try {
                return (int) FD_I010ToARGBMatrixFilter.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, dst, dstStride, constants, width, height,
                        filter);
            } catch (Throwable t) {
                throw Downcalls.failure("I010ToARGBMatrixFilter", t);
            }
        }
    }

    /// `int P010ToARGBMatrixFilter(const uint16_t *src_y, int src_stride_y, const uint16_t *src_uv,
    /// int src_stride_uv, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants
    /// *yuvconstants, int width, int height, enum FilterMode filter)`
    public static final class P010ToArgbMatrixFilter {

        private static final MethodHandle FD_P010ToARGBMatrixFilter = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT,
                JAVA_INT));

        private final MemorySegment address;

        P010ToArgbMatrixFilter(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "P010ToARGBMatrixFilter");
        }

        public int call(
                MemorySegment srcY,
                int strideY,
                MemorySegment srcUv,
                int strideUv,
                MemorySegment dst,
                int dstStride,
                MemorySegment constants,
                int width,
                int height,
                int filter) {
            try {
                return (int) FD_P010ToARGBMatrixFilter.invokeExact(
                        address, srcY, strideY, srcUv, strideUv, dst, dstStride, constants, width, height, filter);
            } catch (Throwable t) {
                throw Downcalls.failure("P010ToARGBMatrixFilter", t);
            }
        }
    }

    /// `int I420AlphaToARGBMatrixFilter(const uint8_t *src_y, int src_stride_y, const uint8_t
    /// *src_u, int src_stride_u, const uint8_t *src_v, int src_stride_v, const uint8_t *src_a, int
    /// src_stride_a, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants
    /// *yuvconstants, int width, int height, int attenuate, enum FilterMode filter)`
    ///
    /// `attenuate` 1 premultiplies the colour by the alpha on the way out.
    public static final class I420AlphaToArgbMatrixFilter {

        private static final MethodHandle FD_I420AlphaToARGBMatrixFilter = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I420AlphaToArgbMatrixFilter(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I420AlphaToARGBMatrixFilter");
        }

        public int call(
                MemorySegment srcY,
                int strideY,
                MemorySegment srcU,
                int strideU,
                MemorySegment srcV,
                int strideV,
                MemorySegment srcA,
                int strideA,
                MemorySegment dst,
                int dstStride,
                MemorySegment constants,
                int width,
                int height,
                int attenuate,
                int filter) {
            try {
                return (int) FD_I420AlphaToARGBMatrixFilter.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, srcA, strideA, dst, dstStride, constants,
                        width, height, attenuate, filter);
            } catch (Throwable t) {
                throw Downcalls.failure("I420AlphaToARGBMatrixFilter", t);
            }
        }
    }

    /// `void SplitUVPlane(const uint8_t *src_uv, int src_stride_uv, uint8_t *dst_u, int
    /// dst_stride_u, uint8_t *dst_v, int dst_stride_v, int width, int height)`
    ///
    /// NV12's interleaved chroma as two planes, `width` being the chroma width: libyuv has no
    /// filtered NV12 conversion, so NV12 goes through the I420 one.
    public static final class SplitUvPlane {

        private static final MethodHandle FD_SplitUVPlane = Downcalls.link(
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SplitUvPlane(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SplitUVPlane");
        }

        public void call(
                MemorySegment srcUv,
                int strideUv,
                MemorySegment dstU,
                int dstStrideU,
                MemorySegment dstV,
                int dstStrideV,
                int width,
                int height) {
            try {
                FD_SplitUVPlane.invokeExact(
                        address, srcUv, strideUv, dstU, dstStrideU, dstV, dstStrideV, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("SplitUVPlane", t);
            }
        }
    }

    /// `int I444ToI420(const uint8_t *src_y, int src_stride_y, const uint8_t *src_u, int
    /// src_stride_u, const uint8_t *src_v, int src_stride_v, uint8_t *dst_y, int dst_stride_y,
    /// uint8_t *dst_u, int dst_stride_u, uint8_t *dst_v, int dst_stride_v, int width, int height)`
    ///
    /// Subsamples 4:4:4 chroma to 4:2:0, for a picture the frame contract does not name.
    public static final class I444ToI420 {

        private static final MethodHandle FD_I444ToI420 = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I444ToI420(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I444ToI420");
        }

        public int call(
                MemorySegment srcY,
                int strideY,
                MemorySegment srcU,
                int strideU,
                MemorySegment srcV,
                int strideV,
                MemorySegment dstY,
                int dstStrideY,
                MemorySegment dstU,
                int dstStrideU,
                MemorySegment dstV,
                int dstStrideV,
                int width,
                int height) {
            try {
                return (int) FD_I444ToI420.invokeExact(
                        address,
                        srcY,
                        strideY,
                        srcU,
                        strideU,
                        srcV,
                        strideV,
                        dstY,
                        dstStrideY,
                        dstU,
                        dstStrideU,
                        dstV,
                        dstStrideV,
                        width,
                        height);
            } catch (Throwable t) {
                throw Downcalls.failure("I444ToI420", t);
            }
        }
    }

    /// `int I422ToI420(...the same shape as I444ToI420...)`
    public static final class I422ToI420 {

        private static final MethodHandle FD_I422ToI420 = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I422ToI420(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I422ToI420");
        }

        public int call(
                MemorySegment srcY,
                int strideY,
                MemorySegment srcU,
                int strideU,
                MemorySegment srcV,
                int strideV,
                MemorySegment dstY,
                int dstStrideY,
                MemorySegment dstU,
                int dstStrideU,
                MemorySegment dstV,
                int dstStrideV,
                int width,
                int height) {
            try {
                return (int) FD_I422ToI420.invokeExact(
                        address,
                        srcY,
                        strideY,
                        srcU,
                        strideU,
                        srcV,
                        strideV,
                        dstY,
                        dstStrideY,
                        dstU,
                        dstStrideU,
                        dstV,
                        dstStrideV,
                        width,
                        height);
            } catch (Throwable t) {
                throw Downcalls.failure("I422ToI420", t);
            }
        }
    }

    /// `int I410ToI010(const uint16_t *src_y, int src_stride_y, const uint16_t *src_u, int
    /// src_stride_u, const uint16_t *src_v, int src_stride_v, uint16_t *dst_y, int dst_stride_y,
    /// uint16_t *dst_u, int dst_stride_u, uint16_t *dst_v, int dst_stride_v, int width, int
    /// height)`
    ///
    /// The 10-bit 4:4:4 to 4:2:0 subsampling; strides in samples, as every 16-bit libyuv function
    /// counts them.
    public static final class I410ToI010 {

        private static final MethodHandle FD_I410ToI010 = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I410ToI010(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I410ToI010");
        }

        public int call(
                MemorySegment srcY,
                int strideY,
                MemorySegment srcU,
                int strideU,
                MemorySegment srcV,
                int strideV,
                MemorySegment dstY,
                int dstStrideY,
                MemorySegment dstU,
                int dstStrideU,
                MemorySegment dstV,
                int dstStrideV,
                int width,
                int height) {
            try {
                return (int) FD_I410ToI010.invokeExact(
                        address,
                        srcY,
                        strideY,
                        srcU,
                        strideU,
                        srcV,
                        strideV,
                        dstY,
                        dstStrideY,
                        dstU,
                        dstStrideU,
                        dstV,
                        dstStrideV,
                        width,
                        height);
            } catch (Throwable t) {
                throw Downcalls.failure("I410ToI010", t);
            }
        }
    }

    /// `int I210ToI010(...the same shape as I410ToI010...)`
    public static final class I210ToI010 {

        private static final MethodHandle FD_I210ToI010 = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I210ToI010(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I210ToI010");
        }

        public int call(
                MemorySegment srcY,
                int strideY,
                MemorySegment srcU,
                int strideU,
                MemorySegment srcV,
                int strideV,
                MemorySegment dstY,
                int dstStrideY,
                MemorySegment dstU,
                int dstStrideU,
                MemorySegment dstV,
                int dstStrideV,
                int width,
                int height) {
            try {
                return (int) FD_I210ToI010.invokeExact(
                        address,
                        srcY,
                        strideY,
                        srcU,
                        strideU,
                        srcV,
                        strideV,
                        dstY,
                        dstStrideY,
                        dstU,
                        dstStrideU,
                        dstV,
                        dstStrideV,
                        width,
                        height);
            } catch (Throwable t) {
                throw Downcalls.failure("I210ToI010", t);
            }
        }
    }
}
