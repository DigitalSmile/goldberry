package dev.goldberry.media.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaCapabilities;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.platform.linux.GStreamerAudioProvider;
import dev.goldberry.media.platform.linux.GStreamerVideoProvider;
import dev.goldberry.media.platform.macos.AudioToolboxProvider;
import dev.goldberry.media.platform.macos.VideoToolboxProvider;
import dev.goldberry.media.platform.windows.MediaFoundationAudioProvider;
import dev.goldberry.media.platform.windows.MediaFoundationVideoProvider;

/// A `MediaPlayer` playing H.264 and AAC through the platform providers, end to
/// end: bring-your-own-codec with the operating system's decoders rather than
/// a fake, and the unsupported-codec case turned around. On whichever system it
/// runs:
/// VideoToolbox and AudioToolbox on macOS, GStreamer on Linux, Media
/// Foundation on Windows.
@DisplayName("MediaPlayer with the platform decoders")
class PlatformPlaybackTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    /// Serves one fixture under `mem:`.
    private record Fixture(byte[] data) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return new MemoryIO(data);
        }
    }

    private MediaPlayer player;

    /// The names this system's video and audio providers report.
    private record Names(String video, String audio) {

        static Names current() {
            var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.startsWith("mac")) {
                return new Names(VideoToolboxProvider.NAME, AudioToolboxProvider.NAME);
            }
            if (os.startsWith("windows")) {
                return new Names(MediaFoundationVideoProvider.NAME, MediaFoundationAudioProvider.NAME);
            }
            return new Names(GStreamerVideoProvider.NAME, GStreamerAudioProvider.NAME);
        }
    }

    @BeforeEach
    void requirements() {
        FfmpegRequirement.enforce();
        if (!PlatformDecoders.available() && !Boolean.getBoolean("goldberry.platform.required")) {
            Assumptions.abort(PlatformDecoders.unavailableReason().orElseThrow());
        }
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    private static byte[] fixture(String name) {
        try (var in =
                PlatformPlaybackTest.class.getResourceAsStream("/dev/goldberry/media/platform/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalStateException("no fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void play(String name, MediaPlayer.Builder builder) {
        var sink = new VirtualSink(AudioFormat.DEFAULT, true);
        player = builder.hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> sink)
                .ioProviders(List.of(new Fixture(fixture(name))))
                .build();
        player.open(Source.of(URI.create("mem:///" + name)));
    }

    private PlayerStatus await(Predicate<PlayerStatus> condition) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            var status = player.status();
            if (condition.test(status)) {
                return status;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("timed out; the player is " + player.status());
    }

    @Test
    @DisplayName("found by ServiceLoader: an H.264 and AAC file plays to the end on the system's decoders")
    void playsThroughServiceLoader() {
        // No decoderProviders(…): the player asks ServiceLoader, which finds this
        // module's providers.
        play("clip-h264-high.mp4", MediaPlayer.builder());
        var ended = await(status -> status.state() == PlaybackState.ENDED || status.state() == PlaybackState.ERROR);
        assertEquals(PlaybackState.ENDED, ended.state(), () -> "failed: " + ended.error());
        assertEquals(Names.current().video(), ended.videoDecoder().orElseThrow());
        assertEquals(Names.current().audio(), ended.audioDecoder().orElseThrow());
        var picture = player.currentPicture().orElseThrow();
        assertEquals(160, picture.width());
        assertEquals(90, picture.height());
        // The last of 25 pictures at 25 fps.
        assertEquals(960_000_000L, picture.ptsNanos(), 1_000_000);
    }

    @Test
    @DisplayName("listed by PlatformDecoders: an HEVC file plays to the end")
    void playsThroughTheList() {
        play("clip-hevc.mp4", MediaPlayer.builder().decoderProviders(PlatformDecoders.providers()));
        var ended = await(status -> status.state() == PlaybackState.ENDED || status.state() == PlaybackState.ERROR);
        assertEquals(PlaybackState.ENDED, ended.state(), () -> "failed: " + ended.error());
        assertEquals(Names.current().video(), ended.videoDecoder().orElseThrow());
    }

    @Test
    @DisplayName("without the providers the same file is UNSUPPORTED_CODEC, naming both codecs")
    void withoutTheProviders() {
        play("clip-h264-high.mp4", MediaPlayer.builder().decoderProviders(List.<DecoderProvider>of()));
        var failed = await(status -> status.state() == PlaybackState.ERROR);
        assertEquals(
                new MediaError.UnsupportedCodec(List.of("h264", "aac")),
                failed.error().orElseThrow());
    }

    @Test
    @DisplayName("the capabilities list this system's two providers by name")
    void capabilities() {
        var providers = MediaCapabilities.current().providers();
        assertTrue(providers.contains(Names.current().video()), providers::toString);
        assertTrue(providers.contains(Names.current().audio()), providers::toString);
    }
}
