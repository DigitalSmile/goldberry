package dev.goldberry.media.codec;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

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
public record DecoderRequest(
        CodecId codec, String codecName, TrackParams params, MemorySegment extradata, Rational timeBase) {

    public DecoderRequest {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(codecName, "codecName");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(extradata, "extradata");
        Objects.requireNonNull(timeBase, "timeBase");
    }
}
