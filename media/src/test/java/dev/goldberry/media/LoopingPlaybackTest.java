package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.picture.VideoPicture;

/// A looping player, end to end against FFmpeg: the source plays over and over
/// with no pause at the seam, its pictures' times running on, the position
/// within the source, and the state never `ENDED`.
///
/// Timed as `VideoPlaybackTest` is, by hand: through a [VirtualSink] where the
/// source has audio and a [MediaClock] where it does not. `clip-vp9.webm` is a
/// second of `testsrc2` at 25 fps with an Opus tone, and `clip-vp9-10bit.webm`
/// a fifth of a second with no audio: five pictures, 40 ms apart.
@DisplayName("MediaPlayer looping a source, against FFmpeg")
class LoopingPlaybackTest {

    private static final AudioFormat FORMAT = AudioFormat.DEFAULT;
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final long FRAME = 40_000_000L;
    private static final long SECOND = 1_000_000_000L;

    private final List<PlaybackState> states = Collections.synchronizedList(new ArrayList<>());
    private MediaPlayer player;
    private VirtualSink sink;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    private void open(String name, MediaClock clock) {
        sink = new VirtualSink(FORMAT, false);
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> sink)
                .clock(clock)
                .ioProviders(List.of(new VideoPlaybackTest.Fixture(VideoPlaybackTest.fixture(name))))
                .looping(true)
                .build();
        player.onStatus(status -> {
            synchronized (states) {
                if (states.isEmpty() || states.getLast() != status.state()) {
                    states.add(status.state());
                }
            }
        });
        player.open(Source.of(URI.create("mem:///" + name)));
    }

    private PlayerStatus await(Predicate<PlayerStatus> condition) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            var status = player.status();
            if (condition.test(status)) {
                return status;
            }
            sleep();
        }
        throw new AssertionError("timed out; last status " + player.status() + ", states " + states);
    }

    /// The picture shown once it is the one at `ptsNanos`, asking as a view does.
    private VideoPicture awaitPicture(long ptsNanos) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        VideoPicture last = null;
        while (System.nanoTime() < deadline) {
            var picture = player.currentPicture();
            if (picture.isPresent()) {
                last = picture.get();
                if (last.ptsNanos() == ptsNanos) {
                    return last;
                }
            }
            sleep();
        }
        throw new AssertionError(
                "no picture at " + ptsNanos + " ns; the last was " + last + ", status " + player.status());
    }

    /// Plays `nanos` more of the audio, by letting the virtual speaker play what
    /// the Engine writes, step by step.
    private void playAudioFor(long nanos) {
        var target = FORMAT.samples(nanos);
        var played = 0L;
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (played < target && System.nanoTime() < deadline) {
            var step = sink.paused() ? 0 : Math.min(target - played, sink.queuedSamples());
            if (step > 0) {
                sink.advance(step);
                played += step;
            } else {
                sleep();
            }
        }
        assertEquals(target, played, "the Engine did not write enough audio to play " + nanos + " ns more");
    }

    /// `position` is `nanos`, give or take a millisecond: the audio clock starts
    /// where the first sample is, and Opus's first is a fraction of one before zero.
    private static void assertAbout(long nanos, Duration position, String message) {
        assertTrue(Math.abs(position.toNanos() - nanos) <= 1_000_000L, message + ": " + position);
    }

    private static void sleep() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName(
            "with no audio, every picture of three passes shows at its time, and the position is within the source")
    void videoOnlyPlaysOver() {
        var now = new AtomicLong(SECOND);
        open("clip-vp9-10bit.webm", now::get);
        await(status -> status.state() == PlaybackState.PLAYING);
        assertTrue(player.status().looping());
        // Five pictures a pass: the n-th picture is at n pictures' time, whatever
        // the pass, since a pass is exactly five pictures long.
        for (var n = 0; n < 15; n++) {
            awaitPicture(n * FRAME);
            assertEquals(
                    Duration.ofNanos(n * FRAME % (5 * FRAME)), player.status().position());
            now.addAndGet(FRAME);
        }
        assertEquals(PlaybackState.PLAYING, player.status().state());
        synchronized (states) {
            assertEquals(
                    List.of(PlaybackState.PLAYING),
                    states.subList(states.indexOf(PlaybackState.PLAYING), states.size()),
                    "nothing waits at a seam, and nothing ends: " + states);
        }
        var statistics = player.videoStatistics();
        assertTrue(statistics.shown() >= 15, "counted on across the seams: " + statistics);
        assertEquals(0, statistics.late());
    }

    @Test
    @DisplayName("with audio, the clock runs on across the seam: each picture of the next pass at its time")
    void audioAndVideoPlayOver() {
        open("clip-vp9.webm", MediaClock.system());
        await(status -> status.state() == PlaybackState.PLAYING);
        awaitPicture(0);
        var clears = sink.clears();
        // A pass is the picture's second: 25 pictures. Halfway into each picture,
        // the one showing is the one at its start.
        for (var n = 1; n <= 60; n++) {
            playAudioFor(n == 1 ? FRAME + FRAME / 2 : FRAME);
            awaitPicture(n * FRAME);
        }
        assertAbout(60 * FRAME + FRAME / 2 - 2 * SECOND, player.status().position(), "within the third pass");
        assertEquals(PlaybackState.PLAYING, player.status().state());
        assertFalse(states.contains(PlaybackState.ENDED), "states " + states);
        assertEquals(clears, sink.clears(), "the sink is never cleared at a seam");
    }

    @Test
    @DisplayName("a source with only sound plays over too, the tone running on into the next pass")
    void audioOnlyPlaysOver() {
        open("tone-opus.webm", MediaClock.system());
        await(status -> status.state() == PlaybackState.PLAYING);
        playAudioFor(2 * SECOND + SECOND / 2);
        assertEquals(PlaybackState.PLAYING, player.status().state());
        assertFalse(states.contains(PlaybackState.ENDED), "states " + states);
        var position = player.status().position();
        assertTrue(
                position.compareTo(Duration.ofMillis(400)) > 0 && position.compareTo(Duration.ofMillis(600)) < 0,
                "within the third pass: " + position);
        // The tone is in the samples after the seams: never a stretch of silence
        // longer than the padding an Opus track's last packet ends with.
        var samples = sink.captured();
        var silent = 0;
        var longest = 0;
        for (var i = 0; i < samples.length; i += FORMAT.channels()) {
            silent = Math.abs(samples[i]) < 1e-4f ? silent + 1 : 0;
            longest = Math.max(longest, silent);
        }
        assertTrue(longest < FORMAT.samples(20_000_000L), "silence of " + longest + " samples at a seam");
    }

    @Test
    @DisplayName("turned off, the passes already read play out and the player ends within the source")
    void turnedOff() {
        var now = new AtomicLong(SECOND);
        open("clip-vp9-10bit.webm", now::get);
        await(status -> status.state() == PlaybackState.PLAYING);
        awaitPicture(0);
        now.addAndGet(7 * FRAME);
        awaitPicture(7 * FRAME);
        player.setLooping(false);
        assertFalse(player.status().looping());
        // The demuxer reads up to two seconds ahead: ten passes of this clip.
        for (var i = 0; i < 100 && player.status().state() != PlaybackState.ENDED; i++) {
            now.addAndGet(FRAME);
            sleep();
        }
        var ended = await(status -> status.state() == PlaybackState.ENDED);
        assertTrue(
                !ended.position().isNegative() && ended.position().toNanos() <= 5 * FRAME,
                "the end of the last pass, within the source: " + ended.position());
    }

    @Test
    @DisplayName("a seek after a seam lands in the source's own time, and the source plays over again from there")
    void seekAfterASeam() {
        open("clip-vp9.webm", MediaClock.system());
        await(status -> status.state() == PlaybackState.PLAYING);
        awaitPicture(0);
        playAudioFor(SECOND + 5 * FRAME + FRAME / 2);
        awaitPicture(SECOND + 5 * FRAME);
        player.pause();
        player.seek(Duration.ofMillis(500));
        // Pictures at 480 and 520 ms: 480 is the one showing at 500.
        awaitPicture(12 * FRAME);
        assertEquals(Duration.ofMillis(500), player.status().position());

        player.play();
        playAudioFor(600_000_000L);
        // 1.1 s on the clock: the second pass's picture at 80 ms.
        awaitPicture(SECOND + 2 * FRAME);
        assertAbout(100_000_000L, player.status().position(), "within the second pass");
    }

    @Test
    @DisplayName("a seek to the very end of a looping source starts it over rather than ending it")
    void seekToTheEnd() {
        var now = new AtomicLong(SECOND);
        open("clip-vp9-10bit.webm", now::get);
        await(status -> status.state() == PlaybackState.PLAYING);
        player.seek(Duration.ofMillis(200));
        // At the end: the next pass's first picture, at the pass's length.
        awaitPicture(5 * FRAME);
        // Then on through that pass, a few milliseconds at a time: the clock of
        // a seek starts with its first picture, which a loaded machine may queue
        // after a step of the hand.
        var last = 5 * FRAME;
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (last < 8 * FRAME && System.nanoTime() < deadline) {
            now.addAndGet(FRAME / 8);
            sleep();
            var pts = player.currentPicture().map(VideoPicture::ptsNanos).orElse(last);
            assertTrue(pts >= last, "pictures run on: " + pts + " after " + last);
            last = pts;
        }
        assertTrue(last >= 8 * FRAME, "into the next pass: " + last + ", status " + player.status());
        assertFalse(states.contains(PlaybackState.ENDED), "states " + states);
    }

    @Test
    @DisplayName("looping is the player's setting, kept for the next source and shown in the status")
    void setting() {
        player = MediaPlayer.builder().sink(() -> new VirtualSink(FORMAT, true)).build();
        assertFalse(player.looping());
        assertFalse(player.status().looping());
        player.setLooping(true);
        assertTrue(player.looping());
        assertTrue(player.status().looping());
    }
}
