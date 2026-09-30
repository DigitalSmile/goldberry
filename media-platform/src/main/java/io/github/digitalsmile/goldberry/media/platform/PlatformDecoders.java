package io.github.digitalsmile.goldberry.media.platform;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.platform.linux.GStreamerAudioProvider;
import io.github.digitalsmile.goldberry.media.platform.linux.GStreamerVideoProvider;
import io.github.digitalsmile.goldberry.media.platform.linux.LinuxDecoders;
import io.github.digitalsmile.goldberry.media.platform.macos.AudioToolboxProvider;
import io.github.digitalsmile.goldberry.media.platform.macos.MacDecoders;
import io.github.digitalsmile.goldberry.media.platform.macos.VideoToolboxProvider;
import io.github.digitalsmile.goldberry.media.platform.windows.MediaFoundationAudioProvider;
import io.github.digitalsmile.goldberry.media.platform.windows.MediaFoundationVideoProvider;
import io.github.digitalsmile.goldberry.media.platform.windows.WindowsDecoders;

/// The operating system's own decoders, as Decoder SPI providers
/// (`docs/goldberry-media.md` §5, ADR-0472, ADR-0489).
///
/// This module's providers are found by `ServiceLoader` like any other, so an
/// application that puts it on the module path needs no code: a `MediaPlayer`
/// built with the default providers plays H.264, HEVC, AAC, AC-3 and E-AC-3
/// with the system's decoders. This class is for an application that lists its
/// providers itself:
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
/// | `gstreamer-video` | H.264, HEVC (8- and 10-bit 4:2:0), with the decoders installed | Linux |
/// | `gstreamer-audio` | AAC, AC-3, E-AC-3, with the decoders installed | Linux |
/// | `mediafoundation-video` | H.264, HEVC (8- and 10-bit 4:2:0) | Windows |
/// | `mediafoundation-audio` | AAC, AC-3, E-AC-3 | Windows |
///
/// Each provider supports nothing on another system, so a file in one of these
/// codecs fails there with `UNSUPPORTED_CODEC` naming it, as it does without
/// this module.
public final class PlatformDecoders {

    private PlatformDecoders() {}

    /// A new instance of every provider this module has, whatever the system.
    public static List<DecoderProvider> providers() {
        return List.of(
                new VideoToolboxProvider(),
                new AudioToolboxProvider(),
                new GStreamerVideoProvider(),
                new GStreamerAudioProvider(),
                new MediaFoundationVideoProvider(),
                new MediaFoundationAudioProvider());
    }

    /// Whether this system's decoders are available: the system's libraries
    /// bound. Binds them on the first call.
    public static boolean available() {
        return unavailableReason().isEmpty();
    }

    /// Why this system's decoders are not available, or empty when they are.
    public static Optional<String> unavailableReason() {
        return unavailableReason(System.getProperty("os.name", ""));
    }

    /// Why the decoders of the system `osName` names are not available: asks
    /// that system's package, and only that one.
    static Optional<String> unavailableReason(String osName) {
        var os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) {
            return MacDecoders.unavailableReason();
        }
        if (os.startsWith("linux")) {
            return LinuxDecoders.unavailableReason();
        }
        if (os.startsWith("windows")) {
            return WindowsDecoders.unavailableReason();
        }
        return Optional.of("no platform decoders for " + osName);
    }
}
