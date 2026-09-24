package io.github.digitalsmile.goldberry.media.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.FfmpegRequirement;
import io.github.digitalsmile.goldberry.media.HardwareDecoding;
import io.github.digitalsmile.goldberry.media.MediaCapabilities;
import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.VirtualSink;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.media.platform.macos.AudioToolboxProvider;
import io.github.digitalsmile.goldberry.media.platform.macos.VideoToolboxProvider;

/// A `MediaPlayer` playing H.264 and AAC through the platform providers, end to
/// end: `docs/goldberry-media.md` §7's S8 with the operating system's decoders
/// rather than a fake, and S7 turned around.
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
        try (var in = PlatformPlaybackTest.class.getResourceAsStream(
                "/io/github/digitalsmile/goldberry/media/platform/fixtures/" + name)) {
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
    @DisplayName("found by ServiceLoader: an H.264 and AAC file plays to the end on VideoToolbox and AudioToolbox")
    void playsThroughServiceLoader() {
        // No decoderProviders(…): the player asks ServiceLoader, which finds this
        // module's providers.
        play("clip-h264-high.mp4", MediaPlayer.builder());
        var ended = await(status -> status.state() == PlaybackState.ENDED || status.state() == PlaybackState.ERROR);
        assertEquals(PlaybackState.ENDED, ended.state(), () -> "failed: " + ended.error());
        assertEquals(VideoToolboxProvider.NAME, ended.videoDecoder().orElseThrow());
        assertEquals(AudioToolboxProvider.NAME, ended.audioDecoder().orElseThrow());
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
        assertEquals(VideoToolboxProvider.NAME, ended.videoDecoder().orElseThrow());
    }

    @Test
    @DisplayName("without the providers the same file is UNSUPPORTED_CODEC, naming both codecs (S7)")
    void withoutTheProviders() {
        play("clip-h264-high.mp4", MediaPlayer.builder().decoderProviders(List.<DecoderProvider>of()));
        var failed = await(status -> status.state() == PlaybackState.ERROR);
        assertEquals(
                new MediaError.UnsupportedCodec(List.of("h264", "aac")),
                failed.error().orElseThrow());
    }

    @Test
    @DisplayName("the capabilities list the two providers by name")
    void capabilities() {
        var providers = MediaCapabilities.current().providers();
        assertTrue(providers.contains(VideoToolboxProvider.NAME), providers::toString);
        assertTrue(providers.contains(AudioToolboxProvider.NAME), providers::toString);
    }
}
