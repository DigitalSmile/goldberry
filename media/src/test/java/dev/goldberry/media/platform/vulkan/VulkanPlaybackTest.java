package dev.goldberry.media.platform.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.platform.fixtures.Fixtures;

/// A `MediaPlayer` playing through Vulkan Video, end to end, on a machine
/// whose GPU decodes; skipped on one without.
@DisplayName("MediaPlayer with Vulkan Video")
class VulkanPlaybackTest {

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

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
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

    @ParameterizedTest(name = "{0}")
    @CsvSource({"clip-h264-full-709.mp4, 160, 90, 160", "clip-hevc-256.mp4, 256, 144, 960"})
    @DisplayName("a file plays to the end on the GPU's decoder, which the status names")
    void playsToTheEnd(String name, int width, int height, long lastMillis) {
        var provider = new VulkanVideoProvider(VulkanVideoProvider.Policy.ALL);
        try (var demuxer = Fixtures.demux(name)) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            Assumptions.assumeTrue(
                    VideoDevice.get().isPresent() && provider.supports(demuxer.request(track.index())),
                    "no Vulkan Video decoder for " + name + " on this machine");
        }
        var sink = new VirtualSink(AudioFormat.DEFAULT, true);
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.AUTO)
                .decoderProviders(List.of(provider))
                .sink(() -> sink)
                .ioProviders(List.of(new Fixture(Fixtures.bytes(name))))
                .build();
        player.open(Source.of(URI.create("mem:///" + name)));
        var ended = await(status -> status.state() == PlaybackState.ENDED || status.state() == PlaybackState.ERROR);
        assertEquals(PlaybackState.ENDED, ended.state(), () -> "failed: " + ended.error());
        assertEquals(VulkanVideoProvider.NAME, ended.videoDecoder().orElseThrow());
        var picture = player.currentPicture().orElseThrow();
        assertEquals(width, picture.width());
        assertEquals(height, picture.height());
        assertEquals(lastMillis * 1_000_000L, picture.ptsNanos(), 1_000_000);
    }
}
