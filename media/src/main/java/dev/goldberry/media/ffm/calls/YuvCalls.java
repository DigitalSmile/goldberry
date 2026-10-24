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
/// B, G, R, A in memory), each with a matrix handed in, and a scaler. The matrices
/// are libyuv's own constants, found by name in `YuvConstants`.
public record YuvCalls(
        I420ToArgbMatrix i420ToArgbMatrix,
        I422ToArgbMatrix i422ToArgbMatrix,
        I444ToArgbMatrix i444ToArgbMatrix,
        I010ToArgbMatrix i010ToArgbMatrix,
        I210ToArgbMatrix i210ToArgbMatrix,
        I410ToArgbMatrix i410ToArgbMatrix,
        Nv12ToArgbMatrix nv12ToArgbMatrix,
        P010ToArgbMatrix p010ToArgbMatrix,
        I420AlphaToArgbMatrix i420AlphaToArgbMatrix,
        I010AlphaToArgbMatrix i010AlphaToArgbMatrix,
        I444ToI420 i444ToI420,
        I422ToI420 i422ToI420,
        I410ToI010 i410ToI010,
        I210ToI010 i210ToI010,
        ArgbScale argbScale) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static YuvCalls bind(SymbolLookup lookup) {
        return new YuvCalls(
                new I420ToArgbMatrix(lookup),
                new I422ToArgbMatrix(lookup),
                new I444ToArgbMatrix(lookup),
                new I010ToArgbMatrix(lookup),
                new I210ToArgbMatrix(lookup),
                new I410ToArgbMatrix(lookup),
                new Nv12ToArgbMatrix(lookup),
                new P010ToArgbMatrix(lookup),
                new I420AlphaToArgbMatrix(lookup),
                new I010AlphaToArgbMatrix(lookup),
                new I444ToI420(lookup),
                new I422ToI420(lookup),
                new I410ToI010(lookup),
                new I210ToI010(lookup),
                new ArgbScale(lookup));
    }

    /// `int I420ToARGBMatrix(const uint8_t *src_y, int src_stride_y, const uint8_t *src_u, int
    /// src_stride_u, const uint8_t *src_v, int src_stride_v, uint8_t *dst_argb, int
    /// dst_stride_argb, const struct YuvConstants *yuvconstants, int width, int height)`
    public static final class I420ToArgbMatrix {

        private static final MethodHandle FD_I420ToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                JAVA_INT));

        private final MemorySegment address;

        I420ToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I420ToARGBMatrix");
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
                int height) {
            try {
                return (int) FD_I420ToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, dst, dstStride, constants, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("I420ToARGBMatrix", t);
            }
        }
    }

    /// `int I422ToARGBMatrix(...the same shape as I420ToARGBMatrix...)`
    public static final class I422ToArgbMatrix {

        private static final MethodHandle FD_I422ToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                JAVA_INT));

        private final MemorySegment address;

        I422ToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I422ToARGBMatrix");
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
                int height) {
            try {
                return (int) FD_I422ToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, dst, dstStride, constants, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("I422ToARGBMatrix", t);
            }
        }
    }

    /// `int I444ToARGBMatrix(...the same shape as I420ToARGBMatrix...)`
    public static final class I444ToArgbMatrix {

        private static final MethodHandle FD_I444ToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                JAVA_INT));

        private final MemorySegment address;

        I444ToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I444ToARGBMatrix");
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
                int height) {
            try {
                return (int) FD_I444ToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, dst, dstStride, constants, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("I444ToARGBMatrix", t);
            }
        }
    }

    /// `int I010ToARGBMatrix(const uint16_t *src_y, int src_stride_y, const uint16_t *src_u, int
    /// src_stride_u, const uint16_t *src_v, int src_stride_v, uint8_t *dst_argb, int
    /// dst_stride_argb, const struct YuvConstants *yuvconstants, int width, int height)`
    ///
    /// The strides are in samples (16-bit elements), not bytes: libyuv advances a `uint16_t *` by
    /// them. A `VideoFrame` stride in bytes is halved on the way in.
    public static final class I010ToArgbMatrix {

        private static final MethodHandle FD_I010ToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                JAVA_INT));

        private final MemorySegment address;

        I010ToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I010ToARGBMatrix");
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
                int height) {
            try {
                return (int) FD_I010ToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, dst, dstStride, constants, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("I010ToARGBMatrix", t);
            }
        }
    }

    /// `int I210ToARGBMatrix(...the same shape as I010ToARGBMatrix...)`
    public static final class I210ToArgbMatrix {

        private static final MethodHandle FD_I210ToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                JAVA_INT));

        private final MemorySegment address;

        I210ToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I210ToARGBMatrix");
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
                int height) {
            try {
                return (int) FD_I210ToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, dst, dstStride, constants, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("I210ToARGBMatrix", t);
            }
        }
    }

    /// `int I410ToARGBMatrix(...the same shape as I010ToARGBMatrix...)`
    public static final class I410ToArgbMatrix {

        private static final MethodHandle FD_I410ToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                JAVA_INT));

        private final MemorySegment address;

        I410ToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I410ToARGBMatrix");
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
                int height) {
            try {
                return (int) FD_I410ToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, dst, dstStride, constants, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("I410ToARGBMatrix", t);
            }
        }
    }

    /// `int NV12ToARGBMatrix(const uint8_t *src_y, int src_stride_y, const uint8_t *src_uv, int
    /// src_stride_uv, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants
    /// *yuvconstants, int width, int height)`
    public static final class Nv12ToArgbMatrix {

        private static final MethodHandle FD_NV12ToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        Nv12ToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "NV12ToARGBMatrix");
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
                int height) {
            try {
                return (int) FD_NV12ToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcUv, strideUv, dst, dstStride, constants, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("NV12ToARGBMatrix", t);
            }
        }
    }

    /// `int P010ToARGBMatrix(const uint16_t *src_y, int src_stride_y, const uint16_t *src_uv, int
    /// src_stride_uv, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants
    /// *yuvconstants, int width, int height)`
    public static final class P010ToArgbMatrix {

        private static final MethodHandle FD_P010ToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        P010ToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "P010ToARGBMatrix");
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
                int height) {
            try {
                return (int) FD_P010ToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcUv, strideUv, dst, dstStride, constants, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("P010ToARGBMatrix", t);
            }
        }
    }

    /// `int I420AlphaToARGBMatrix(const uint8_t *src_y, int src_stride_y, const uint8_t *src_u, int
    /// src_stride_u, const uint8_t *src_v, int src_stride_v, const uint8_t *src_a, int
    /// src_stride_a, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants
    /// *yuvconstants, int width, int height, int attenuate)`
    ///
    /// `attenuate` 1 premultiplies the colour by the alpha on the way out.
    public static final class I420AlphaToArgbMatrix {

        private static final MethodHandle FD_I420AlphaToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I420AlphaToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I420AlphaToARGBMatrix");
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
                int attenuate) {
            try {
                return (int) FD_I420AlphaToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, srcA, strideA, dst, dstStride, constants,
                        width, height, attenuate);
            } catch (Throwable t) {
                throw Downcalls.failure("I420AlphaToARGBMatrix", t);
            }
        }
    }

    /// `int I010AlphaToARGBMatrix(...the same shape as I420AlphaToARGBMatrix, with uint16_t
    /// planes...)`
    public static final class I010AlphaToArgbMatrix {

        private static final MethodHandle FD_I010AlphaToARGBMatrix = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT,
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        I010AlphaToArgbMatrix(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "I010AlphaToARGBMatrix");
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
                int attenuate) {
            try {
                return (int) FD_I010AlphaToARGBMatrix.invokeExact(
                        address, srcY, strideY, srcU, strideU, srcV, strideV, srcA, strideA, dst, dstStride, constants,
                        width, height, attenuate);
            } catch (Throwable t) {
                throw Downcalls.failure("I010AlphaToARGBMatrix", t);
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

    /// `int ARGBScale(const uint8_t *src_argb, int src_stride_argb, int src_width, int src_height,
    /// uint8_t *dst_argb, int dst_stride_argb, int dst_width, int dst_height, enum FilterMode
    /// filtering)`
    public static final class ArgbScale {

        private static final MethodHandle FD_ARGBScale = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        ArgbScale(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "ARGBScale");
        }

        public int call(
                MemorySegment src,
                int srcStride,
                int srcWidth,
                int srcHeight,
                MemorySegment dst,
                int dstStride,
                int dstWidth,
                int dstHeight,
                int filtering) {
            try {
                return (int) FD_ARGBScale.invokeExact(
                        address, src, srcStride, srcWidth, srcHeight, dst, dstStride, dstWidth, dstHeight, filtering);
            } catch (Throwable t) {
                throw Downcalls.failure("ARGBScale", t);
            }
        }
    }
}
