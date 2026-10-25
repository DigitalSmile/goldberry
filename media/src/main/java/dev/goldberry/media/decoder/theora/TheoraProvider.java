package dev.goldberry.media.decoder.theora;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.ffm.MediaNatives;

/// Theora through libtheora, for every Theora track, while
/// `libgoldberry-media` is loaded.
public final class TheoraProvider implements DecoderProvider {

    /// The provider's name, as [dev.goldberry.media.MediaCapabilities] and a
    /// player's status report it.
    public static final String NAME = "libtheora";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return request.codec() == CodecId.THEORA && MediaNatives.isAvailable();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        return new TheoraDecoder(MediaNatives.get(), request);
    }
}
