package dev.goldberry.media.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.decoder.BuiltInDecoders;

/// Codec resolution: which decoder plays a track.
///
/// The order is the application's providers, highest priority first, then the
/// built-in ones ([BuiltInDecoders]), whatever anyone's priority says: a
/// provider an application brings is there to be preferred, and the built-in
/// decoders are the fallback for the royalty-free codecs. A provider that
/// throws from `supports` or `open` is passed over with a warning, and the
/// next is asked.
///
/// The fallback ladder: a decoder that fails mid-stream is dropped and the
/// track is reopened with `skip` one higher, which passes over that many
/// candidates that would open. When none is left the track is unsupported.
public final class Decoders {

    private static final Logger LOG = Logs.of(Decoders.class);

    /// A decoder and the name of the provider that opened it.
    ///
    /// @param decoder  the decoder, opened
    /// @param provider the provider's name, as the status reports it
    public record Resolved(Decoder decoder, String provider) {

        public Resolved {
            Objects.requireNonNull(decoder, "decoder");
            Objects.requireNonNull(provider, "provider");
        }
    }

    private Decoders() {}

    /// Whether anything decodes `stream` of `container` as `hardware` allows: a
    /// provider that says it supports it, or a built-in decoder. Nothing is
    /// opened. The Engine asks this for every track it is about to play before
    /// it plays any of them, so that a file with two codecs it cannot play names
    /// both.
    public static boolean supports(
            Container container, int stream, List<? extends DecoderProvider> providers, HardwareDecoding hardware) {
        var request = container.request(stream).withHardware(hardware);
        for (var provider : ordered(providers)) {
            if (supports(provider, request)) {
                return true;
            }
        }
        return false;
    }

    /// Opens a decoder for `stream` of `container`.
    ///
    /// @param providers the application's providers, in any order; they are
    ///                  sorted here and the built-in ones follow them
    /// @param skip      how many candidates that would open to pass over: 0 for
    ///                  the first open, and one more for each decoder that has
    ///                  failed mid-stream on this track, which walks the fallback
    ///                  ladder
    /// @param hardware  what the player allows of the video engine, handed to
    ///                  every provider in its request
    /// @throws MediaException [MediaError.UnsupportedCodec] when no candidate is
    ///                        left
    public static Resolved open(
            Container container,
            int stream,
            List<? extends DecoderProvider> providers,
            int skip,
            HardwareDecoding hardware) {
        var request = container.request(stream).withHardware(hardware);
        var remaining = skip;
        for (var provider : ordered(providers)) {
            if (!supports(provider, request)) {
                continue;
            }
            if (remaining > 0) {
                remaining--;
                continue;
            }
            try {
                return new Resolved(
                        Objects.requireNonNull(provider.open(request), "open returned null"), provider.name());
            } catch (RuntimeException e) {
                LOG.warn(
                        "decoder provider {} could not open {}; trying the next",
                        provider.name(),
                        request.codecName(),
                        e);
            }
        }
        throw new MediaException(new MediaError.UnsupportedCodec(List.of(request.codecName())));
    }

    /// `providers` by priority, highest first, then the built-in decoders.
    static List<DecoderProvider> ordered(List<? extends DecoderProvider> providers) {
        var ordered = new ArrayList<DecoderProvider>(providers);
        ordered.sort(Comparator.comparingInt(DecoderProvider::priority).reversed());
        ordered.addAll(BuiltInDecoders.providers());
        return ordered;
    }

    private static boolean supports(DecoderProvider provider, DecoderRequest request) {
        try {
            return provider.supports(request);
        } catch (RuntimeException e) {
            LOG.warn(
                    "decoder provider {} failed to answer for {}; skipping it",
                    provider.name(),
                    request.codecName(),
                    e);
            return false;
        }
    }
}
