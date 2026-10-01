package dev.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.List;

import dev.goldberry.media.codec.Frame;

/// The Core Media a VideoToolbox decoder is fed through: a format description
/// for the stream, and one sample buffer per packet.
final class CoreMedia {

    /// `CMTime`: `value / timescale` seconds.
    static final StructLayout CM_TIME = MemoryLayout.structLayout(
                    JAVA_LONG.withName("value"),
                    JAVA_INT.withName("timescale"),
                    JAVA_INT.withName("flags"),
                    JAVA_LONG.withName("epoch"))
            .withName("CMTime");

    /// `CMSampleTimingInfo`: a sample's duration, presentation and decoding time.
    static final StructLayout CM_SAMPLE_TIMING_INFO = MemoryLayout.structLayout(
                    CM_TIME.withName("duration"),
                    CM_TIME.withName("presentationTimeStamp"),
                    CM_TIME.withName("decodeTimeStamp"))
            .withName("CMSampleTimingInfo");

    /// The timescale times are passed in: nanoseconds, the unit the Engine
    /// already counts in. It fits the `int32_t` a timescale is.
    static final int NANOSECOND_TIMESCALE = 1_000_000_000;

    /// `kCMTimeFlags_Valid`.
    private static final int TIME_VALID = 1;

    /// `kCMBlockBufferAssureMemoryNowFlag`: allocate the block now, so the copy
    /// into it cannot fail for want of memory later.
    private static final int ASSURE_MEMORY_NOW = 1;

    private static final long TIME_VALUE = CM_TIME.byteOffset(MemoryLayout.PathElement.groupElement("value"));
    private static final long TIME_TIMESCALE = CM_TIME.byteOffset(MemoryLayout.PathElement.groupElement("timescale"));
    private static final long TIME_FLAGS = CM_TIME.byteOffset(MemoryLayout.PathElement.groupElement("flags"));
    private static final long TIMING_PTS =
            CM_SAMPLE_TIMING_INFO.byteOffset(MemoryLayout.PathElement.groupElement("presentationTimeStamp"));

    /// `OSStatus CMVideoFormatDescriptionCreateFromH264ParameterSets(CFAllocatorRef allocator,`
    /// `size_t parameterSetCount, const uint8_t *const *parameterSetPointers, const size_t *parameterSetSizes,`
    /// `int NALUnitHeaderLength, CMFormatDescriptionRef *formatDescriptionOut)`
    private static final MethodHandle FD_CMVideoFormatDescriptionCreateFromH264ParameterSets =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

    /// `OSStatus CMVideoFormatDescriptionCreateFromHEVCParameterSets(CFAllocatorRef allocator,`
    /// `size_t parameterSetCount, const uint8_t *const *parameterSetPointers, const size_t *parameterSetSizes,`
    /// `int NALUnitHeaderLength, CFDictionaryRef extensions, CMFormatDescriptionRef *formatDescriptionOut)`
    private static final MethodHandle FD_CMVideoFormatDescriptionCreateFromHEVCParameterSets = Framework.link(
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    /// `OSStatus CMBlockBufferCreateWithMemoryBlock(CFAllocatorRef structureAllocator, void *memoryBlock,`
    /// `size_t blockLength, CFAllocatorRef blockAllocator,`
    /// `const CMBlockBufferCustomBlockSource *customBlockSource,`
    /// `size_t offsetToData, size_t dataLength, CMBlockBufferFlags flags, CMBlockBufferRef *blockBufferOut)`
    private static final MethodHandle FD_CMBlockBufferCreateWithMemoryBlock = Framework.link(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_INT, ADDRESS));

