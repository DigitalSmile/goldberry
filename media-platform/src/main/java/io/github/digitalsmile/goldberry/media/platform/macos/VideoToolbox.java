package io.github.digitalsmile.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// VideoToolbox's decompression session: the operating system's H.264 and HEVC
/// decoders, on the media engine when the Mac has one and in software when not.
final class VideoToolbox {

    /// `kCMVideoCodecType_H264`, `'avc1'`.
    static final int CODEC_H264 = OsStatus.code("avc1");
    /// `kCMVideoCodecType_HEVC`, `'hvc1'`.
    static final int CODEC_HEVC = OsStatus.code("hvc1");

    /// `kVTDecodeInfo_FrameDropped`: the callback carries no picture.
    static final int INFO_FRAME_DROPPED = 1 << 1;

    /// `void (*VTDecompressionOutputCallback)(void *decompressionOutputRefCon, void *sourceFrameRefCon,`
    /// `OSStatus status, VTDecodeInfoFlags infoFlags, CVImageBufferRef imageBuffer,`
    /// `CMTime presentationTimeStamp, CMTime presentationDuration)`
    static final FunctionDescriptor OUTPUT_CALLBACK = FunctionDescriptor.ofVoid(
            ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, CoreMedia.CM_TIME, CoreMedia.CM_TIME);

    /// `VTDecompressionOutputCallbackRecord`: the callback and its first argument.
    static final StructLayout OUTPUT_CALLBACK_RECORD = MemoryLayout.structLayout(
                    ADDRESS.withName("decompressionOutputCallback"), ADDRESS.withName("decompressionOutputRefCon"))
            .withName("VTDecompressionOutputCallbackRecord");

    /// `OSStatus VTDecompressionSessionCreate(CFAllocatorRef allocator,`
    /// `CMVideoFormatDescriptionRef videoFormatDescription, CFDictionaryRef videoDecoderSpecification,`
    /// `CFDictionaryRef destinationImageBufferAttributes,`
    /// `const VTDecompressionOutputCallbackRecord *outputCallback,`
    /// `VTDecompressionSessionRef *decompressionSessionOut)`
    private static final MethodHandle FD_VTDecompressionSessionCreate =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /// `OSStatus VTDecompressionSessionDecodeFrame(VTDecompressionSessionRef session, CMSampleBufferRef sampleBuffer,`
    /// `VTDecodeFrameFlags decodeFlags, void *sourceFrameRefCon, VTDecodeInfoFlags *infoFlagsOut)`
    private static final MethodHandle FD_VTDecompressionSessionDecodeFrame =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    /// `OSStatus VTDecompressionSessionFinishDelayedFrames(VTDecompressionSessionRef session)`, and
    /// `…WaitForAsynchronousFrames`
    private static final MethodHandle FD_VTDecompressionSessionStatus =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `void VTDecompressionSessionInvalidate(VTDecompressionSessionRef session)`
    private static final MethodHandle FD_VTDecompressionSessionInvalidate =
            Framework.link(FunctionDescriptor.ofVoid(ADDRESS));

    /// `Boolean VTIsHardwareDecodeSupported(CMVideoCodecType codecType)`
    private static final MethodHandle FD_VTIsHardwareDecodeSupported =
            Framework.link(FunctionDescriptor.of(JAVA_BYTE, JAVA_INT));

    private final MemorySegment sessionCreate;
    private final MemorySegment decodeFrame;
    private final MemorySegment finishDelayedFrames;
    private final MemorySegment waitForAsynchronousFrames;
    private final MemorySegment invalidate;
    private final MemorySegment isHardwareDecodeSupported;

    VideoToolbox(SymbolLookup lookup) {
        var f = Framework.VIDEO_TOOLBOX;
        this.sessionCreate = f.symbol(lookup, "VTDecompressionSessionCreate");
        this.decodeFrame = f.symbol(lookup, "VTDecompressionSessionDecodeFrame");
        this.finishDelayedFrames = f.symbol(lookup, "VTDecompressionSessionFinishDelayedFrames");
        this.waitForAsynchronousFrames = f.symbol(lookup, "VTDecompressionSessionWaitForAsynchronousFrames");
        this.invalidate = f.symbol(lookup, "VTDecompressionSessionInvalidate");
        this.isHardwareDecodeSupported = f.symbol(lookup, "VTIsHardwareDecodeSupported");
    }

    /// A new session decoding `format` into pictures with `attributes` (a
    /// `CFDictionary`), each handed to `callbackRecord`'s callback.
    ///
    /// @throws OsStatus.Failure when the system has no decoder for the stream
    MemorySegment createSession(MemorySegment format, MemorySegment attributes, MemorySegment callbackRecord) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            var status = (int) FD_VTDecompressionSessionCreate.invokeExact(
                    sessionCreate, MemorySegment.NULL, format, MemorySegment.NULL, attributes, callbackRecord, out);
            OsStatus.check("VTDecompressionSessionCreate", status);
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : Framework.failure("VTDecompressionSessionCreate", t);
        }
    }

    /// Decodes `sample` synchronously: the callback has run for it when this
    /// returns, unless the decoder held the picture back. Answers the status
    /// rather than throwing, since one bad packet is not the end of a stream.
    int decode(MemorySegment session, MemorySegment sample, MemorySegment infoFlagsOut) {
        try {
            return (int) FD_VTDecompressionSessionDecodeFrame.invokeExact(
                    decodeFrame, session, sample, 0, MemorySegment.NULL, infoFlagsOut);
        } catch (Throwable t) {
            throw Framework.failure("VTDecompressionSessionDecodeFrame", t);
        }
    }

    /// Makes the session emit every picture it holds back, and waits for them.
    void finish(MemorySegment session) {
        try {
            OsStatus.check("VTDecompressionSessionFinishDelayedFrames", (int)
                    FD_VTDecompressionSessionStatus.invokeExact(finishDelayedFrames, session));
            OsStatus.check("VTDecompressionSessionWaitForAsynchronousFrames", (int)
                    FD_VTDecompressionSessionStatus.invokeExact(waitForAsynchronousFrames, session));
        } catch (Throwable t) {
            throw t instanceof RuntimeException e
                    ? e
                    : Framework.failure("VTDecompressionSessionFinishDelayedFrames", t);
        }
    }

    /// Tears the session down. It still has to be released.
    void invalidate(MemorySegment session) {
        try {
            FD_VTDecompressionSessionInvalidate.invokeExact(invalidate, session);
        } catch (Throwable t) {
            throw Framework.failure("VTDecompressionSessionInvalidate", t);
        }
    }

    /// Whether this Mac decodes `codecType` on its media engine rather than in
    /// software. Either decodes; this is for diagnostics.
    boolean hardwareDecodes(int codecType) {
        try {
            return (byte) FD_VTIsHardwareDecodeSupported.invokeExact(isHardwareDecodeSupported, codecType) != 0;
        } catch (Throwable t) {
            throw Framework.failure("VTIsHardwareDecodeSupported", t);
        }
    }
}
