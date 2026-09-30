/// Goldberry's media module: audio and video playback over FFmpeg, driven from
/// Java (`docs/goldberry-media.md`).
///
/// An optional content module in `docs/content-widgets.md`'s sense: an
/// application opts in, and nothing in `:core` or `:widgets` knows this module
/// exists. The engine is FFmpeg's demuxers and decoders and nothing else of it —
/// no network layer, no filters, no devices — with the threads, the clock and every
/// byte of I/O on the Java side
/// ([ADR-0460](../book/src/adr/0460-media-is-ffmpeg-driven-from-java-not-libvlc.md)).
///
/// **This module binds native code itself**, which only `:natives` otherwise does.
/// FFmpeg is LGPL-2.1+ and must stay a set of replaceable shared libraries, so it
/// cannot join `libgoldberry`, and its bindings live beside the engine that is
/// their only caller
/// ([ADR-0461](../book/src/adr/0461-a-media-engine-binds-its-own-libraries.md)).
/// What `:natives`' rule protects still holds here: `…media.ffi` is not exported,
/// and no FFmpeg type appears in anything that is.
///
/// **The operating system's own decoders are here too**, behind the same Decoder
/// SPI: VideoToolbox and AudioToolbox on macOS, GStreamer on Linux, Media
/// Foundation on Windows, for the patent-pool codecs the published natives do
/// not build ([ADR-0472](../book/src/adr/0472-the-platform-decoders-bind-the-system-frameworks.md),
/// ADR-0489). They were `goldberry-media-platform` until
/// [ADR-0493](../book/src/adr/0493-the-platform-decoders-are-part-of-media.md):
/// they bind the system's libraries and ship no native code, so being in this
/// module costs an application nothing, and each supports nothing on a system
/// that is not its own.
///
/// Consumers pass `--enable-native-access=io.github.digitalsmile.goldberry.media`
/// (JEP 472).
module io.github.digitalsmile.goldberry.media {

    /// Logging and the start-up timeline (ADR-0174). Not `transitive`: no type of
    /// it appears in a signature here.
    requires io.github.digitalsmile.goldberry.common;

    /// SDL's audio stream, which `:natives` exports to this module alone: the
    /// desktop's audio sink (ADR-0461). Not `transitive`, since no type of it
    /// appears in a signature here.
    requires io.github.digitalsmile.goldberry.natives;

    /// Video on the GPU (`docs/gpu-plan.md`, phase 6; ADR-0484): `video-view`
    /// shows its pictures through `:gpu`'s video layer when `:gpu` is in the
    /// application's module graph, and draws them on the CPU when it is not.
    /// `static`, so an application that ships no GPU module plays video all the
    /// same; `:gpu` exports its video package to this module alone.
    requires static io.github.digitalsmile.goldberry.gpu;

    /// The controls `audio-player` is built from. `transitive` because an
    /// [io.github.digitalsmile.goldberry.media.view.AudioPlayer] is a widget.
    requires transitive io.github.digitalsmile.goldberry.widgets;

    /// The JDK's HTTP client, which `HttpIO` fetches `http:` and `https:` sources
    /// with (`docs/goldberry-media.md` §4). `transitive` because `HttpIO.open`
    /// takes an `HttpClient`, for an application that brings its own.
    requires transitive java.net.http;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    requires transitive static org.jspecify;

    /// The furniture: [io.github.digitalsmile.goldberry.media.MediaPlayer], which
    /// plays, [io.github.digitalsmile.goldberry.media.MediaProbe], which says what
    /// a source holds, the status and pictures a player reports, the Clock SPI,
    /// and the errors.
    exports io.github.digitalsmile.goldberry.media;

    /// A decoded picture, in either of the two forms a player hands a view:
    /// converted to BGRA, or its planes as decoded (ADR-0483, ADR-0496).
    exports io.github.digitalsmile.goldberry.media.picture;

    /// What a track *is*, in Goldberry's words rather than FFmpeg's: the codec, the
    /// kind of stream and its parameters. It is also the vocabulary the Decoder SPI
    /// is written in (phase 2).
    exports io.github.digitalsmile.goldberry.media.codec;

    /// Where the bytes come from. FFmpeg performs no I/O of its own. Every byte it
    /// reads comes through a [io.github.digitalsmile.goldberry.media.io.MediaIO],
    /// and an application adds a protocol by providing one.
    exports io.github.digitalsmile.goldberry.media.io;

    /// Text subtitles: cues read from SubRip and WebVTT files and from a
    /// container's subtitle track, as plain lines.
    exports io.github.digitalsmile.goldberry.media.subtitle;

    /// Where audio goes: the sink the Engine writes to, and the one format it
    /// writes. The desktop sink is SDL's. A test's sink plays in no time.
    exports io.github.digitalsmile.goldberry.media.audio;

    /// The widgets: `audio-player`, `video-view`, `media-controls` and
    /// `media-player`.
    exports io.github.digitalsmile.goldberry.media.view;

    /// The system decoders, listed for an application that builds its player's
    /// providers itself: [io.github.digitalsmile.goldberry.media.platform.PlatformDecoders].
    /// The implementations, `…platform.macos`, `…platform.linux` and
    /// `…platform.windows`, are not exported.
    exports io.github.digitalsmile.goldberry.media.platform;

    /// Opened to `:core`, which reads `media.css` out of this package.
    opens io.github.digitalsmile.goldberry.media.view to
            io.github.digitalsmile.goldberry.core;

    /// The catalog the weaver generates. The `provides` line is patched into the
    /// compiled descriptor by the build (ADR-0131).
    uses io.github.digitalsmile.goldberry.widgets.markup.WidgetCatalog;

    /// A protocol an application brings, found by URI scheme.
    uses io.github.digitalsmile.goldberry.media.io.MediaIOProvider;

    /// A decoder an application brings, consulted before the built-in ones.
    uses io.github.digitalsmile.goldberry.media.codec.DecoderProvider;

    /// What the operating system says its playback device's latency is, taken
    /// off the audio clock (ADR-0474). CoreAudio's is below; an application may
    /// bring another.
    uses io.github.digitalsmile.goldberry.media.audio.OutputLatency;

    /// The system decoders, each of which supports nothing off its own system.
    /// Found by `ServiceLoader` like an application's own, so a `MediaPlayer`
    /// built with the default providers plays H.264, HEVC, AAC, AC-3 and E-AC-3
    /// wherever the system can.
    provides io.github.digitalsmile.goldberry.media.codec.DecoderProvider with
            io.github.digitalsmile.goldberry.media.platform.macos.VideoToolboxProvider,
            io.github.digitalsmile.goldberry.media.platform.macos.AudioToolboxProvider,
            io.github.digitalsmile.goldberry.media.platform.linux.GStreamerVideoProvider,
            io.github.digitalsmile.goldberry.media.platform.linux.GStreamerAudioProvider,
            io.github.digitalsmile.goldberry.media.platform.windows.MediaFoundationVideoProvider,
            io.github.digitalsmile.goldberry.media.platform.windows.MediaFoundationAudioProvider;

    /// The default output device's latency, taken off the audio clock (ADR-0474).
    provides io.github.digitalsmile.goldberry.media.audio.OutputLatency with
            io.github.digitalsmile.goldberry.media.platform.macos.CoreAudioLatency;
}
