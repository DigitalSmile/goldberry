package dev.goldberry.media.decoder.vorbis;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.ffm.MediaNatives;

/// Vorbis through libvorbis, for every Vorbis track, while
/// `libgoldberry-media` is loaded.
public final class VorbisProvider implements DecoderProvider {

    /// The provider's name, as [dev.goldberry.media.MediaCapabilities] and a
    /// player's status report it.
    public static final String NAME = "vorbis";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return request.codec() == CodecId.VORBIS && MediaNatives.isAvailable();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        return new VorbisDecoder(MediaNatives.get(), request);
    }
}
