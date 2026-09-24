package io.github.digitalsmile.goldberry.media.ffi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;

/// Codec resolution (`docs/goldberry-media.md` §3): which decoder plays a track.
///
/// The Engine holds no codec whitelist. For each track it asks the
/// [DecoderProvider]s, highest priority first, and then the built-in FFmpeg
/// decoders, which answer from what this build compiled in. The first that
/// supports the track and opens is used. A provider that says yes and then fails
/// to open is logged and passed over: the first rung of the fallback ladder
/// ("provider → next provider → built-in").
///
/// The built-in decoders are two rungs when hardware decode is on and the codec
/// has a hardware path (phase 5, ADR-0470): FFmpeg on the device, then FFmpeg in
/// software. A mid-stream failure on the device walks one rung down, like a
/// provider's.
///
/// Nothing supports it → [MediaError.UnsupportedCodec], naming the codec.
public final class Decoders {

    /// What [#open] chose.
    ///
    /// @param decoder  the open decoder, which the caller closes
    /// @param provider the provider's [DecoderProvider#name], or [#BUILT_IN], or
    ///                 `ffmpeg (videotoolbox)` and the like for the built-in
    ///                 decoder on a device
    public record Resolved(Decoder decoder, String provider) {
        public Resolved {
            Objects.requireNonNull(decoder, "decoder");
            Objects.requireNonNull(provider, "provider");
        }
    }

    /// The name the built-in decoders go by, in [Resolved] and in capabilities.
    public static final String BUILT_IN = "ffmpeg";

    private static final Logger LOG = Logs.of(Decoders.class);

    private Decoders() {}

    /// Whether anything decodes `stream` of `demuxer`: a provider that says it
    /// supports it, or the built-in decoders. Nothing is opened. The Engine asks
    /// this for every track it is about to play before it plays any of them, so
    /// that a file with two codecs it cannot play names both (§7, S7).
    public static boolean supports(
            Ffmpeg ffmpeg, Demuxer demuxer, int stream, List<? extends DecoderProvider> providers) {
        var request = demuxer.request(stream);
        for (var provider : providers) {
            try {
                if (provider.supports(request)) {
                    return true;
                }
            } catch (RuntimeException e) {
                LOG.warn("decoder provider {} failed to answer for {}", provider.name(), request.codecName(), e);
            }
        }
        return FfmpegDecoder.supports(ffmpeg, demuxer.codecParameters(stream));
    }

    /// Opens a decoder for `stream` of `demuxer`, in software for the built-in
    /// decoders.
    public static Resolved open(
            Ffmpeg ffmpeg, Demuxer demuxer, int stream, List<? extends DecoderProvider> providers, int skip) {
        return open(ffmpeg, demuxer, stream, providers, Hardware.OFF, skip);
    }

    /// Opens a decoder for `stream` of `demuxer`.
    ///
    /// @param providers the providers to ask, in any order; they are sorted here
    /// @param hardware  whether the built-in decoder tries a device first
    /// @param skip      how many candidates that would open to pass over: 0 for the
    ///                  first open, and one more for each decoder that has failed
    ///                  mid-stream on this track, which walks the fallback ladder
    /// @throws MediaException [MediaError.UnsupportedCodec] when no candidate is
    ///                        left
    public static Resolved open(
            Ffmpeg ffmpeg,
            Demuxer demuxer,
            int stream,
            List<? extends DecoderProvider> providers,
            Hardware hardware,
            int skip) {
        var request = demuxer.request(stream);
        var ordered = new ArrayList<DecoderProvider>(providers);
        ordered.sort(Comparator.comparingInt(DecoderProvider::priority).reversed());
        var remaining = skip;
        for (var provider : ordered) {
            boolean supported;
            try {
                supported = provider.supports(request);
            } catch (RuntimeException e) {
                LOG.warn(
                        "decoder provider {} failed to answer for {}; skipping it",
                        provider.name(),
                        request.codecName(),
                        e);
                continue;
            }
            if (!supported) {
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
        var parameters = demuxer.codecParameters(stream);
        if (FfmpegDecoder.hardwareCandidate(ffmpeg, parameters, hardware)) {
            if (remaining == 0) {
                // A device that will not open is software already, and says so. One
                // that opens and fails to start the decoder is the next rung's.
                try {
                    var decoder = FfmpegDecoder.open(ffmpeg, parameters, demuxer.timeBase(stream), hardware);
                    return new Resolved(decoder, decoder.describe());
                } catch (FfmpegException e) {
                    LOG.info(
                            "the hardware decoder for {} did not start ({}); decoding in software",
                            request.codecName(),
                            e.getMessage());
                }
            } else {
                remaining--;
            }
        }
        if (remaining == 0 && FfmpegDecoder.supports(ffmpeg, parameters)) {
            return new Resolved(FfmpegDecoder.open(ffmpeg, parameters, demuxer.timeBase(stream)), BUILT_IN);
        }
        throw new MediaException(new MediaError.UnsupportedCodec(List.of(request.codecName())));
    }
}
