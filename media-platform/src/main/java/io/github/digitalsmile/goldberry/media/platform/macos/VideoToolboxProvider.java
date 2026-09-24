package io.github.digitalsmile.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.util.Optional;

import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.TrackParams;

/// H.264 and HEVC, decoded by macOS's VideoToolbox (`docs/goldberry-media.md`
/// §5, ADR-0472).
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
        if (!(request.params() instanceof TrackParams.Video video) || video.width() <= 0 || video.height() <= 0) {
            return Optional.empty();
        }
        if (request.codec() != CodecId.H264 && request.codec() != CodecId.HEVC) {
            return Optional.empty();
        }
        try {
            var record = request.extradata().toArray(JAVA_BYTE);
            var configuration =
                    request.codec() == CodecId.H264 ? ParameterSets.h264(record) : ParameterSets.hevc(record);
            return configuration.shape().decodable() ? Optional.of(configuration) : Optional.empty();
        } catch (IllegalArgumentException e) {
            // No record, or Annex B start codes rather than one: a stream whose
            // packets are not in the form VideoToolbox reads.
            return Optional.empty();
        }
    }
}
