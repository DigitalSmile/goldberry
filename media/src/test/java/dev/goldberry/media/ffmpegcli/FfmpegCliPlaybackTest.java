package dev.goldberry.media.ffmpegcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaNativesRequirement;
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
import dev.goldberry.media.platform.fixtures.Fixtures;

/// A `MediaPlayer` playing H.264 and AAC with `ffmpeg` as its only decoder, end
/// to end, and walking past an `ffmpeg` that dies to the next one.
@DisplayName("MediaPlayer with ffmpeg-cli")
@Timeout(60)
class FfmpegCliPlaybackTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

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

    private Path ffmpeg;
    private MediaPlayer player;

    @BeforeEach
    void requirements() {
        MediaNativesRequirement.enforce();
        ffmpeg = FfmpegRequirement.ffmpeg();
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    private void play(String name, List<? extends DecoderProvider> providers) {
        var sink = new VirtualSink(AudioFormat.DEFAULT, true);
        player = MediaPlayer.builder()
                .decoderProviders(List.copyOf(providers))
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> sink)
                .ioProviders(List.of(new Fixture(Fixtures.bytes(name))))
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

    private PlayerStatus awaitTheEnd() {
        return await(status -> status.state() == PlaybackState.ENDED || status.state() == PlaybackState.ERROR);
    }

    @Test
    @DisplayName("an H.264 and AAC file plays to the end, both tracks decoded by ffmpeg")
    void plays() {
        play("clip-h264-high.mp4", List.of(FfmpegCliProvider.of(ffmpeg)));
        var ended = awaitTheEnd();
        assertEquals(PlaybackState.ENDED, ended.state(), () -> "failed: " + ended.error());
        assertEquals(FfmpegCliProvider.NAME, ended.videoDecoder().orElseThrow());
        assertEquals(FfmpegCliProvider.NAME, ended.audioDecoder().orElseThrow());
        var picture = player.currentPicture().orElseThrow();
        assertEquals(160, picture.width());
        assertEquals(90, picture.height());
        // The last of 25 pictures at 25 fps.
        assertEquals(960_000_000L, picture.ptsNanos());
    }

    @Test
    @DisplayName("an HEVC 10-bit file plays to the end")
    void playsTenBit() {
        play("clip-hevc-10bit.mp4", List.of(FfmpegCliProvider.of(ffmpeg)));
        var ended = awaitTheEnd();
        assertEquals(PlaybackState.ENDED, ended.state(), () -> "failed: " + ended.error());
        assertEquals(FfmpegCliProvider.NAME, ended.videoDecoder().orElseThrow());
    }

    @Test
    @DisplayName("an ffmpeg that dies mid-stream on the picture is dropped, and the next rung plays it to the end")
    void fallsBack(@TempDir Path directory) throws IOException {
        Assumptions.assumeFalse(
                System.getProperty("os.name", "").startsWith("Windows"), "a shell script stands in for ffmpeg");
        var dying = directory.resolve("dying-ffmpeg");
        var ran = directory.resolve("ran");
        Files.writeString(dying, """
                #!/bin/sh
                case " $* " in *" -codecs "*|*" -c:a "*) exec '%s' "$@" ;; esac
                touch '%s'
                head -c 3000 > /dev/null
                exit 3
                """.formatted(ffmpeg, ran));
        Files.setPosixFilePermissions(dying, PosixFilePermissions.fromString("rwx------"));
        // Both rank the same; the dying one is asked first, being first in the list.
        // It dies on the picture only: the Engine's audio thread has no rung once
        // its track has ended, and a one-second track may be all sent by then.
        play("clip-h264-high.mp4", List.of(FfmpegCliProvider.of(dying), FfmpegCliProvider.of(ffmpeg)));
        var ended = awaitTheEnd();
        assertEquals(PlaybackState.ENDED, ended.state(), () -> "failed: " + ended.error());
        assertEquals(960_000_000L, player.currentPicture().orElseThrow().ptsNanos());
        assertTrue(Files.exists(ran), "the dying ffmpeg was the first rung");
    }
}
