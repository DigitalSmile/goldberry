package dev.goldberry.media;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.ffi.FfmpegCapabilities;
import dev.goldberry.media.ffi.FfmpegLibraries;

/// What this installation can play: the codecs the loaded FFmpeg decodes, the
/// containers it demuxes, and the decoder providers on the class path.
///
/// Read from the libraries themselves, so it describes the build that is loaded
/// and not the one the documentation describes. An application uses it to grey
/// out a file it cannot play, and a bug report quotes it.
///
/// ```java
/// if (!MediaCapabilities.current().canDecode(CodecId.AV1)) { … }
/// ```
///
/// @param decoders  FFmpeg's codec names the built-in decoders handle (`av1`,
///                  `opus`, `pcm_s16le`)
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

    /// The capabilities of the loaded FFmpeg and the providers
    /// [ServiceLoader] finds.
    ///
    /// @throws MediaException with [MediaError.NativesUnavailable] when FFmpeg is
    ///                        not loaded
    public static MediaCapabilities current() {
        var ffmpeg = FfmpegLibraries.get();
        var providers = ServiceLoader.load(DecoderProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(DecoderProvider::priority).reversed())
                .map(DecoderProvider::name)
                .toList();
        return new MediaCapabilities(
                FfmpegCapabilities.decodableCodecs(ffmpeg), FfmpegCapabilities.demuxers(ffmpeg), providers);
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
