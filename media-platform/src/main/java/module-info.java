/// Goldberry's platform decoders: the operating system's own codecs behind the
/// Decoder SPI (`docs/goldberry-media.md` §5,
/// [ADR-0472](../book/src/adr/0472-the-platform-decoders-bind-the-system-frameworks.md)).
///
/// The published natives decode royalty-free codecs only. H.264, HEVC, AAC, AC-3
/// and E-AC-3 are left to whoever holds their licences, and an operating system
/// that ships decoders for them does. This module hands those decoders to the
/// Engine as [io.github.digitalsmile.goldberry.media.codec.DecoderProvider]s:
/// VideoToolbox and AudioToolbox on macOS, the GStreamer decoders a Linux
/// distribution installs, and Media Foundation on Windows (ADR-0489). It ships
/// no native code: it binds the system libraries with FFM, as `:media` binds
/// FFmpeg (ADR-0461).
///
/// Optional, like every content module: nothing depends on it, and an
/// application opts in by putting it on the module path. `ServiceLoader` finds
/// the providers from there.
///
/// Consumers pass
/// `--enable-native-access=io.github.digitalsmile.goldberry.media.platform`
/// (JEP 472).
module io.github.digitalsmile.goldberry.media.platform {

    /// The Decoder SPI. `transitive` because `PlatformDecoders` answers
    /// `DecoderProvider`s.
    requires transitive io.github.digitalsmile.goldberry.media;

    /// Logging (ADR-0174). Not `transitive`: no type of it appears in a
    /// signature here.
    requires io.github.digitalsmile.goldberry.common;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    requires static org.jspecify;

    /// The providers, listed for an application that builds its player's list
    /// itself. The implementations, `…platform.macos`, `…platform.linux` and
    /// `…platform.windows`, are not exported.
    exports io.github.digitalsmile.goldberry.media.platform;

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
