package dev.goldberry.media.ffmpegcli;

import java.util.Map;
import java.util.Optional;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.matroska.MatroskaCodecs;

/// What the provider would hand `ffmpeg` for a track: the codec in `ffmpeg`'s
/// words, to ask the binary whether it decodes it, and the `CodecID` to write
/// the track with.
///
/// @param ffmpegName the codec's name in `ffmpeg -codecs`
/// @param matroskaId the `CodecID` the track is written with
record Claim(String ffmpegName, String matroskaId) {

    /// Matroska tracks this module reads as [CodecId#UNKNOWN], keeping their
    /// `CodecID` as their name, that `ffmpeg`'s Matroska reader knows. Only
    /// Matroska's names are matched, never `ffmpeg`'s: then the extradata is the
    /// track's `CodecPrivate` already, in the form the same `CodecID` wants back.
    private static final Map<String, String> MATROSKA_ONLY = Map.ofEntries(
            Map.entry("V_MPEG1", "mpeg1video"),
            Map.entry("V_PRORES", "prores"),
            Map.entry("V_THEORA", "theora"),
            Map.entry("V_FFV1", "ffv1"),
            Map.entry("A_MPEG/L1", "mp1"),
            Map.entry("A_MPEG/L2", "mp2"),
            Map.entry("A_DTS", "dts"),
            Map.entry("A_TRUEHD", "truehd"),
            Map.entry("A_MLP", "mlp"),
            Map.entry("A_ALAC", "alac"),
            Map.entry("A_TTA1", "tta"),
            Map.entry("A_WAVPACK4", "wavpack"));

    /// What `request` would be claimed as, or empty for a track the provider
    /// leaves alone: a codec the published natives decode, a track that is not
    /// video or audio, or a codec it cannot name to `ffmpeg`.
    static Optional<Claim> of(DecoderRequest request) {
        if (!(request.params() instanceof TrackParams.Video || request.params() instanceof TrackParams.Audio)) {
            return Optional.empty();
        }
        var codec = request.codec();
        if (codec != CodecId.UNKNOWN) {
            // The built-in decoders are tested, and preferred: never a royalty-free codec.
            if (codec.royaltyFree()) {
                return Optional.empty();
            }
            return MatroskaCodecs.matroskaId(codec).map(id -> new Claim(codec.ffmpegName(), id));
        }
        var name = request.codecName();
        return Optional.ofNullable(MATROSKA_ONLY.get(name)).map(ffmpegName -> new Claim(ffmpegName, name));
    }
}
