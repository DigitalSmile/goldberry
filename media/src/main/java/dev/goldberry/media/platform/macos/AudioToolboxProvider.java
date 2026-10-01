package dev.goldberry.media.platform.macos;

import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.TrackParams;

/// AAC, AC-3 and E-AC-3, decoded by macOS's AudioToolbox
/// (`docs/goldberry-media.md` §5, ADR-0472).
///
/// Supports an audio track when this is macOS and the codec is one of the three:
/// AAC with the `AudioSpecificConfig` the container carries (MP4's `esds`,
/// Matroska's `CodecPrivate`), and AC-3 or E-AC-3 with a rate and up to eight
/// channels. Samples arrive as interleaved 32-bit float, in FFmpeg's channel
/// order.
///
/// Apple licenses the three codecs for the decoders it ships, so a Goldberry
/// application that plays them through this provider brings no codec of its own.
public final class AudioToolboxProvider implements DecoderProvider {

    /// This provider's [#name()]: what a player's status reports as the decoder.
    public static final String NAME = "audiotoolbox";

    /// A provider. `ServiceLoader` makes one on every operating system; nothing
    /// is opened until a track is offered.
    public AudioToolboxProvider() {
        // Stateless: the frameworks are bound once per process, on first use.
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return claims(request) && Frameworks.get().isPresent();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        if (!claims(request)) {
            throw new IllegalArgumentException("AudioToolbox does not decode this " + request.codecName() + " track");
        }
        var frameworks = Frameworks.get()
                .orElseThrow(() ->
                        new IllegalStateException(Frameworks.unavailableReason().orElse("no system frameworks")));
        return new AudioToolboxDecoder(frameworks, request);
    }

    /// Whether `request` is a track this provider decodes, on a system that has
    /// the frameworks.
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
}
