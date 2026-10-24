package dev.goldberry.media.decoder.opus;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.decoder.BuiltInDecoders;
import dev.goldberry.media.ffm.MediaNatives;

/// Opus through libopus, for every Opus track, while `libgoldberry-media` is
/// loaded.
public final class OpusProvider implements DecoderProvider {

    @Override
    public String name() {
        return BuiltInDecoders.OPUS;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return request.codec() == CodecId.OPUS && MediaNatives.isAvailable();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        return new OpusDecoder(MediaNatives.get(), request);
    }
}
