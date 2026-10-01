package dev.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/// The Core Video a decoded picture is read through: a pixel buffer's format,
/// size and planes, locked for the CPU while the Engine reads them, and the
/// colour attachments the decoder set on it.
final class CoreVideo {

    /// `kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange`, `'420v'`: NV12, limited
    /// range.
    static final int NV12_VIDEO_RANGE = OsStatus.code("420v");
    /// `kCVPixelFormatType_420YpCbCr8BiPlanarFullRange`, `'420f'`: NV12, full
    /// range.
    static final int NV12_FULL_RANGE = OsStatus.code("420f");
    /// `kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange`, `'x420'`: P010,
    /// limited range.
    static final int P010_VIDEO_RANGE = OsStatus.code("x420");
    /// `kCVPixelFormatType_420YpCbCr10BiPlanarFullRange`, `'xf20'`: P010, full
    /// range.
    static final int P010_FULL_RANGE = OsStatus.code("xf20");

    /// `kCVPixelBufferLock_ReadOnly`.
    private static final long LOCK_READ_ONLY = 1;

    /// `CVReturn CVPixelBufferLockBaseAddress(CVPixelBufferRef pixelBuffer, CVPixelBufferLockFlags lockFlags)`
    private static final MethodHandle FD_CVPixelBufferLockBaseAddress =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));

    /// `CVReturn CVPixelBufferUnlockBaseAddress(CVPixelBufferRef pixelBuffer, CVPixelBufferLockFlags unlockFlags)`
    private static final MethodHandle FD_CVPixelBufferUnlockBaseAddress =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));

    /// `OSType CVPixelBufferGetPixelFormatType(CVPixelBufferRef pixelBuffer)`
    private static final MethodHandle FD_CVPixelBufferGetPixelFormatType =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `size_t CVPixelBufferGetWidth(CVPixelBufferRef pixelBuffer)`, and `…GetHeight`
    private static final MethodHandle FD_CVPixelBufferGetSize =
            Framework.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS));

    /// `void *CVPixelBufferGetBaseAddressOfPlane(CVPixelBufferRef pixelBuffer, size_t planeIndex)`
    private static final MethodHandle FD_CVPixelBufferGetBaseAddressOfPlane =
            Framework.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG));

    /// `size_t CVPixelBufferGetBytesPerRowOfPlane(CVPixelBufferRef pixelBuffer, size_t planeIndex)`, and
    /// `…GetHeightOfPlane`
    private static final MethodHandle FD_CVPixelBufferGetPlaneSize =
            Framework.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG));

    /// `CVPixelBufferRef CVPixelBufferRetain(CVPixelBufferRef texture)`
    private static final MethodHandle FD_CVPixelBufferRetain = Framework.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

    /// `void CVPixelBufferRelease(CVPixelBufferRef texture)`
    private static final MethodHandle FD_CVPixelBufferRelease = Framework.link(FunctionDescriptor.ofVoid(ADDRESS));

    /// `CFTypeRef CVBufferCopyAttachment(CVBufferRef buffer, CFStringRef key, CVAttachmentMode *attachmentMode)`
    private static final MethodHandle FD_CVBufferCopyAttachment =
            Framework.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private final CoreFoundation cf;
    private final MemorySegment lockBaseAddress;
    private final MemorySegment unlockBaseAddress;
    private final MemorySegment getPixelFormatType;
    private final MemorySegment getWidth;
    private final MemorySegment getHeight;
    private final MemorySegment getBaseAddressOfPlane;
    private final MemorySegment getBytesPerRowOfPlane;
    private final MemorySegment getHeightOfPlane;
    private final MemorySegment retain;
    private final MemorySegment release;
    /// `CVBufferCopyAttachment` arrived in macOS 12. Older systems have only the
    /// deprecated `CVBufferGetAttachment`, which returns without a reference.
    private final MemorySegment copyAttachment;

    private final boolean attachmentIsCopied;

    /// `kCVPixelBufferPixelFormatTypeKey`.
    final MemorySegment pixelFormatTypeKey;

    private final MemorySegment ycbcrMatrixKey;
    private final MemorySegment matrix709;
    private final MemorySegment matrix601;
    private final MemorySegment matrixSmpte240;
    private final Optional<MemorySegment> matrix2020;

    CoreVideo(SymbolLookup lookup, CoreFoundation cf) {
        this.cf = cf;
        var f = Framework.CORE_VIDEO;
        this.lockBaseAddress = f.symbol(lookup, "CVPixelBufferLockBaseAddress");
        this.unlockBaseAddress = f.symbol(lookup, "CVPixelBufferUnlockBaseAddress");
        this.getPixelFormatType = f.symbol(lookup, "CVPixelBufferGetPixelFormatType");
        this.getWidth = f.symbol(lookup, "CVPixelBufferGetWidth");
        this.getHeight = f.symbol(lookup, "CVPixelBufferGetHeight");
        this.getBaseAddressOfPlane = f.symbol(lookup, "CVPixelBufferGetBaseAddressOfPlane");
        this.getBytesPerRowOfPlane = f.symbol(lookup, "CVPixelBufferGetBytesPerRowOfPlane");
        this.getHeightOfPlane = f.symbol(lookup, "CVPixelBufferGetHeightOfPlane");
        this.retain = f.symbol(lookup, "CVPixelBufferRetain");
        this.release = f.symbol(lookup, "CVPixelBufferRelease");
        var copy = f.optionalSymbol(lookup, "CVBufferCopyAttachment");
        this.attachmentIsCopied = copy.isPresent();
        this.copyAttachment = copy.orElseGet(() -> f.symbol(lookup, "CVBufferGetAttachment"));
        this.pixelFormatTypeKey = f.constant(lookup, "kCVPixelBufferPixelFormatTypeKey");
        this.ycbcrMatrixKey = f.constant(lookup, "kCVImageBufferYCbCrMatrixKey");
        this.matrix709 = f.constant(lookup, "kCVImageBufferYCbCrMatrix_ITU_R_709_2");
        this.matrix601 = f.constant(lookup, "kCVImageBufferYCbCrMatrix_ITU_R_601_4");
        this.matrixSmpte240 = f.constant(lookup, "kCVImageBufferYCbCrMatrix_SMPTE_240M_1995");
        this.matrix2020 = f.optionalSymbol(lookup, "kCVImageBufferYCbCrMatrix_ITU_R_2020")
                .map(symbol -> f.constant(lookup, "kCVImageBufferYCbCrMatrix_ITU_R_2020"));
    }

    /// The YUV matrix a decoded picture says it was encoded with.
    enum Matrix {
        BT601,
        BT709,
        BT2020,
        /// The picture carries no matrix, or one the present path does not know.
        UNSPECIFIED,
    }

    /// Locks `buffer`'s planes for reading by the CPU.
    void lock(MemorySegment buffer) {
        try {
            OsStatus.check("CVPixelBufferLockBaseAddress", (int)
                    FD_CVPixelBufferLockBaseAddress.invokeExact(lockBaseAddress, buffer, LOCK_READ_ONLY));
        } catch (Throwable t) {
            throw rethrow("CVPixelBufferLockBaseAddress", t);
        }
    }

    /// Unlocks what [#lock] locked.
    void unlock(MemorySegment buffer) {
        try {
            OsStatus.check("CVPixelBufferUnlockBaseAddress", (int)
                    FD_CVPixelBufferUnlockBaseAddress.invokeExact(unlockBaseAddress, buffer, LOCK_READ_ONLY));
        } catch (Throwable t) {
            throw rethrow("CVPixelBufferUnlockBaseAddress", t);
        }
    }

    /// `buffer`'s pixel format, a four-character code.
    int pixelFormat(MemorySegment buffer) {
        try {
            return (int) FD_CVPixelBufferGetPixelFormatType.invokeExact(getPixelFormatType, buffer);
        } catch (Throwable t) {
            throw Framework.failure("CVPixelBufferGetPixelFormatType", t);
        }
    }

    /// `buffer`'s width in pixels.
    int width(MemorySegment buffer) {
        return size("CVPixelBufferGetWidth", getWidth, buffer);
    }

    /// `buffer`'s height in pixels.
    int height(MemorySegment buffer) {
        return size("CVPixelBufferGetHeight", getHeight, buffer);
    }

    /// The address of `plane` of a locked `buffer`.
    MemorySegment planeAddress(MemorySegment buffer, int plane) {
        try {
            return (MemorySegment)
                    FD_CVPixelBufferGetBaseAddressOfPlane.invokeExact(getBaseAddressOfPlane, buffer, (long) plane);
        } catch (Throwable t) {
            throw Framework.failure("CVPixelBufferGetBaseAddressOfPlane", t);
        }
    }

    /// The bytes from one row of `plane` to the next.
    int bytesPerRow(MemorySegment buffer, int plane) {
        return planeSize("CVPixelBufferGetBytesPerRowOfPlane", getBytesPerRowOfPlane, buffer, plane);
    }

    /// The rows of `plane`.
    int planeHeight(MemorySegment buffer, int plane) {
        return planeSize("CVPixelBufferGetHeightOfPlane", getHeightOfPlane, buffer, plane);
    }

    /// Takes a reference to `buffer`, which a decoder's callback only lends.
    void retain(MemorySegment buffer) {
        try {
            var _ = (MemorySegment) FD_CVPixelBufferRetain.invokeExact(retain, buffer);
        } catch (Throwable t) {
            throw Framework.failure("CVPixelBufferRetain", t);
        }
    }

    /// Gives back a reference taken by [#retain].
    void release(MemorySegment buffer) {
        if (buffer.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            FD_CVPixelBufferRelease.invokeExact(release, buffer);
        } catch (Throwable t) {
            throw Framework.failure("CVPixelBufferRelease", t);
        }
    }

    /// The YUV matrix attached to `buffer`.
    Matrix matrix(MemorySegment buffer) {
        MemorySegment value;
        try {
            value = (MemorySegment)
                    FD_CVBufferCopyAttachment.invokeExact(copyAttachment, buffer, ycbcrMatrixKey, MemorySegment.NULL);
        } catch (Throwable t) {
            throw Framework.failure("CVBufferCopyAttachment", t);
        }
        if (value.equals(MemorySegment.NULL)) {
            return Matrix.UNSPECIFIED;
        }
        try {
            if (cf.equal(value, matrix709) || cf.equal(value, matrixSmpte240)) {
                // SMPTE 240M is within a rounding of BT.709, and FFmpeg's
                // swscale treats it so.
                return Matrix.BT709;
            }
            if (cf.equal(value, matrix601)) {
                return Matrix.BT601;
            }
            if (matrix2020.isPresent() && cf.equal(value, matrix2020.get())) {
                return Matrix.BT2020;
            }
            return Matrix.UNSPECIFIED;
        } finally {
            if (attachmentIsCopied) {
                cf.release(value);
            }
        }
    }

    private static int size(String function, MemorySegment address, MemorySegment buffer) {
        try {
            return Math.toIntExact((long) FD_CVPixelBufferGetSize.invokeExact(address, buffer));
        } catch (Throwable t) {
            throw rethrow(function, t);
        }
    }

    private static int planeSize(String function, MemorySegment address, MemorySegment buffer, int plane) {
        try {
            return Math.toIntExact((long) FD_CVPixelBufferGetPlaneSize.invokeExact(address, buffer, (long) plane));
        } catch (Throwable t) {
            throw rethrow(function, t);
        }
    }

    private static RuntimeException rethrow(String function, Throwable t) {
        return t instanceof RuntimeException e ? e : Framework.failure(function, t);
    }
}