    /// `OSStatus CMBlockBufferReplaceDataBytes(const void *sourceBytes, CMBlockBufferRef destinationBuffer,`
    /// `size_t offsetIntoDestination, size_t dataLength)`
    private static final MethodHandle FD_CMBlockBufferReplaceDataBytes =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG));

    /// `OSStatus CMSampleBufferCreateReady(CFAllocatorRef allocator, CMBlockBufferRef dataBuffer,`
    /// `CMFormatDescriptionRef formatDescription, CMItemCount numSamples, CMItemCount numSampleTimingEntries,`
    /// `const CMSampleTimingInfo *sampleTimingArray, CMItemCount numSampleSizeEntries,`
    /// `const size_t *sampleSizeArray, CMSampleBufferRef *sampleBufferOut)`
    private static final MethodHandle FD_CMSampleBufferCreateReady = Framework.link(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));

    private final CoreFoundation cf;
    private final MemorySegment cmVideoFormatDescriptionCreateFromH264ParameterSets;
    private final MemorySegment cmVideoFormatDescriptionCreateFromHevcParameterSets;
    private final MemorySegment cmBlockBufferCreateWithMemoryBlock;
    private final MemorySegment cmBlockBufferReplaceDataBytes;
    private final MemorySegment cmSampleBufferCreateReady;

    CoreMedia(SymbolLookup lookup, CoreFoundation cf) {
        this.cf = cf;
        var f = Framework.CORE_MEDIA;
        this.cmVideoFormatDescriptionCreateFromH264ParameterSets =
                f.symbol(lookup, "CMVideoFormatDescriptionCreateFromH264ParameterSets");
        this.cmVideoFormatDescriptionCreateFromHevcParameterSets =
                f.symbol(lookup, "CMVideoFormatDescriptionCreateFromHEVCParameterSets");
        this.cmBlockBufferCreateWithMemoryBlock = f.symbol(lookup, "CMBlockBufferCreateWithMemoryBlock");
        this.cmBlockBufferReplaceDataBytes = f.symbol(lookup, "CMBlockBufferReplaceDataBytes");
        this.cmSampleBufferCreateReady = f.symbol(lookup, "CMSampleBufferCreateReady");
    }

    /// A new video format description for an H.264 (`hevc` false) or HEVC stream,
    /// made from its `parameterSets` and the `nalLengthSize` of its packets.
    ///
    /// Core Media parses the parameter sets: the size, the crop, and the VUI's
    /// matrix and range, which the decoder then attaches to every picture. A
    /// description made from the configuration record alone carries none of the
    /// colour, and VideoToolbox guesses it from the picture's size.
    MemorySegment videoFormatDescription(boolean hevc, List<byte[]> parameterSets, int nalLengthSize) {
        var function = hevc
                ? "CMVideoFormatDescriptionCreateFromHEVCParameterSets"
                : "CMVideoFormatDescriptionCreateFromH264ParameterSets";
        try (var arena = Arena.ofConfined()) {
            var count = parameterSets.size();
            var pointers = arena.allocate(ADDRESS, count);
            var sizes = arena.allocate(JAVA_LONG, count);
            for (var i = 0; i < count; i++) {
                var set = parameterSets.get(i);
                pointers.setAtIndex(ADDRESS, i, arena.allocateFrom(JAVA_BYTE, set));
                sizes.setAtIndex(JAVA_LONG, i, set.length);
            }
            var out = arena.allocate(ADDRESS);
            var status = hevc
                    ? (int) FD_CMVideoFormatDescriptionCreateFromHEVCParameterSets.invokeExact(
                            cmVideoFormatDescriptionCreateFromHevcParameterSets,
                            MemorySegment.NULL,
                            (long) count,
                            pointers,
                            sizes,
                            nalLengthSize,
                            MemorySegment.NULL,
                            out)
                    : (int) FD_CMVideoFormatDescriptionCreateFromH264ParameterSets.invokeExact(
                            cmVideoFormatDescriptionCreateFromH264ParameterSets,
                            MemorySegment.NULL,
                            (long) count,
                            pointers,
                            sizes,
                            nalLengthSize,
                            out);
            OsStatus.check(function, status);
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw rethrow(function, t);
        }
    }

    /// A new sample buffer of one sample: a copy of `data`, described by `format`
    /// and shown at `ptsNanos` (or with no time, for [Frame#NO_PTS]).
    ///
    /// The bytes are copied into a block Core Media owns, so the sample buffer
    /// outlives the packet it came from, however long the decoder keeps it.
    MemorySegment sampleBuffer(MemorySegment data, MemorySegment format, long ptsNanos) {
        var block = MemorySegment.NULL;
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            var length = data.byteSize();
            var status = (int) FD_CMBlockBufferCreateWithMemoryBlock.invokeExact(
                    cmBlockBufferCreateWithMemoryBlock,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    length,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    0L,
                    length,
                    ASSURE_MEMORY_NOW,
                    out);
            OsStatus.check("CMBlockBufferCreateWithMemoryBlock", status);
            block = out.get(ADDRESS, 0);
            status = (int) FD_CMBlockBufferReplaceDataBytes.invokeExact(
                    cmBlockBufferReplaceDataBytes, data, block, 0L, length);
            OsStatus.check("CMBlockBufferReplaceDataBytes", status);

            var timing = arena.allocate(CM_SAMPLE_TIMING_INFO);
            setTime(timing.asSlice(TIMING_PTS, CM_TIME.byteSize()), ptsNanos);
            var size = arena.allocateFrom(JAVA_LONG, length);
            status = (int) FD_CMSampleBufferCreateReady.invokeExact(
                    cmSampleBufferCreateReady, MemorySegment.NULL, block, format, 1L, 1L, timing, 1L, size, out);
            OsStatus.check("CMSampleBufferCreateReady", status);
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw rethrow("CMSampleBufferCreateReady", t);
        } finally {
            // The sample buffer holds its own reference to the block.
            cf.release(block);
        }
    }

    /// Writes `nanos` into the `CMTime` at `time`: valid nanoseconds, or
    /// `kCMTimeInvalid` (all zero) for [Frame#NO_PTS].
    static void setTime(MemorySegment time, long nanos) {
        time.fill((byte) 0);
        if (nanos != Frame.NO_PTS) {
            time.set(JAVA_LONG, TIME_VALUE, nanos);
            time.set(JAVA_INT, TIME_TIMESCALE, NANOSECOND_TIMESCALE);
            time.set(JAVA_INT, TIME_FLAGS, TIME_VALID);
        }
    }

    /// The `CMTime` at `time` in nanoseconds, or [Frame#NO_PTS] when it is not a
    /// valid, finite time.
    static long nanos(MemorySegment time) {
        var flags = time.get(JAVA_INT, TIME_FLAGS);
        var timescale = time.get(JAVA_INT, TIME_TIMESCALE);
        // Valid, and none of the infinities or indefinite.
        if ((flags & 0x1D) != TIME_VALID || timescale <= 0) {
            return Frame.NO_PTS;
        }
        var value = time.get(JAVA_LONG, TIME_VALUE);
        if (timescale == NANOSECOND_TIMESCALE) {
            return value;
        }
        // Exact, like the Engine's own conversions: no double to round a picture
        // onto the other side of a boundary.
        return BigInteger.valueOf(value)
                .multiply(BigInteger.valueOf(NANOSECOND_TIMESCALE))
                .divide(BigInteger.valueOf(timescale))
                .longValue();
    }

    private static RuntimeException rethrow(String function, Throwable t) {
        return t instanceof RuntimeException e ? e : Framework.failure(function, t);
    }
}
