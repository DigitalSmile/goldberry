package io.github.digitalsmile.goldberry.media.platform;

import java.util.List;
import java.util.Optional;

import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.platform.macos.AudioToolboxProvider;
import io.github.digitalsmile.goldberry.media.platform.macos.MacDecoders;
import io.github.digitalsmile.goldberry.media.platform.macos.VideoToolboxProvider;

/// The operating system's own decoders, as Decoder SPI providers
/// (`docs/goldberry-media.md` §5, ADR-0472).
///
/// This module's providers are found by `ServiceLoader` like any other, so an
/// application that puts it on the module path needs no code: a `MediaPlayer`
/// built with the default providers plays H.264, HEVC, AAC, AC-3 and E-AC-3 on
/// macOS. This class is for an application that lists its providers itself:
///
/// ```java
/// var player = MediaPlayer.builder()
///         .decoderProviders(PlatformDecoders.providers())
///         .build();
/// ```
///
/// | Provider | Codecs | System |
/// |---|---|---|
/// | `videotoolbox` | H.264, HEVC (8- and 10-bit 4:2:0) | macOS |
/// | `audiotoolbox` | AAC (LC, HE, HEv2), AC-3, E-AC-3 | macOS |
///
/// On other systems the providers are there and support nothing, so a file in
/// one of these codecs fails with `UNSUPPORTED_CODEC` naming it, as it does
/// without this module.
public final class PlatformDecoders {

    private PlatformDecoders() {}

    /// A new instance of every provider this module has, whatever the system.
    public static List<DecoderProvider> providers() {
        return List.of(new VideoToolboxProvider(), new AudioToolboxProvider());
    }

    /// Whether this system's decoders are available: macOS, with the frameworks
    /// bound. Binds them on the first call.
    public static boolean available() {
        return MacDecoders.unavailableReason().isEmpty();
    }

    /// Why this system's decoders are not available, or empty when they are.
    public static Optional<String> unavailableReason() {
        return MacDecoders.unavailableReason();
    }
}
