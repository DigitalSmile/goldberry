/// Goldberry's media module: audio and video playback, demuxed in Java and
/// decoded by the reference codecs.
///
/// An optional content module: an application opts in, and nothing in `:core`
/// or `:widgets` knows this module exists. The containers — Matroska and WebM,
/// MP4, WAV, and the rest as they land — are read in Java, every byte through
/// the I/O SPI, and the threads, the clock and the queues are Java too. What is
/// native is the decoding: dav1d, libvpx, libtheora, Opus, Vorbis, FLAC and
/// minimp3, with libyuv for the CPU's colour conversion, linked into one
/// `libgoldberry-media` per target and called through the Foreign Function and
/// Memory API. There is no libVLC, no FFmpeg and no JNI.
///
/// **This module binds native code itself**, which only `:natives` otherwise does.
/// Its library is its own so that an application which plays no media carries
/// none of it, and the bindings live beside the engine that is their only
/// caller. What `:natives`' rule protects still holds here: `…media.ffm` is not
/// exported, and no upstream type appears in anything that is.
///
/// **The operating system's own decoders are here too**, behind the same Decoder
/// SPI: VideoToolbox and AudioToolbox on macOS, GStreamer on Linux, Media
/// Foundation on Windows, for the patent-pool codecs the published natives do
/// not build. They bind the system's libraries and ship no native code, so being
/// in this module costs an application nothing, and each supports nothing on a
/// system that is not its own.
///
/// Consumers pass `--enable-native-access=dev.goldberry.media`
/// (JEP 472).
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html#the-module).
module dev.goldberry.media {

    /// Logging and the start-up timeline. Not `transitive`: no type of it
    /// appears in a signature here.
    requires dev.goldberry.common;

    /// SDL's audio stream, which `:natives` exports to this module alone: the
    /// desktop's audio sink. Not `transitive`, since no type of it
    /// appears in a signature here.
    requires dev.goldberry.natives;

    /// Video on the GPU: `video-view`
    /// shows its pictures through `:gpu`'s video layer when `:gpu` is in the
    /// application's module graph, and draws them on the CPU when it is not.
    /// `static`, so an application that ships no GPU module plays video all the
    /// same; `:gpu` exports its video package to this module alone.
    /// `transitive`, because [dev.goldberry.media.gpu.PictureRenderer]'s
    /// signatures name `:gpu`'s frame and render target, and a consumer
    /// compiling against them has to be able to read them.
    requires transitive static dev.goldberry.gpu;

    /// The controls `audio-player` is built from. `transitive` because an
    /// [dev.goldberry.media.view.AudioPlayer] is a widget.
    requires transitive dev.goldberry.widgets;

    /// The JDK's HTTP client, which `HttpIO` fetches `http:` and `https:` sources
    /// with. `transitive` because `HttpIO.open`
    /// takes an `HttpClient`, for an application that brings its own.
    requires transitive java.net.http;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    requires transitive static org.jspecify;

    /// The furniture: [dev.goldberry.media.MediaPlayer], which
    /// plays, [dev.goldberry.media.MediaProbe], which says what
    /// a source holds, the status and pictures a player reports, the Clock SPI,
    /// and the errors.
    exports dev.goldberry.media;

    /// A decoded picture, in either of the two forms a player hands a view:
    /// converted to BGRA, or its planes as decoded.
    exports dev.goldberry.media.picture;

    /// What a track *is*, in Goldberry's words rather than a library's: the codec, the
    /// kind of stream and its parameters. It is also the vocabulary the Decoder SPI
    /// is written in (phase 2).
    exports dev.goldberry.media.codec;

    /// Where the bytes come from. No native code reads a byte. Every byte a
    /// container reads comes through a [dev.goldberry.media.io.MediaIO],
    /// and an application adds a protocol by providing one.
    exports dev.goldberry.media.io;

    /// Text subtitles: cues read from SubRip and WebVTT files and from a
    /// container's subtitle track, as plain lines.
    exports dev.goldberry.media.subtitle;

    /// Where audio goes: the sink the Engine writes to, and the one format it
    /// writes. The desktop sink is SDL's. A test's sink plays in no time.
    exports dev.goldberry.media.audio;

    /// The widgets: `audio-player`, `video-view`, `media-controls` and
    /// `media-player`.
    exports dev.goldberry.media.view;

    /// A player's pictures drawn into an application's own textures:
    /// [dev.goldberry.media.gpu.PictureRenderer]. The only exported package that
    /// names `:gpu`'s types, and loaded only by an application that calls it.
    exports dev.goldberry.media.gpu;

    /// The system decoders, listed for an application that builds its player's
    /// providers itself: [dev.goldberry.media.platform.PlatformDecoders].
    /// The implementations, `…platform.macos`, `…platform.linux` and
    /// `…platform.windows`, are not exported.
    exports dev.goldberry.media.platform;

    /// The last rung before a codec is unsupported: an `ffmpeg` program the user
    /// installed, run per track and fed through pipes, named by
    /// `-Dgoldberry.media.ffmpeg` or handed to
    /// [dev.goldberry.media.ffmpegcli.FfmpegCliProvider#of]. Off unless one is.
    exports dev.goldberry.media.ffmpegcli;

    /// Opened to `:core`, which reads `media.css` out of this package.
    opens dev.goldberry.media.view to
            dev.goldberry.core;

    /// The catalog the weaver generates. The `provides` line is patched into the
    /// compiled descriptor by the build.
    uses dev.goldberry.widgets.markup.WidgetCatalog;

    /// A protocol an application brings, found by URI scheme.
    uses dev.goldberry.media.io.MediaIOProvider;

    /// A decoder an application brings, consulted before the built-in ones.
    uses dev.goldberry.media.codec.DecoderProvider;

    /// What the operating system says its playback device's latency is, taken
    /// off the audio clock. CoreAudio's is below; an application may
    /// bring another.
    uses dev.goldberry.media.audio.OutputLatency;

    /// The system decoders, each of which supports nothing off its own system.
    /// Found by `ServiceLoader` like an application's own, so a `MediaPlayer`
    /// built with the default providers plays H.264, HEVC, AAC, AC-3 and E-AC-3
    /// wherever the system can. Then Vulkan Video on the GPU, then `ffmpeg-cli`, which supports nothing until
    /// `-Dgoldberry.media.ffmpeg` names a program.
    provides dev.goldberry.media.codec.DecoderProvider with
            dev.goldberry.media.platform.macos.VideoToolboxProvider,
            dev.goldberry.media.platform.macos.AudioToolboxProvider,
            dev.goldberry.media.platform.linux.GStreamerVideoProvider,
            dev.goldberry.media.platform.linux.GStreamerAudioProvider,
            dev.goldberry.media.platform.windows.MediaFoundationVideoProvider,
            dev.goldberry.media.platform.windows.MediaFoundationAudioProvider,
            dev.goldberry.media.platform.vulkan.VulkanVideoProvider,
            dev.goldberry.media.ffmpegcli.FfmpegCliProvider;

    /// The default output device's latency, taken off the audio clock.
    provides dev.goldberry.media.audio.OutputLatency with
            dev.goldberry.media.platform.macos.CoreAudioLatency;
}
