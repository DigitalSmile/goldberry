package dev.goldberry.media;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.container.Containers;
import dev.goldberry.media.decoder.BuiltInDecoders;
import dev.goldberry.media.ffm.MediaNatives;

/// What this installation can play: the codecs the built-in decoders decode,
/// the containers read in Java, and the decoder providers on the class path.
///
/// Asked of the loaded library and the providers themselves, so it describes
/// the build that is loaded and not the one the documentation describes. An application uses it to grey
/// out a file it cannot play, and a bug report quotes it.
///
/// ```java
/// if (!MediaCapabilities.current().canDecode(CodecId.AV1)) { … }
/// ```
///
/// @param decoders  FFmpeg's codec names the built-in decoders handle (`av1`,
///                  `opus`, `flac`); the PCM ones alone while the natives are
///                  not loaded
/// @param demuxers  the container format names the demuxers answer to
///                  (`matroska`, `webm`, `mp4`)
/// @param providers the [DecoderProvider]s found, by name, in the order they
///                  are asked
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
public record MediaCapabilities(Set<String> decoders, Set<String> demuxers, List<String> providers) {

    public MediaCapabilities {
        decoders = Set.copyOf(decoders);
        demuxers = Set.copyOf(demuxers);
        providers = List.copyOf(providers);
    }

    /// The capabilities of the loaded natives and the providers [ServiceLoader]
    /// finds. Without the natives the built-in decoders are PCM alone, and the
    /// containers and providers are still listed.
    public static MediaCapabilities current() {
        var providers = ServiceLoader.load(DecoderProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(DecoderProvider::priority).reversed())
                .map(DecoderProvider::name)
                .toList();
        return new MediaCapabilities(BuiltInDecoders.decodableCodecs(), Set.copyOf(Containers.readable()), providers);
    }

    /// Whether `libgoldberry-media` loaded and passed its checks: without it the
    /// built-in decoders are PCM alone, and [#nativesUnavailableReason()] says why.
    public static boolean nativesAvailable() {
        return MediaNatives.isAvailable();
    }

    /// Why the natives are not loaded, in the loader's words, or empty when they
    /// are: the natives jar is missing from the path, or the library failed a
    /// check. What a player shows beside a source it cannot play.
    public static Optional<String> nativesUnavailableReason() {
        return MediaNatives.unavailableReason();
    }

    /// Whether the built-in decoders handle `codec`.
    ///
    /// A provider may still play a codec this answers false for. Whether it does
    /// depends on the track (profile, size), which is why only opening one tells.
    public boolean canDecode(CodecId codec) {
        return codec != CodecId.UNKNOWN && decoders.contains(codec.ffmpegName());
    }

    /// Whether a demuxer answers to `format`, such as `webm` or `ogg`.
    public boolean canDemux(String format) {
        return demuxers.contains(format);
    }
}
