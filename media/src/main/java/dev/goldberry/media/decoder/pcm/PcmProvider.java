package dev.goldberry.media.decoder.pcm;

import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;

/// Raw PCM, decoded in Java: no library to load, so it is always there.
public final class PcmProvider implements DecoderProvider {

    /// The name the status reports.
    public static final String NAME = "pcm";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return PcmDecoder.decodes(request.codec());
    }

    @Override
    public Decoder open(DecoderRequest request) {
        return new PcmDecoder(request);
    }
}
