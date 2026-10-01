package dev.goldberry.media.platform.windows;

import java.util.Optional;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.TrackParams;

/// AAC, AC-3 and E-AC-3, decoded by Windows's Media Foundation decoders
/// (`docs/goldberry-media.md` §5, ADR-0472).
///
/// Supports an audio track when this is Windows, the codec is one of the three,
/// and the system has a decoder for it: AAC with the `AudioSpecificConfig` the
/// container carries (MP4's `esds`, Matroska's `CodecPrivate`), and AC-3 or
/// E-AC-3 with a rate and up to eight channels. Samples arrive as interleaved
/// 32-bit float, in FFmpeg's channel order.
///
/// Microsoft licenses the three codecs for the decoders it ships, so a Goldberry
/// application that plays them through this provider brings no codec of its own.
public final class MediaFoundationAudioProvider implements DecoderProvider {

    /// This provider's [#name()]: what a player's status reports as the decoder.
    public static final String NAME = "mediafoundation-audio";

    /// A provider. `ServiceLoader` makes one on every operating system; nothing
    /// is opened until a track is offered.
    public MediaFoundationAudioProvider() {
        // Stateless: Media Foundation is bound once per process, on first use.
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        if (!claims(request)) {
            return false;
        }
        var subtype = subtype(request.codec()).orElseThrow();
        return MediaFoundation.get()
                .filter(mf -> mf.hasDecoder(MfGuids.MFT_CATEGORY_AUDIO_DECODER, MfGuids.MFMediaType_Audio, subtype))
                .isPresent();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        if (!claims(request)) {
            throw new IllegalArgumentException(
                    "Media Foundation does not decode this " + request.codecName() + " track");
        }
        var mf = MediaFoundation.get()
                .orElseThrow(() -> new IllegalStateException(
                        MediaFoundation.unavailableReason().orElse("no Media Foundation")));
        return new MediaFoundationAudioDecoder(mf, request);
    }

    /// Whether `request` is a track this provider decodes, on a system that has
    /// the decoder.
    static boolean claims(DecoderRequest request) {
        if (!(request.params() instanceof TrackParams.Audio audio)) {
            return false;
        }
        return switch (request.codec()) {
            // The configuration is at least two bytes; AAC in a container that
            // gives none is ADTS, whose headers the demuxers here do not strip.
            case AAC -> request.extradata().byteSize() >= 2;
            case AC3, EAC3 -> audio.sampleRate() > 0 && audio.channels() > 0 && audio.channels() <= 8;
            default -> false;
        };
    }

    /// The Media Foundation input subtype of `codec`, or empty for one this
    /// provider does not decode.
    static Optional<Guid> subtype(CodecId codec) {
        return switch (codec) {
            case AAC -> Optional.of(MfGuids.MFAudioFormat_AAC);
            case AC3 -> Optional.of(MfGuids.MFAudioFormat_Dolby_AC3);
            case EAC3 -> Optional.of(MfGuids.MFAudioFormat_Dolby_DDPlus);
            default -> Optional.empty();
        };
    }
}
