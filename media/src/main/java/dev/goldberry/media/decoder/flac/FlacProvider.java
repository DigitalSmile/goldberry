package dev.goldberry.media.decoder.flac;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.ffm.MediaNatives;

/// FLAC through libFLAC, for every FLAC track, while `libgoldberry-media` is
/// loaded.
public final class FlacProvider implements DecoderProvider {

    /// The provider's name, as [dev.goldberry.media.MediaCapabilities] and a
    /// player's status report it.
    public static final String NAME = "flac";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return request.codec() == CodecId.FLAC && MediaNatives.isAvailable();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        return new FlacDecoder(MediaNatives.get(), request);
    }
}
