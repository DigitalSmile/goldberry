package dev.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;

/// The fields of `AVCodecParameters` the Engine reads
/// ([FfmpegStructs#AV_CODEC_PARAMETERS]).
///
/// Reads what describes a track and what a provider's decoder needs. The colour
/// fields are in the layout and verified with it, but the present path reads
/// colour from each decoded frame instead, which is what a decoder actually
/// produced (`AvFrameView`).
final class AvCodecParametersView {

    private static final long CODEC_TYPE = offset("codec_type");
    private static final long CODEC_ID = offset("codec_id");
    private static final long EXTRADATA = offset("extradata");
    private static final long EXTRADATA_SIZE = offset("extradata_size");
    private static final long FORMAT = offset("format");
    private static final long BIT_RATE = offset("bit_rate");
    private static final long PROFILE = offset("profile");
    private static final long LEVEL = offset("level");
    private static final long WIDTH = offset("width");
    private static final long HEIGHT = offset("height");
    private static final long NB_CHANNELS =
            FfmpegStructs.AV_CODEC_PARAMETERS.byteOffset(groupElement("ch_layout"), groupElement("nb_channels"));
    private static final long SAMPLE_RATE = offset("sample_rate");

    private AvCodecParametersView() {}

    /// `parameters`, sized to the struct.
    static MemorySegment of(MemorySegment parameters) {
        return Pointers.struct(parameters, FfmpegStructs.AV_CODEC_PARAMETERS);
    }

    /// `AVMEDIA_TYPE_*`.
    static int codecType(MemorySegment parameters) {
        return parameters.get(JAVA_INT, CODEC_TYPE);
    }

    /// The `AVCodecID`. It is read only to be named: see [Ffmpeg#codecName].
    static int codecId(MemorySegment parameters) {
        return parameters.get(JAVA_INT, CODEC_ID);
    }

    /// The codec configuration the container carries, sized; empty when there is
    /// none.
    static MemorySegment extradata(MemorySegment parameters) {
        var size = parameters.get(JAVA_INT, EXTRADATA_SIZE);
        return size <= 0 ? MemorySegment.NULL : Pointers.array(parameters.get(ADDRESS, EXTRADATA), JAVA_BYTE, size);
    }

    /// The pixel format of a video track, or the sample format of an audio one.
    static int format(MemorySegment parameters) {
        return parameters.get(JAVA_INT, FORMAT);
    }

    /// Bits per second, or 0 when unknown.
    static long bitRate(MemorySegment parameters) {
        return parameters.get(JAVA_LONG, BIT_RATE);
    }

    /// The codec profile, or `AV_PROFILE_UNKNOWN`.
    static int profile(MemorySegment parameters) {
        return parameters.get(JAVA_INT, PROFILE);
    }

    /// The codec level, or `AV_LEVEL_UNKNOWN`.
    static int level(MemorySegment parameters) {
        return parameters.get(JAVA_INT, LEVEL);
    }

    /// Width in pixels.
    static int width(MemorySegment parameters) {
        return parameters.get(JAVA_INT, WIDTH);
    }

    /// Height in pixels.
    static int height(MemorySegment parameters) {
        return parameters.get(JAVA_INT, HEIGHT);
    }

    /// The channel count, from the channel layout.
    static int channels(MemorySegment parameters) {
        return parameters.get(JAVA_INT, NB_CHANNELS);
    }

    /// Samples per second.
    static int sampleRate(MemorySegment parameters) {
        return parameters.get(JAVA_INT, SAMPLE_RATE);
    }

    private static long offset(String field) {
        return FfmpegStructs.AV_CODEC_PARAMETERS.byteOffset(groupElement(field));
    }
}
