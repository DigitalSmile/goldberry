package dev.goldberry.media.decoder.av1;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.decoder.BuiltInDecoders;
import dev.goldberry.media.ffm.MediaNatives;

/// AV1 through dav1d, for every AV1 track, while `libgoldberry-media` is
/// loaded.
public final class Dav1dProvider implements DecoderProvider {

    @Override
    public String name() {
        return BuiltInDecoders.DAV1D;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return request.codec() == CodecId.AV1 && MediaNatives.isAvailable();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        return new Dav1dDecoder(MediaNatives.get(), request);
    }
}
