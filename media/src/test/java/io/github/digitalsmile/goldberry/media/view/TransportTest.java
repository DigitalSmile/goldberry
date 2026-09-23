package io.github.digitalsmile.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.FfmpegRequirement;
import io.github.digitalsmile.goldberry.media.MediaInfo;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.media.TimeRange;
import io.github.digitalsmile.goldberry.media.Wav;
import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.VirtualSink;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.media.io.TestHttpServer;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.controls.slider.Slider;

/// What the transport controls make of a network source's status: the seek
/// bar's spans, and whether the widget keeps looking while the fetch goes on
/// (`docs/goldberry-media.md` §4, S3).
@DisplayName("the transport's buffered stretches")
class TransportTest {

    private static final Source SOURCE = Source.of(URI.create("http://example.com/clip.webm"));

    private static PlayerStatus status(PlaybackState state, Optional<Duration> duration, List<TimeRange> ranges) {
        var info = new MediaInfo(SOURCE, duration, List.of(), true, false);
        return new PlayerStatus(
                state,
                Duration.ZERO,
                Optional.of(info),
                Optional.empty(),
                1f,
                false,
                1f,
                Optional.empty(),
                Optional.empty(),
                Duration.ZERO,
                ranges,
                Optional.empty());
    }

    private static TimeRange range(long fromMillis, long toMillis) {
        return new TimeRange(Duration.ofMillis(fromMillis), Duration.ofMillis(toMillis));
    }

    @Test
    @DisplayName("each buffered range is a span of the seek bar, in seconds")
    void spansInSeconds() {
        var status = status(
                PlaybackState.PLAYING,
                Optional.of(Duration.ofSeconds(10)),
                List.of(range(0, 2_500), range(6_000, 7_250)));
        assertEquals(List.of(new Slider.Span(0, 2.5), new Slider.Span(6, 7.25)), Transport.buffered(status));
        assertEquals(List.of(), Transport.buffered(PlayerStatus.IDLE));
    }

    @Test
    @DisplayName("the widget keeps looking while part is fetched, and stops when all of it is, or nothing is")
    void fetching() {
        var ten = Optional.of(Duration.ofSeconds(10));
        assertTrue(FollowingState.fetching(status(PlaybackState.PAUSED, ten, List.of(range(0, 2_000)))));
        assertTrue(FollowingState.fetching(status(PlaybackState.BUFFERING, ten, List.of(range(0, 2_000)))));
        assertFalse(FollowingState.fetching(status(PlaybackState.PAUSED, ten, List.of(range(0, 10_000)))));
        assertFalse(FollowingState.fetching(status(PlaybackState.PAUSED, ten, List.of())));
        assertFalse(FollowingState.fetching(status(PlaybackState.OPENING, ten, List.of(range(0, 2_000)))));
        assertTrue(FollowingState.fetching(status(PlaybackState.PAUSED, Optional.empty(), List.of(range(0, 2_000)))));
    }

    @Test
    @DisplayName("over HTTP, the audio player's seek bar marks what has been fetched")
    void overHttp() throws IOException {
        FfmpegRequirement.enforce();
        var rate = AudioFormat.DEFAULT.sampleRate();
        try (var server = new TestHttpServer(Wav.silence(rate, 2, rate * 4));
                var player = MediaPlayer.builder()
                        .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                        .ioProviders(List.of())
                        .decoderProviders(List.of())
                        .build()) {
            player.open(Source.of(server.uri("clip.wav")));
            var deadline = System.nanoTime() + 10_000_000_000L;
            while ((player.status().bufferedRanges().isEmpty()
                            || !player.status().state().hasMedia())
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            var seek = walk(new ElementTree(new AudioPlayer(player)).root()).stream()
                    .map(Element::widget)
                    .filter(Slider.class::isInstance)
                    .map(Slider.class::cast)
                    .filter(slider -> slider.attributes().classes().contains("media-seek"))
                    .findFirst()
                    .orElseThrow();
            assertFalse(seek.spans().isEmpty(), "no spans on " + seek);
            assertEquals(0, seek.spans().getFirst().from(), 1e-9);
        }
    }

    private static List<Element> walk(Element root) {
        var all = new java.util.ArrayList<Element>();
        all.add(root);
        for (var child : root.children()) {
            all.addAll(walk(child));
        }
        return all;
    }
}
