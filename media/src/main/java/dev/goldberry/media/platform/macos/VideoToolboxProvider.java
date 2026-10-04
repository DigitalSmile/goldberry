package dev.goldberry.media.platform.macos;

import java.util.Optional;

import dev.goldberry.media.bitstream.ParameterSets;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;

/// H.264 and HEVC, decoded by macOS's VideoToolbox.
///
/// Supports a video track when this is macOS, the codec is one of the two, the
/// container gave its configuration record (`avcC` or `hvcC`, which MP4 and
/// Matroska both do), and the stream is 8- or 10-bit 4:2:0. VideoToolbox picks
/// the media engine where the Mac has one for the stream, and its software
/// decoder otherwise; either way the pictures arrive as NV12 or P010.
///
/// Apple licenses both codecs for the decoders it ships, so a Goldberry
/// application that plays them through this provider brings no codec of its own.
public final class VideoToolboxProvider implements DecoderProvider {

    /// This provider's [#name()]: what a player's status reports as the decoder.
    public static final String NAME = "videotoolbox";

    /// A provider. `ServiceLoader` makes one on every operating system; nothing
    /// is opened until a track is offered.
    public VideoToolboxProvider() {
        // Stateless: the frameworks are bound once per process, on first use.
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return configuration(request).isPresent() && Frameworks.get().isPresent();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        var frameworks = Frameworks.get()
                .orElseThrow(() ->
                        new IllegalStateException(Frameworks.unavailableReason().orElse("no system frameworks")));
        var configuration = configuration(request)
                .orElseThrow(() -> new IllegalArgumentException(
                        "VideoToolbox does not decode this " + request.codecName() + " track"));
        return new VideoToolboxDecoder(frameworks, request, configuration);
    }

    /// `request`'s configuration record, read, when it is a track this provider
    /// decodes; empty otherwise, and for a record it cannot read.
    static Optional<ParameterSets.Configuration> configuration(DecoderRequest request) {
        return ParameterSets.of(request);
    }
}
