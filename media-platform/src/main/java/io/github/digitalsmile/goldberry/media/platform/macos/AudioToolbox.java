package io.github.digitalsmile.goldberry.media.platform.macos;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// AudioToolbox's audio converter: the operating system's AAC, AC-3 and E-AC-3
/// decoders, with the channel remapping the frame contract needs done on the way
/// out.
///
/// The struct layouts are Core Audio's with natural alignment (the headers pack
/// nothing), and were measured against the SDK: `AudioStreamBasicDescription` 40
/// bytes, `AudioBufferList` of one buffer 24 with the buffer at 8,
/// `AudioFormatListItem` 48, `AudioFormatInfo` 56 with the cookie at 40.
final class AudioToolbox {

    /// `kAudioFormatMPEG4AAC`.
    static final int FORMAT_AAC = OsStatus.code("aac ");
    /// `kAudioFormatAC3`.
    static final int FORMAT_AC3 = OsStatus.code("ac-3");
    /// `kAudioFormatEnhancedAC3`.
    static final int FORMAT_EAC3 = OsStatus.code("ec-3");
    /// `kAudioFormatLinearPCM`.
    static final int FORMAT_LINEAR_PCM = OsStatus.code("lpcm");

    /// `kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked`: interleaved float
    /// samples with no padding, which is [io.github.digitalsmile.goldberry.media.codec.SampleFormat#F32].
    static final int FLAGS_PACKED_FLOAT = 1 | 8;

    /// `kAudioFormatProperty_FormatList`: every layer an AAC stream decodes to, the
    /// best first (HE-AAC's doubled rate over its core's).
    static final int PROPERTY_FORMAT_LIST = OsStatus.code("flst");
    /// `kAudioConverterDecompressionMagicCookie`.
    static final int PROPERTY_MAGIC_COOKIE = OsStatus.code("dmgc");
    /// `kAudioConverterOutputChannelLayout`.
    static final int PROPERTY_OUTPUT_CHANNEL_LAYOUT = OsStatus.code("ocl ");

    /// `kAudioChannelLayoutTag_UseChannelDescriptions`.
    static final int LAYOUT_TAG_USE_DESCRIPTIONS = 0;

    /// `AudioStreamBasicDescription`.
    static final StructLayout STREAM_DESCRIPTION = MemoryLayout.structLayout(
                    JAVA_DOUBLE.withName("mSampleRate"),
                    JAVA_INT.withName("mFormatID"),
                    JAVA_INT.withName("mFormatFlags"),
                    JAVA_INT.withName("mBytesPerPacket"),
                    JAVA_INT.withName("mFramesPerPacket"),
                    JAVA_INT.withName("mBytesPerFrame"),
                    JAVA_INT.withName("mChannelsPerFrame"),
                    JAVA_INT.withName("mBitsPerChannel"),
                    JAVA_INT.withName("mReserved"))
            .withName("AudioStreamBasicDescription");

    /// `AudioBufferList` holding exactly one `AudioBuffer`.
    static final StructLayout BUFFER_LIST = MemoryLayout.structLayout(
                    JAVA_INT.withName("mNumberBuffers"),
                    MemoryLayout.paddingLayout(4),
                    JAVA_INT.withName("mNumberChannels"),
                    JAVA_INT.withName("mDataByteSize"),
                    ADDRESS.withName("mData"))
            .withName("AudioBufferList");

    /// `AudioStreamPacketDescription`.
    static final StructLayout PACKET_DESCRIPTION = MemoryLayout.structLayout(
                    JAVA_LONG.withName("mStartOffset"),
                    JAVA_INT.withName("mVariableFramesInPacket"),
                    JAVA_INT.withName("mDataByteSize"))
            .withName("AudioStreamPacketDescription");

    /// `AudioFormatInfo`: what `kAudioFormatProperty_FormatList` is asked about,
    /// a stream description and its magic cookie.
    static final StructLayout FORMAT_INFO = MemoryLayout.structLayout(
                    STREAM_DESCRIPTION.withName("mASBD"),
                    ADDRESS.withName("mMagicCookie"),
                    JAVA_INT.withName("mMagicCookieSize"),
                    MemoryLayout.paddingLayout(4))
            .withName("AudioFormatInfo");

