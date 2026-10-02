package dev.goldberry.media.platform.linux;

import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.platform.bitstream.ParameterSets;

/// H.264 and HEVC, decoded by the GStreamer decoders the Linux system has
/// installed.
///
/// Supports a video track when this is Linux with GStreamer, the codec is one of
/// the two, the container gave its configuration record (`avcC` or `hvcC`), the
/// stream is 8- or 10-bit 4:2:0 (the same claim the macOS provider makes), and
/// the system has the codec's parser and a decoder for it. The decoder is the
/// one GStreamer ranks highest: `avdec_h264` and `avdec_h265` from gst-libav, a
/// VA-API decoder where the machine has one, `openh264dec`.
///
/// The distribution licenses the decoders it ships, so a Goldberry application
/// that plays these codecs through this provider brings no codec of its own.
public final class GStreamerVideoProvider implements DecoderProvider {

    /// This provider's [#name()]: what a player's status reports as the decoder.
    public static final String NAME = "gstreamer-video";

    /// A provider. `ServiceLoader` makes one on every operating system; nothing
    /// is opened until a track is offered.
    public GStreamerVideoProvider() {
        // Stateless: GStreamer is bound once per process, on first use.
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        if (ParameterSets.of(request).isEmpty()) {
            return false;
        }
        var codec = GstCodec.of(request.codec()).orElseThrow();
        return GStreamer.get().map(gs -> !gs.decoders(codec, request).isEmpty()).orElse(false);
    }

    @Override
    public Decoder open(DecoderRequest request) {
        if (ParameterSets.of(request).isEmpty()) {
            throw new IllegalArgumentException("GStreamer does not decode this " + request.codecName() + " track");
        }
        var gs = GStreamer.get()
                .orElseThrow(() ->
                        new IllegalStateException(GStreamer.unavailableReason().orElse("no GStreamer")));
        var codec = GstCodec.of(request.codec()).orElseThrow();
        return new GstVideoDecoder(gs, codec, request, gs.decoders(codec, request));
    }
}
