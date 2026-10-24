package dev.goldberry.media.decoder.vpx;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.ffm.MediaNatives;

/// VP9 and VP8 through libvpx, for every track of either, while
/// `libgoldberry-media` is loaded.
public final class VpxProvider implements DecoderProvider {

    /// The provider's name, as [dev.goldberry.media.MediaCapabilities] and a
    /// player's status report it.
    public static final String NAME = "libvpx";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return (request.codec() == CodecId.VP9 || request.codec() == CodecId.VP8) && MediaNatives.isAvailable();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        return new VpxDecoder(MediaNatives.get(), request);
    }
}
