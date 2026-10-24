package dev.goldberry.media.decoder.mp3;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.ffm.MediaNatives;

/// MPEG audio through minimp3, for every MP3 track and for the layer I and II
/// tracks a container names `mp1` and `mp2`, while `libgoldberry-media` is
/// loaded.
public final class Mp3Provider implements DecoderProvider {

    /// The provider's name, as [dev.goldberry.media.MediaCapabilities] and a
    /// player's status report it.
    public static final String NAME = "minimp3";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        if (!MediaNatives.isAvailable()) {
            return false;
        }
        if (request.codec() == CodecId.MP3) {
            return true;
        }
        return request.codec() == CodecId.UNKNOWN
                && (request.codecName().equals("mp1") || request.codecName().equals("mp2"));
    }

    @Override
    public Decoder open(DecoderRequest request) {
        return new Mp3Decoder(MediaNatives.get(), request);
    }
}
