package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.junit.TimeBudget;
import dev.goldberry.junit.WallClock;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.io.HttpIO;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.io.TestHttpServer;

/// The Engine over sources that stall, drop and never end: the water marks,
/// progressive HTTP over a flaky link, and a live radio stream, against FFmpeg.
///
/// The water marks are checked against a fake `MediaIO` that stalls where it is
/// told, so the moment of the stall is exact; the link and the stream against a local HTTP
/// server. The audio is a WAV at the sink's own rate, so every sample that
/// reaches the sink can be checked, and "played through a drop" means "not one
/// sample lost or repeated".
@DisplayName("MediaPlayer over the network")
class NetworkPlaybackTest {

    private static final AudioFormat FORMAT = AudioFormat.DEFAULT;
    private static final int RATE = FORMAT.sampleRate();
    private static final int WAV_HEADER = 44;
    private static final int BYTES_PER_FRAME = 4;
    private static final Duration WAIT = Duration.ofSeconds(15);

    private static HttpClient client;

    private final List<PlaybackState> states = Collections.synchronizedList(new ArrayList<>());
    private final List<PlayerStatus> pushed = Collections.synchronizedList(new ArrayList<>());
    private MediaPlayer player;
    private VirtualSink sink;
    private TestHttpServer server;

    /// Opens every `mem:` source as the given `MediaIO`.
    record Memory(MediaIO io) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return io;
        }
    }

    /// `http:` through HttpIO with short waits, so the faults take milliseconds.
    record QuickHttp(HttpIO.Options options) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("http");
        }

        @Override
        public MediaIO open(Source source) throws IOException {
            return HttpIO.open(source, client, options);
        }
    }

    @BeforeAll
    static void client() {
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
        if (server != null) {
            server.close();
        }
    }

    private void open(URI uri, boolean instant, Duration highWater, List<? extends MediaIOProvider> protocols) {
        sink = new VirtualSink(FORMAT, instant);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(protocols)
                .decoderProviders(List.of())
                .highWaterMark(highWater)
                .build();
        player.onStatus(status -> {
            pushed.add(status);
            synchronized (states) {
                if (states.isEmpty() || states.getLast() != status.state()) {
                    states.add(status.state());
                }
            }
        });
        player.open(Source.of(uri));
    }

    private PlayerStatus await(Predicate<PlayerStatus> condition) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            var status = player.status();
            if (condition.test(status)) {
                return status;
            }
            sleep(2);
        }
        throw new AssertionError("timed out; last status " + player.status() + ", states " + states);
    }

    private PlayerStatus awaitState(PlaybackState state) {
        return await(status -> status.state() == state);
    }

    private static void awaitTrue(BooleanSupplier condition) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out");
            }
            sleep(2);
        }
    }

    /// Plays whatever the sink holds while the player plays, until `until`: the
    /// speaker of a real device, sped up. Nothing is played while buffering, as
    /// a paused device would not.
    private PlayerStatus playUntil(Predicate<PlayerStatus> until) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            var status = player.status();
            if (until.test(status)) {
                return status;
            }
            if (status.state() == PlaybackState.PLAYING) {
                sink.playAll();
            }
            sleep(2);
        }
        throw new AssertionError("timed out; last status " + player.status() + ", states " + states);
    }

    /// Every sample of the sine the WAV holds reached the sink, in order: none
    /// lost to a drop, none repeated by a reconnect.
    private void assertWholeSine(int frames) {
        assertEquals(frames, sink.capturedSamples());
        var captured = sink.captured();
        for (var i = 0; i < frames; i += 97) {
            assertEquals(Wav.sineSample(RATE, i, 440, 12_000) / 32768f, captured[2 * i], 0f, "sample " + i);
        }
    }

    private static void assertBetween(Duration low, Duration actual, Duration high) {
        assertTrue(
                actual.compareTo(low) >= 0 && actual.compareTo(high) <= 0,
                actual + " is not within " + low + " and " + high);
    }

    private static long byteOf(double seconds) {
        return WAV_HEADER + (long) (seconds * RATE) * BYTES_PER_FRAME;
    }

    private boolean saw(PlaybackState... sequence) {
        synchronized (states) {
            var at = 0;
            for (var state : states) {
                if (at < sequence.length && state == sequence[at]) {
                    at++;
                }
            }
            return at == sequence.length;
        }
    }

    /// FFmpeg reads about 4.4 s of a 48 kHz stereo WAV while it probes one
    /// (`avformat_find_stream_info`), so a stall is set past that, where only the
    /// demux thread's own reads reach it.
    private static final double PAST_THE_PROBE = 6;

    @Test
    @DisplayName("starts only at the high water mark, and a stall goes back to BUFFERING with the sink paused")
    void stallRebuffers() {
        var frames = RATE * 10;
        var io = new MemoryIO(Wav.sine(RATE, 2, frames, 440, 12_000));
        io.stallAt = byteOf(PAST_THE_PROBE);
        open(URI.create("mem:///clip.wav"), false, Duration.ofSeconds(1), List.of(new Memory(io)));
        var started = awaitState(PlaybackState.PLAYING);
        assertTrue(started.bufferedAhead().compareTo(Duration.ofSeconds(1)) >= 0, started.toString());

        // Played up to the stall: nothing left to play, and more to come.
        var stalled = playUntil(status -> status.state() == PlaybackState.BUFFERING);
        assertTrue(io.stalled());
        assertTrue(sink.paused());
        // At the stall, less the part of a packet FFmpeg holds back until the
        // rest of it arrives.
        assertBetween(Duration.ofMillis(5_900), stalled.position(), Duration.ofSeconds(6));
        assertEquals(Duration.ZERO, stalled.bufferedAhead());

        // Half a second arrives, then the link stalls again: half a second is not
        // the high water mark, so it waits rather than play it and stall again.
        io.stallAt = byteOf(PAST_THE_PROBE + 0.5);
        io.release();
        awaitTrue(io::stalled);
        await(status -> status.bufferedAhead().compareTo(Duration.ofMillis(400)) > 0);
        sleep(150);
        var waiting = player.status();
        assertEquals(PlaybackState.BUFFERING, waiting.state());
        assertBetween(Duration.ofMillis(400), waiting.bufferedAhead(), Duration.ofMillis(600));
        assertTrue(sink.paused());

        io.stallAt = -1;
        io.release();
        var resumed = awaitState(PlaybackState.PLAYING);
        assertTrue(resumed.bufferedAhead().compareTo(Duration.ofSeconds(1)) >= 0, resumed.toString());
        assertFalse(sink.paused());
        playUntil(status -> status.state() == PlaybackState.ENDED);
        assertTrue(
                saw(PlaybackState.PLAYING, PlaybackState.BUFFERING, PlaybackState.PLAYING, PlaybackState.ENDED),
                states.toString());
        assertWholeSine(frames);
    }

    @Test
    @DisplayName("a zero high water mark plays on as soon as there is anything to play")
    void zeroHighWater() {
        var io = new MemoryIO(Wav.sine(RATE, 2, RATE * 10, 440, 12_000));
        io.stallAt = byteOf(PAST_THE_PROBE);
        open(URI.create("mem:///clip.wav"), false, Duration.ZERO, List.of(new Memory(io)));
        awaitState(PlaybackState.PLAYING);
        playUntil(status -> status.state() == PlaybackState.BUFFERING);
        io.stallAt = byteOf(PAST_THE_PROBE + 0.2);
        io.release();
        awaitState(PlaybackState.PLAYING);
        io.stallAt = -1;
        io.release();
    }

    @Test
    @DisplayName("plays through dropped connections and a stalled one, every sample in order")
    void flakyLink() throws IOException {
        var frames = RATE * 3;
        var wav = Wav.sine(RATE, 2, frames, 440, 12_000);
        server = new TestHttpServer(wav);
        server.dropAfter = 100_000;
        server.drops = 2;
        server.stallAt = byteOf(2);
        var options = HttpIO.Options.DEFAULT
                .withReconnects(3, Duration.ofMillis(10), Duration.ofMillis(40))
                .withStallTimeout(Duration.ofMillis(300));
        open(server.uri("clip.wav"), true, Duration.ofMillis(500), List.of(new QuickHttp(options)));
        var ended = awaitState(PlaybackState.ENDED);
        assertWholeSine(frames);
        assertEquals(1, server.stalls());
        assertTrue(server.requests().size() >= 4, server.requests().toString());
        assertTrue(ended.seekable());
        assertFalse(ended.live());
        // All of it fetched, which is all of the presentation.
        assertEquals(List.of(new TimeRange(Duration.ZERO, ended.duration().orElseThrow())), ended.bufferedRanges());
    }

    @Test
    @DisplayName("seeks over HTTP, sample-exact, and shows what it has fetched")
    void seeksOverHttp() throws IOException {
        var frames = RATE * 3;
        server = new TestHttpServer(Wav.sine(RATE, 2, frames, 440, 12_000));
        open(server.uri("clip.wav"), false, Duration.ofMillis(500), List.of());
        awaitState(PlaybackState.PLAYING);
        player.seek(Duration.ofMillis(2_000));
        await(status -> sink.clears() > 0 && sink.capturedSamples() > RATE / 10);
        var captured = sink.captured();
        for (var i = 0; i < 64; i++) {
            assertEquals(Wav.sineSample(RATE, 2 * RATE + i, 440, 12_000) / 32768f, captured[2 * i], 0f);
        }
        var ranges = await(status -> !status.bufferedRanges().isEmpty()).bufferedRanges();
        assertEquals(Duration.ZERO, ranges.getFirst().start());
    }

    @Test
    @DisplayName("a live radio stream: LIVE, unseekable, and what is playing from its ICY titles")
    void radio() throws IOException {
        server = new TestHttpServer(CodecFixturesTest.fixture("tone.mp3"));
        server.icy = 8_192;
        server.titles = List.of("Goldberry - First", "Goldberry - Second");
        // The built-in protocol, as an application gets it.
        open(server.uri("stream"), false, Duration.ofMillis(500), List.of());
        var playing = await(status ->
                status.state() == PlaybackState.PLAYING && status.nowPlaying().isPresent());
        assertTrue(playing.live());
        assertFalse(playing.seekable());
        assertTrue(playing.nowPlaying().orElseThrow().startsWith("Goldberry - "), playing.toString());
        assertEquals(List.of(), playing.bufferedRanges());
        assertTrue(pushed.stream().anyMatch(status -> status.nowPlaying().isPresent()), "a title is pushed");

        // A seek on a live stream does nothing, and the stream plays on.
        player.seek(Duration.ofSeconds(5));
        playUntil(status -> status.position().compareTo(Duration.ofSeconds(1)) > 0);
        assertEquals(PlaybackState.PLAYING, player.status().state());
    }

    @Test
    @DisplayName("publishes a title the stream changes mid-play")
    void titleChanges() {
        // Longer than the queues hold, so the demux thread is still reading, and
        // still asking for the title, as it plays.
        var io = new MemoryIO(Wav.sine(RATE, 2, RATE * 10, 440, 12_000));
        io.seekable = false;
        io.live = true;
        io.title = "Before";
        open(URI.create("mem:///live.wav"), false, Duration.ofMillis(100), List.of(new Memory(io)));
        var first = await(status -> status.nowPlaying().isPresent());
        assertEquals(Optional.of("Before"), first.nowPlaying());
        assertTrue(first.live());
        io.title = "After";
        // A new title is read after the next packet: play some to move it on.
        playUntil(status -> status.nowPlaying().equals(Optional.of("After")));
        assertTrue(pushed.stream().anyMatch(status -> status.nowPlaying().equals(Optional.of("After"))));
    }

    @Test
    @WallClock
    @DisplayName("closing while a network source is still opening returns at once")
    void closeWhileOpening() throws IOException {
        server = new TestHttpServer(Wav.sine(RATE, 2, RATE, 440, 12_000));
        // The headers arrive; the bytes never do.
        server.stallAt = 0;
        var stall = Duration.ofSeconds(30);
        var options = HttpIO.Options.DEFAULT.withStallTimeout(stall);
        open(server.uri("clip.wav"), true, Duration.ofMillis(500), List.of(new QuickHttp(options)));
        sleep(200);
        assertEquals(PlaybackState.OPENING, player.status().state());
        var started = System.nanoTime();
        player.close();
        player = null;
        // At once means well short of the stall timeout a close that waited
        // for the read would sit out.
        TimeBudget.of(Duration.ofSeconds(5))
                .shortOf(stall)
                .assertWithin(Duration.ofNanos(System.nanoTime() - started), "closing while opening");
    }

    @Test
    @DisplayName("a server that fails for good ends in ERROR, naming the status")
    void serverError() throws IOException {
        server = new TestHttpServer(new byte[16]);
        server.status = 404;
        open(server.uri("gone.wav"), true, Duration.ofMillis(500), List.of());
        var failed = awaitState(PlaybackState.ERROR);
        var error = (MediaError.Io) failed.error().orElseThrow();
        assertTrue(error.detail().contains("404"), error.detail());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
