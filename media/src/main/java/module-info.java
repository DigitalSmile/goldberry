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

    /// The controls `audio-player` is built from. `transitive` because an
    /// [io.github.digitalsmile.goldberry.media.view.AudioPlayer] is a widget.
    requires transitive io.github.digitalsmile.goldberry.widgets;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    requires transitive static org.jspecify;

    /// The furniture: [io.github.digitalsmile.goldberry.media.MediaPlayer], which
    /// plays, [io.github.digitalsmile.goldberry.media.MediaProbe], which says what
    /// a source holds, the status and pictures a player reports, the Clock SPI,
    /// and the errors.
    exports io.github.digitalsmile.goldberry.media;

    /// What a track *is*, in Goldberry's words rather than FFmpeg's: the codec, the
    /// kind of stream and its parameters. It is also the vocabulary the Decoder SPI
    /// is written in (phase 2).
    exports io.github.digitalsmile.goldberry.media.codec;

    /// Where the bytes come from. FFmpeg performs no I/O of its own. Every byte it
    /// reads comes through a [io.github.digitalsmile.goldberry.media.io.MediaIO],
    /// and an application adds a protocol by providing one.
    exports io.github.digitalsmile.goldberry.media.io;

    /// Where audio goes: the sink the Engine writes to, and the one format it
    /// writes. The desktop sink is SDL's. A test's sink plays in no time.
    exports io.github.digitalsmile.goldberry.media.audio;

    /// The widgets: `audio-player`, `video-view`, `media-controls` and
    /// `media-player`.
    exports io.github.digitalsmile.goldberry.media.view;

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
}
