package dev.goldberry.media.ffi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.TrackParams;

/// Codec resolution: which decoder plays a track.
///
/// The Engine holds no codec whitelist. For each track it asks the
/// [DecoderProvider]s, highest priority first, and then the built-in FFmpeg
/// decoders, which answer from what this build compiled in. The first that
/// supports the track and opens is used. A provider that says yes and then fails
/// to open is logged and passed over: the first rung of the fallback ladder
/// ("provider → next provider → built-in").
///
/// The built-in decoders are two rungs when hardware decode is on and the codec
/// has a hardware path: FFmpeg on the device, then FFmpeg in
/// software. A mid-stream failure on the device walks one rung down, like a
/// provider's.
///
/// A video track whose pictures carry alpha beside them
/// ([TrackParams.Video#alpha()]) has no hardware rung: the built-in decoder
/// decodes it and its alpha in software, which a device cannot do for it. The
/// providers are asked first all the same, and see the flag in the request; the
/// system decoders claim H.264 and HEVC, which carry no alpha this way.
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
    /// that a file with two codecs it cannot play names both.
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
        var alpha = request.params() instanceof TrackParams.Video video && video.alpha();
        if (!alpha && FfmpegDecoder.hardwareCandidate(ffmpeg, parameters, hardware)) {
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
            return new Resolved(
                    FfmpegDecoder.open(ffmpeg, parameters, demuxer.timeBase(stream), Hardware.OFF, alpha), BUILT_IN);
        }
        throw new MediaException(new MediaError.UnsupportedCodec(List.of(request.codecName())));
    }
}
