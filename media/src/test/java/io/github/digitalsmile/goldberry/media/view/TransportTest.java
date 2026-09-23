package io.github.digitalsmile.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.FfmpegRequirement;
import io.github.digitalsmile.goldberry.media.MediaInfo;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.media.TimeRange;
import io.github.digitalsmile.goldberry.media.Track;
import io.github.digitalsmile.goldberry.media.Wav;
import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.VirtualSink;
import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.TrackParams;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.media.io.TestHttpServer;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.controls.select.Select;
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
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
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

    private static Track audio(int index, String language, String title) {
        return new Track(
                index,
                CodecId.FLAC,
                "flac",
                new TrackParams.Audio(48_000, 2, Optional.empty(), OptionalInt.empty(), OptionalLong.empty()),
                Optional.empty(),
                false,
                false,
                Optional.ofNullable(language),
                Optional.ofNullable(title));
    }

    @Test
    @DisplayName("a track is labelled by its title and language, either alone, or its number")
    void labels() {
        assertEquals("Commentary (English)", Transport.trackLabel(audio(0, "eng", "Commentary"), 1, Locale.ENGLISH));
        assertEquals("French", Transport.trackLabel(audio(1, "fra", null), 2, Locale.ENGLISH));
        assertEquals("Commentary", Transport.trackLabel(audio(1, null, "Commentary"), 2, Locale.ENGLISH));
        assertEquals("Track 3", Transport.trackLabel(audio(2, null, null), 3, Locale.ENGLISH));
        assertEquals(
                "französisch".toLowerCase(Locale.ROOT),
                Transport.languageName("fra", Locale.GERMAN).toLowerCase(Locale.ROOT));
    }

    @Test
    @DisplayName("a language is named from ISO 639-1, 639-2/T and 639-2/B alike, and an unknown one is left as tagged")
    void languages() {
        assertEquals("French", Transport.languageName("fra", Locale.ENGLISH));
        assertEquals("French", Transport.languageName("fre", Locale.ENGLISH));
        assertEquals("French", Transport.languageName("fr", Locale.ENGLISH));
        assertEquals("German", Transport.languageName("deu", Locale.ENGLISH));
        assertEquals("German", Transport.languageName("GER", Locale.ENGLISH));
        assertEquals("Portuguese", Transport.languageName("pt-BR", Locale.ENGLISH));
        assertEquals("zzz", Transport.languageName("zzz", Locale.ENGLISH));
    }

    @Test
    @DisplayName("the audio track menu is there for two tracks or more, and chooses through the player")
    void menu() {
        FfmpegRequirement.enforce();
        try (var player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new MediaIOProvider() {
                    @Override
                    public java.util.Set<String> schemes() {
                        return java.util.Set.of("mem");
                    }

                    @Override
                    public MediaIO open(Source source) {
                        return new MemoryIO(MediaWidgetsTest.fixture("tones-two-tracks.mkv"));
                    }
                }))
                .decoderProviders(List.of())
                .build()) {
            assertTrue(Transport.audioTracks(player, PlayerStatus.IDLE).isEmpty());
            player.open(Source.of(URI.create("mem:///tones.mkv")));
            var deadline = System.nanoTime() + 10_000_000_000L;
            while (player.status().state() != PlaybackState.PLAYING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            var menu = (Select) Transport.audioTracks(player, player.status()).orElseThrow();
            assertTrue(menu.attributes().classes().contains("media-audio-track"));
            assertEquals("0", menu.value());
            var labels = menu.children().stream()
                    .map(Option.class::cast)
                    .map(Option::label)
                    .toList();
            assertEquals(2, labels.size());
            assertTrue(labels.getFirst().startsWith("Concert pitch"), labels.toString());
            menu.onChange().accept("1");
            while (!player.status()
                            .audioTrack()
                            .map(track -> track.index() == 1)
                            .orElse(false)
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(1, player.status().audioTrack().orElseThrow().index());
            assertEquals(
                    "1",
                    ((Select) Transport.audioTracks(player, player.status()).orElseThrow()).value());
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
