package dev.goldberry.media.platform.linux;

import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.TrackParams;

/// AAC, AC-3 and E-AC-3, decoded by the GStreamer decoders the Linux system has
/// installed (`docs/goldberry-media.md` §5, ADR-0489).
///
/// Supports an audio track when this is Linux with GStreamer, the codec is one
/// of the three, and the system has the codec's parser and a decoder for it. It
/// makes the same claims as the macOS provider: AAC with the
/// `AudioSpecificConfig` the container carries, AC-3 or E-AC-3 with a rate and
/// up to eight channels. Samples arrive as interleaved 32-bit float, in
/// FFmpeg's channel order. The decoder is the one GStreamer ranks highest:
/// `avdec_aac`, `faad`, `avdec_ac3`, `a52dec`.
///
/// The distribution licenses the decoders it ships, so a Goldberry application
/// that plays these codecs through this provider brings no codec of its own.
public final class GStreamerAudioProvider implements DecoderProvider {

    /// This provider's [#name()]: what a player's status reports as the decoder.
    public static final String NAME = "gstreamer-audio";

    /// A provider. `ServiceLoader` makes one on every operating system; nothing
    /// is opened until a track is offered.
    public GStreamerAudioProvider() {
        // Stateless: GStreamer is bound once per process, on first use.
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
        var codec = GstCodec.of(request.codec()).orElseThrow();
        return GStreamer.get().map(gs -> !gs.decoders(codec, request).isEmpty()).orElse(false);
    }

    @Override
    public Decoder open(DecoderRequest request) {
        if (!claims(request)) {
            throw new IllegalArgumentException("GStreamer does not decode this " + request.codecName() + " track");
        }
        var gs = GStreamer.get()
                .orElseThrow(() ->
                        new IllegalStateException(GStreamer.unavailableReason().orElse("no GStreamer")));
        var codec = GstCodec.of(request.codec()).orElseThrow();
        return new GstAudioDecoder(gs, codec, request, gs.decoders(codec, request));
    }

    /// Whether `request` is a track this provider decodes, on a system that has
    /// GStreamer and the decoders.
    static boolean claims(DecoderRequest request) {
        if (!(request.params() instanceof TrackParams.Audio audio)) {
            return false;
        }
        return switch (request.codec()) {
            // AAC in a container that gives no configuration is ADTS, whose
            // headers the demuxers here do not strip.
            case AAC -> request.extradata().byteSize() >= 2;
            case AC3, EAC3 -> audio.sampleRate() > 0 && audio.channels() > 0 && audio.channels() <= 8;
            default -> false;
        };
    }
}
