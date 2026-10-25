package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.subtitle.Cue;

/// Subtitles through the Engine: a container's SubRip and ASS tracks chosen,
/// read as the demuxer reads, and shown at the right time; an external file
/// loaded in their place; and none.
///
/// The clip is video only, so its clock is a hand-moved [MediaClock] and "what
/// shows at 0.2 s" is exact.
@DisplayName("MediaPlayer showing subtitles, against the media natives")
class SubtitlePlaybackTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private MediaPlayer player;

    /// Serves the clip under `mem:`, and a SubRip file under `file:`-less names.
    record Protocol(byte[] clip, byte[] srt) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return new MemoryIO(source.fileName().orElse("").endsWith(".srt") ? srt : clip);
        }
    }

    @BeforeEach
    void open() {
        MediaNativesRequirement.enforce();
        var srt = """
                1
                00:00:00,000 --> 00:00:00,800
                From a file
                """.getBytes(StandardCharsets.UTF_8);
        player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .clock(now::get)
                .ioProviders(List.of(new Protocol(CodecFixturesTest.fixture("clip-vp9-subs.mkv"), srt)))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///clip-vp9-subs.mkv")));
        await(status -> status.state() == PlaybackState.PLAYING);
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    private PlayerStatus await(Predicate<PlayerStatus> condition) {
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            var status = player.status();
            if (condition.test(status)) {
                return status;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("timed out; last status " + player.status());
    }

    /// Moves the clock to `millis` of stream time, and checks `expected` shows
    /// there.
    ///
    /// What shows is waited for, up to 10 s. The clip has no audio, so its
    /// position is the hand-moved clock and not how far the demux thread has
    /// read: on a slow machine the clock reaches a cue before its packet is
    /// read, and the cue shows a moment later. One that never shows still fails.
    private void assertShows(long millis, String... expected) {
        moveTo(millis);
        var wanted = List.of(expected);
        var deadline = System.nanoTime() + 10_000_000_000L;
        var showing = showing();
        while (!showing.equals(wanted) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
            showing = showing();
        }
        assertEquals(wanted, showing, () -> "at " + millis + " ms");
    }

    private List<String> showing() {
        return player.currentSubtitles().stream().map(Cue::text).toList();
    }

    /// Moves the clock to `millis` of stream time.
    ///
    /// In steps of at most [#STEP], each waited for: while a seek settles the
    /// position reads its target and does not move with the clock, and a clock
    /// moved the whole way at once would run past the target when the seek lands.
    private void moveTo(long millis) {
        var target = Duration.ofMillis(millis);
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (player.status().position().compareTo(target) < 0 && System.nanoTime() < deadline) {
            var before = player.status().position();
            now.addAndGet(Math.min(target.minus(before).toNanos(), STEP.toNanos()));
            var settle = System.nanoTime() + 50_000_000L;
            while (player.status().position().equals(before) && System.nanoTime() < settle) {
                Thread.onSpinWait();
            }
        }
    }

    /// The most [#moveTo] moves the clock before it looks again.
    private static final Duration STEP = Duration.ofMillis(5);

    private Track subtitleTrack(CodecId codec) {
        return player.status().info().orElseThrow().tracks(MediaType.SUBTITLE).stream()
                .filter(track -> track.codec() == codec)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("shows none until a track is chosen, then a SubRip track's cues at their times, tags out")
    void subRip() {
        var tracks = player.status().info().orElseThrow().tracks(MediaType.SUBTITLE);
        assertEquals(2, tracks.size());
        assertEquals(Optional.of("eng"), subtitleTrack(CodecId.SUBRIP).language());
        assertEquals(Optional.of("fra"), subtitleTrack(CodecId.ASS).language());
        assertEquals(Optional.empty(), player.status().subtitles());
        assertEquals(List.of(), player.currentSubtitles());

        var english = subtitleTrack(CodecId.SUBRIP);
        player.selectTrack(english);
        await(status -> status.subtitles().equals(Optional.of(new SubtitleSource.Embedded(english))));
        assertShows(200, "First line");
        assertShows(450);
        assertShows(600, "Second line");
    }

    @Test
    @DisplayName("an ASS track's cue has its overrides out and its \\N as a line break")
    void ass() {
        var french = subtitleTrack(CodecId.ASS);
        player.selectTrack(french);
        await(status -> status.subtitles().isPresent());
        assertShows(300, "Première\nligne");
        assertShows(700);
    }

    @Test
    @DisplayName("a file loaded beside the source shows in place of a track, and hiding shows none")
    void externalAndHidden() throws IOException {
        player.selectTrack(subtitleTrack(CodecId.SUBRIP));
        await(status -> status.subtitles().isPresent());
        var file = Source.of(URI.create("mem:///film.srt"));
        player.loadSubtitles(file);
        assertEquals(
                Optional.of(new SubtitleSource.External(file)), player.status().subtitles());
        assertShows(100, "From a file");

        player.hideSubtitles();
        assertEquals(Optional.empty(), player.status().subtitles());
        assertShows(200);
    }

    @Test
    @DisplayName("choosing the video track that shows changes nothing, and the subtitles stay")
    void sameVideoTrack() {
        player.selectTrack(subtitleTrack(CodecId.SUBRIP));
        await(status -> status.subtitles().isPresent());
        var video = player.status().videoTrack().orElseThrow();
        player.selectTrack(video);
        assertEquals(Optional.of(video), player.status().videoTrack());
        assertShows(200, "First line");
    }
}
