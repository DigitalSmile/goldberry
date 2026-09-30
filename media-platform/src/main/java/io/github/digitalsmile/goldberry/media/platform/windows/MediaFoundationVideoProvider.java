package io.github.digitalsmile.goldberry.media.platform.windows;

import java.util.Optional;

import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.platform.bitstream.ParameterSets;

/// H.264 and HEVC, decoded by Windows's Media Foundation decoders
/// (`docs/goldberry-media.md` §5, ADR-0472).
///
/// Supports a video track when this is Windows, the codec is one of the two, the
/// container gave its configuration record (`avcC` or `hvcC`, which MP4 and
/// Matroska both do), the stream is 8- or 10-bit 4:2:0, and the system has a
/// decoder for the codec: H.264's ships with Windows, and HEVC's is the HEVC
/// Video Extensions a machine may or may not have. The pictures arrive as NV12
/// or P010.
///
/// Microsoft licenses both codecs for the decoders it ships, so a Goldberry
/// application that plays them through this provider brings no codec of its own.
public final class MediaFoundationVideoProvider implements DecoderProvider {

    /// This provider's [#name()]: what a player's status reports as the decoder.
    public static final String NAME = "mediafoundation-video";

    /// A provider. `ServiceLoader` makes one on every operating system; nothing
    /// is opened until a track is offered.
    public MediaFoundationVideoProvider() {
        // Stateless: Media Foundation is bound once per process, on first use.
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        if (configuration(request).isEmpty()) {
            return false;
        }
        var subtype = subtype(request.codec());
        return MediaFoundation.get()
                .filter(mf -> mf.hasDecoder(MfGuids.MFT_CATEGORY_VIDEO_DECODER, MfGuids.MFMediaType_Video, subtype))
                .isPresent();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        var configuration = configuration(request)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media Foundation does not decode this " + request.codecName() + " track"));
        var mf = MediaFoundation.get()
                .orElseThrow(() -> new IllegalStateException(
                        MediaFoundation.unavailableReason().orElse("no Media Foundation")));
        return new MediaFoundationVideoDecoder(mf, request, configuration);
    }

    /// `request`'s configuration record, read, when it is a track this provider
    /// decodes; empty otherwise, and for a record it cannot read.
    static Optional<ParameterSets.Configuration> configuration(DecoderRequest request) {
        return ParameterSets.of(request);
    }

    /// The Media Foundation input subtype of `codec`, H.264 or HEVC.
    static Guid subtype(CodecId codec) {
        return codec == CodecId.HEVC ? MfGuids.MFVideoFormat_HEVC : MfGuids.MFVideoFormat_H264;
    }
}
