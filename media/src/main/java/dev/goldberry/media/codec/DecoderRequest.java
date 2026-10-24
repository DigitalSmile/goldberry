package dev.goldberry.media.codec;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import dev.goldberry.media.HardwareDecoding;

/// What the Engine asks a [DecoderProvider] to decode: one track, described in
/// Goldberry's words.
///
/// @param codec     the codec, or [CodecId#UNKNOWN]
/// @param codecName FFmpeg's name for it, which is the only name an
///                  [CodecId#UNKNOWN] codec has
/// @param params    the track's parameters
/// @param extradata the container's codec configuration, such as MP4's
///                  `avcC`/`esds` or Matroska's `CodecPrivate`. Empty when there is
///                  none, and valid only during [DecoderProvider#open]: a decoder
///                  that needs it later copies it
/// @param timeBase  the unit of the track's packet timestamps
/// @param hardware  whether the player lets the decoder use the platform's
///                  video engine: [HardwareDecoding#OFF] asks a provider that has
///                  both to decode in software, and one that has only hardware
///                  to answer no from [DecoderProvider#supports]
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public record DecoderRequest(
        CodecId codec,
        String codecName,
        TrackParams params,
        MemorySegment extradata,
        Rational timeBase,
        HardwareDecoding hardware) {

    public DecoderRequest {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(codecName, "codecName");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(extradata, "extradata");
        Objects.requireNonNull(timeBase, "timeBase");
        Objects.requireNonNull(hardware, "hardware");
    }

    /// A request that leaves the video engine to the provider
    /// ([HardwareDecoding#AUTO]): what a container describes a track as, before
    /// a player says what it allows.
    public DecoderRequest(
            CodecId codec, String codecName, TrackParams params, MemorySegment extradata, Rational timeBase) {
        this(codec, codecName, params, extradata, timeBase, HardwareDecoding.AUTO);
    }

    /// This request, with `hardware` allowed or not.
    public DecoderRequest withHardware(HardwareDecoding hardware) {
        return new DecoderRequest(codec, codecName, params, extradata, timeBase, hardware);
    }

    /// Whether the decoder must decode in software: [HardwareDecoding#OFF].
    public boolean softwareOnly() {
        return hardware == HardwareDecoding.OFF;
    }
}