    /// `AudioFormatListItem`.
    static final StructLayout FORMAT_LIST_ITEM = MemoryLayout.structLayout(
                    STREAM_DESCRIPTION.withName("mASBD"),
                    JAVA_INT.withName("mChannelLayoutTag"),
                    MemoryLayout.paddingLayout(4))
            .withName("AudioFormatListItem");

    /// The bytes of an `AudioChannelLayout` before its channel descriptions, and
    /// of one `AudioChannelDescription`.
    static final long CHANNEL_LAYOUT_HEADER = 12;

    static final long CHANNEL_DESCRIPTION = 20;

    /// `OSStatus (*AudioConverterComplexInputDataProc)(AudioConverterRef inAudioConverter,`
    /// `UInt32 *ioNumberDataPackets, AudioBufferList *ioData,`
    /// `AudioStreamPacketDescription **outDataPacketDescription, void *inUserData)`
    static final FunctionDescriptor INPUT_PROC =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS);

    /// `OSStatus AudioFormatGetPropertyInfo(AudioFormatPropertyID inPropertyID, UInt32 inSpecifierSize,`
    /// `const void *inSpecifier, UInt32 *outPropertyDataSize)`
    private static final MethodHandle FD_AudioFormatGetPropertyInfo =
            Framework.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

    /// `OSStatus AudioFormatGetProperty(AudioFormatPropertyID inPropertyID, UInt32 inSpecifierSize,`
    /// `const void *inSpecifier, UInt32 *ioPropertyDataSize, void *outPropertyData)`
    private static final MethodHandle FD_AudioFormatGetProperty =
            Framework.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `OSStatus AudioConverterNew(const AudioStreamBasicDescription *inSourceFormat,`
    /// `const AudioStreamBasicDescription *inDestinationFormat, AudioConverterRef *outAudioConverter)`
    private static final MethodHandle FD_AudioConverterNew =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `OSStatus AudioConverterSetProperty(AudioConverterRef inAudioConverter, AudioConverterPropertyID inPropertyID,`
    /// `UInt32 inPropertyDataSize, const void *inPropertyData)`
    private static final MethodHandle FD_AudioConverterSetProperty =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

    /// `OSStatus AudioConverterFillComplexBuffer(AudioConverterRef inAudioConverter,`
    /// `AudioConverterComplexInputDataProc inInputDataProc, void *inInputDataProcUserData,`
    /// `UInt32 *ioOutputDataPacketSize, AudioBufferList *outOutputData,`
    /// `AudioStreamPacketDescription *outPacketDescription)`
    private static final MethodHandle FD_AudioConverterFillComplexBuffer =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /// `OSStatus AudioConverterReset(AudioConverterRef inAudioConverter)`, and `…Dispose`
    private static final MethodHandle FD_AudioConverterStatus =
            Framework.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private final MemorySegment formatGetPropertyInfo;
    private final MemorySegment formatGetProperty;
    private final MemorySegment converterNew;
    private final MemorySegment converterSetProperty;
    private final MemorySegment converterFillComplexBuffer;
    private final MemorySegment converterReset;
    private final MemorySegment converterDispose;

    AudioToolbox(SymbolLookup lookup) {
        var f = Framework.AUDIO_TOOLBOX;
        this.formatGetPropertyInfo = f.symbol(lookup, "AudioFormatGetPropertyInfo");
        this.formatGetProperty = f.symbol(lookup, "AudioFormatGetProperty");
        this.converterNew = f.symbol(lookup, "AudioConverterNew");
        this.converterSetProperty = f.symbol(lookup, "AudioConverterSetProperty");
        this.converterFillComplexBuffer = f.symbol(lookup, "AudioConverterFillComplexBuffer");
        this.converterReset = f.symbol(lookup, "AudioConverterReset");
        this.converterDispose = f.symbol(lookup, "AudioConverterDispose");
    }

    /// The layers a `formatId` stream with `cookie` decodes to
    /// (`kAudioFormatProperty_FormatList`), the best first, as
    /// `AudioFormatListItem`s in `arena`.
    MemorySegment formatList(Arena arena, int formatId, MemorySegment cookie) {
        try {
            var info = arena.allocate(FORMAT_INFO);
            info.set(JAVA_INT, FORMAT_INFO.byteOffset(groupElement("mASBD"), groupElement("mFormatID")), formatId);
            info.set(ADDRESS, FORMAT_INFO.byteOffset(groupElement("mMagicCookie")), cookie);
            info.set(JAVA_INT, FORMAT_INFO.byteOffset(groupElement("mMagicCookieSize")), (int) cookie.byteSize());
            var infoSize = (int) FORMAT_INFO.byteSize();
            var size = arena.allocate(JAVA_INT);
            OsStatus.check("AudioFormatGetPropertyInfo", (int) FD_AudioFormatGetPropertyInfo.invokeExact(
                    formatGetPropertyInfo, PROPERTY_FORMAT_LIST, infoSize, info, size));
            var items = arena.allocate(Math.max(size.get(JAVA_INT, 0), (int) FORMAT_LIST_ITEM.byteSize()), 8);
            OsStatus.check("AudioFormatGetProperty", (int) FD_AudioFormatGetProperty.invokeExact(
                    formatGetProperty, PROPERTY_FORMAT_LIST, infoSize, info, size, items));
            var count = size.get(JAVA_INT, 0) / FORMAT_LIST_ITEM.byteSize();
            if (count == 0) {
                throw new IllegalStateException("AudioToolbox lists no format for the stream's configuration");
            }
            return items.asSlice(0, count * FORMAT_LIST_ITEM.byteSize());
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : Framework.failure("AudioFormatGetProperty", t);
        }
    }

    /// A new converter from `source` to `destination`, both
    /// `AudioStreamBasicDescription`s.
    ///
    /// @throws OsStatus.Failure when the system decodes no such stream
    MemorySegment newConverter(MemorySegment source, MemorySegment destination) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            OsStatus.check("AudioConverterNew", (int)
                    FD_AudioConverterNew.invokeExact(converterNew, source, destination, out));
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : Framework.failure("AudioConverterNew", t);
        }
    }

    /// Sets `property` of `converter` to the bytes of `value`.
    void setProperty(MemorySegment converter, int property, MemorySegment value) {
        try {
            OsStatus.check("AudioConverterSetProperty " + OsStatus.fourCc(property), (int)
                    FD_AudioConverterSetProperty.invokeExact(
                            converterSetProperty, converter, property, (int) value.byteSize(), value));
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : Framework.failure("AudioConverterSetProperty", t);
        }
    }

    /// Decodes into `output` until it is full or `inputProc` has nothing more to
    /// give. `packets` holds the output's capacity in frames on the way in and
    /// what was written on the way out. Answers the status, which is the input
    /// procedure's own when it stopped the conversion.
    int fill(MemorySegment converter, MemorySegment inputProc, MemorySegment packets, MemorySegment output) {
        try {
            return (int) FD_AudioConverterFillComplexBuffer.invokeExact(
                    converterFillComplexBuffer,
                    converter,
                    inputProc,
                    MemorySegment.NULL,
                    packets,
                    output,
                    MemorySegment.NULL);
        } catch (Throwable t) {
            throw Framework.failure("AudioConverterFillComplexBuffer", t);
        }
    }

    /// Drops everything the converter holds: a seek.
    void reset(MemorySegment converter) {
        try {
            OsStatus.check("AudioConverterReset", (int) FD_AudioConverterStatus.invokeExact(converterReset, converter));
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : Framework.failure("AudioConverterReset", t);
        }
    }

    /// Frees the converter.
    void dispose(MemorySegment converter) {
        try {
            OsStatus.check(
                    "AudioConverterDispose", (int) FD_AudioConverterStatus.invokeExact(converterDispose, converter));
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : Framework.failure("AudioConverterDispose", t);
        }
    }
}
