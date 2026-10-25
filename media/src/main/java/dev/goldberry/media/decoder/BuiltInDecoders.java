package dev.goldberry.media.decoder;

import java.lang.foreign.MemorySegment;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.decoder.av1.Dav1dProvider;
import dev.goldberry.media.decoder.flac.FlacProvider;
import dev.goldberry.media.decoder.mp3.Mp3Provider;
import dev.goldberry.media.decoder.opus.OpusProvider;
import dev.goldberry.media.decoder.pcm.PcmProvider;
import dev.goldberry.media.decoder.theora.TheoraProvider;
import dev.goldberry.media.decoder.vorbis.VorbisProvider;
import dev.goldberry.media.decoder.vpx.VpxProvider;

/// The decoders `libgoldberry-media` holds, as providers, and raw PCM, which is
/// decoded in Java.
///
/// The Engine puts them after every provider an application brings, so they
/// are the fallback for the royalty-free codecs and nothing else. Each but PCM
/// supports its codec only while the library is loaded; without it, they answer
/// no and the Engine names the codec as unsupported.
public final class BuiltInDecoders {

    /// The provider names, as [dev.goldberry.media.MediaCapabilities] and a
    /// player's status report them.
    public static final String DAV1D = "dav1d";

    public static final String OPUS = "opus";

    public static final String FLAC = FlacProvider.NAME;

    public static final String LIBVPX = VpxProvider.NAME;

    public static final String PCM = PcmProvider.NAME;

    public static final String MINIMP3 = Mp3Provider.NAME;

    public static final String VORBIS = VorbisProvider.NAME;

    public static final String LIBTHEORA = TheoraProvider.NAME;

    private static final List<DecoderProvider> PROVIDERS = List.of(
            new Dav1dProvider(),
            new VpxProvider(),
            new TheoraProvider(),
            new OpusProvider(),
            new VorbisProvider(),
            new FlacProvider(),
            new Mp3Provider(),
            new PcmProvider());

    private BuiltInDecoders() {}

    /// Every built-in provider, in the order they are asked.
    public static List<DecoderProvider> providers() {
        return PROVIDERS;
    }

    /// FFmpeg's names of the codecs a built-in provider decodes right now: the
    /// PCM ones alone while the library is not loaded. What
    /// [dev.goldberry.media.MediaCapabilities#decoders()] answers.
    public static Set<String> decodableCodecs() {
        var names = new LinkedHashSet<String>();
        for (var codec : CodecId.values()) {
            if (codec == CodecId.UNKNOWN || (codec.type() != MediaType.VIDEO && codec.type() != MediaType.AUDIO)) {
                continue;
            }
            TrackParams params = codec.type() == MediaType.VIDEO
                    ? new TrackParams.Video(
                            0, 0, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty())
                    : new TrackParams.Audio(0, 0, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
            var request =
                    new DecoderRequest(codec, codec.ffmpegName(), params, MemorySegment.NULL, Rational.MICROSECONDS);
            for (var provider : PROVIDERS) {
                if (provider.supports(request)) {
                    names.add(codec.ffmpegName());
                    break;
                }
            }
        }
        return Set.copyOf(names);
    }
}
