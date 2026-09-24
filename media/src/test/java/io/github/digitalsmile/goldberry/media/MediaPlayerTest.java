package io.github.digitalsmile.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.ForwardingSink;
import io.github.digitalsmile.goldberry.media.audio.VirtualSink;
import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.codec.Packet;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.SampleFormat;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// The Engine, end to end: open, buffer, play, seek, pause, end and fail, against
/// FFmpeg and a [VirtualSink].
///
/// The sources are WAVs from [Wav], served by an in-memory `mem:` protocol, so
/// every sample the sink receives is known. At 48 kHz stereo there is no resample
/// between the file and the sink, and PCM s16 → f32 is exact, so "the first
/// sample after a seek" can be checked to the sample.
@DisplayName("MediaPlayer, against FFmpeg")
class MediaPlayerTest {

    private static final AudioFormat FORMAT = AudioFormat.DEFAULT;
    private static final Duration WAIT = Duration.ofSeconds(10);

    /// Serves one byte array under `mem:`.
    record MemoryProtocol(byte[] data) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return new MemoryIO(data);
        }
    }

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

    private void open(byte[] data, boolean instant, List<? extends DecoderProvider> providers) {
        sink = new VirtualSink(FORMAT, instant);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new MemoryProtocol(data)))
                .decoderProviders(providers)
                .build();
        player.onStatus(status -> {
            synchronized (states) {
                if (states.isEmpty() || states.getLast() != status.state()) {
                    states.add(status.state());
                }
            }
        });
        player.open(Source.of(URI.create("mem:///clip.wav")));
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
                break;
            }
        }
        throw new AssertionError("timed out; last status " + player.status() + ", states " + states);
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out");
            }
            Thread.onSpinWait();
        }
    }

    private PlayerStatus awaitState(PlaybackState state) {
        return await(status -> status.state() == state);
    }

    @Test
    @DisplayName("plays a file to the end, every sample, through OPENING, BUFFERING and PLAYING")
    void playsToTheEnd() {
        var frames = FORMAT.sampleRate() / 2;
        open(Wav.sine(FORMAT.sampleRate(), 2, frames, 440, 12_000), true, List.of());
        var ended = awaitState(PlaybackState.ENDED);

        assertEquals(frames, sink.capturedSamples());
        var captured = sink.captured();
        for (var i = 0; i < frames; i += 101) {
            assertEquals(Wav.sineSample(FORMAT.sampleRate(), i, 440, 12_000) / 32768f, captured[2 * i], 0f);
        }
        assertEquals(Duration.ofMillis(500), ended.position());
        assertEquals(
                List.of(PlaybackState.OPENING, PlaybackState.BUFFERING, PlaybackState.PLAYING, PlaybackState.ENDED),
                List.copyOf(states));
        assertEquals("ffmpeg", ended.audioDecoder().orElseThrow());
        assertTrue(ended.seekable());
    }

    @Test
    @DisplayName("converts to the sink's format: 8 kHz mono in, 48 kHz stereo out")
    void resamplesToTheSink() {
        open(Wav.sine(8_000, 1, 8_000, 440, 12_000), true, List.of());
        awaitState(PlaybackState.ENDED);
        assertTrue(Math.abs(sink.capturedSamples() - FORMAT.sampleRate()) <= 64, "got " + sink.capturedSamples());
    }

    @Test
    @DisplayName("keeps only about 200 ms queued in the sink, and the position follows what was heard")
    void throttlesAndReportsPosition() {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() * 2), false, List.of());
        awaitState(PlaybackState.PLAYING);
        await(status -> sink.queuedSamples() >= FORMAT.sampleRate() / 5);
        // Throttled: well short of the file's two seconds.
        assertTrue(sink.queuedSamples() < FORMAT.sampleRate() / 2, "queued " + sink.queuedSamples());
        assertEquals(Duration.ZERO, player.status().position());

        sink.advance(FORMAT.sampleRate() / 10);
        assertEquals(Duration.ofMillis(100), player.status().position());
    }

    /// ADR-0474: the audio clock is what is heard, so a speaker 50 ms behind the
    /// queue holds the position, and the pictures timed by it, 50 ms back.
    @Test
    @DisplayName("the position is what is heard: the sink's latency is taken off it")
    void positionIsWhatIsHeard() {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() * 2), false, List.of());
        awaitState(PlaybackState.PLAYING);
        await(status -> sink.queuedSamples() >= FORMAT.sampleRate() / 5);
        sink.latency(Duration.ofMillis(50).toNanos());

        assertEquals(Duration.ZERO, player.status().position(), "nothing heard yet, and never before the start");
        sink.advance(FORMAT.sampleRate() / 10);
        assertEquals(Duration.ofMillis(50), player.status().position());
        assertEquals(Duration.ofMillis(50), player.audioLatency());
    }

    /// Without the floor, a seek would land on its target and then step back by
    /// the latency while the first samples after it travel to the ear.
    @Test
    @DisplayName("after a seek the position holds at the target while the first samples travel")
    void latencyNeverStepsBackPastASeek() {
        var rate = FORMAT.sampleRate();
        open(Wav.silence(rate, 2, rate * 3), false, List.of());
        awaitState(PlaybackState.PLAYING);
        sink.latency(Duration.ofMillis(200).toNanos());
        player.seek(Duration.ofMillis(1500));
        await(status -> sink.clears() > 0 && sink.capturedSamples() >= rate / 5);

        sink.advance(rate / 10);
        assertEquals(Duration.ofMillis(1500), player.status().position(), "1600 left the queue, 1400 is heard");
        // The throttle keeps about 200 ms queued: wait for the next 200 before
        // playing them.
        await(status -> sink.queuedSamples() >= rate / 5);
        sink.advance(rate / 5);
        assertEquals(Duration.ofMillis(1600), player.status().position());
    }

    @Test
    @DisplayName("an audio delay adds to the sink's latency, either way, and is kept for the next source")
    void audioDelay() {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() * 2), false, List.of());
        awaitState(PlaybackState.PLAYING);
        await(status -> sink.queuedSamples() >= FORMAT.sampleRate() / 5);
        sink.latency(Duration.ofMillis(20).toNanos());
        sink.advance(FORMAT.sampleRate() / 5);

        player.setAudioDelay(Duration.ofMillis(30));
        assertEquals(Duration.ofMillis(50), player.audioLatency());
        assertEquals(Duration.ofMillis(150), player.status().position());

        // A television that is late with its picture: the pictures come forward.
        player.setAudioDelay(Duration.ofMillis(-50));
        assertEquals(Duration.ofMillis(230), player.status().position());

        assertThrows(IllegalArgumentException.class, () -> player.setAudioDelay(Duration.ofSeconds(3)));
        assertEquals(Duration.ofMillis(-50), player.audioDelay(), "a refused delay leaves the last one");

        player.open(Source.of(URI.create("mem:///clip.wav")));
        assertEquals(Duration.ofMillis(-50), player.audioDelay());
    }

    /// Once the queue is empty nothing leaves it, so without the tail the clock
    /// would stop a latency short, and the track end before its last samples
    /// were heard: a player that opens the next one then cuts them off.
    @Test
    @DisplayName("a track ends when its last sample is heard, not when it leaves the queue")
    void endsWhenHeard() {
        sink = new VirtualSink(FORMAT, true);
        sink.latency(Duration.ofMillis(150).toNanos());
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new MemoryProtocol(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() / 5))))
                .decoderProviders(List.of())
                .build();
        var started = System.nanoTime();
        player.open(Source.of(URI.create("mem:///clip.wav")));
        var ended = awaitState(PlaybackState.ENDED);

        assertTrue(
                System.nanoTime() - started >= Duration.ofMillis(150).toNanos(),
                "an instant sink ended before its latency had passed");
        assertEquals(Duration.ofMillis(200), ended.position(), "the clock ran on through the tail to the end");
    }

    /// The latency is wall-clock time: at twice the speed the device plays twice
    /// the stream in it, so twice the stream time is still on its way.
    @Test
    @DisplayName("at a rate, the latency is that much more stream time")
    void latencyAtARate() {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() * 2), false, List.of());
        awaitState(PlaybackState.PLAYING);
        player.setRate(2f);
        await(status -> sink.queuedSamples() >= FORMAT.sampleRate() / 5);
        sink.latency(Duration.ofMillis(50).toNanos());

        sink.advance(FORMAT.sampleRate() / 5);
        assertEquals(Duration.ofMillis(100), player.status().position());
    }

    @Test
    @DisplayName("seeks accurately: the first sample written after a seek is the target's (S2)")
    void seeksAccurately() {
        var rate = FORMAT.sampleRate();
        open(Wav.sine(rate, 2, rate * 3, 50, 20_000), false, List.of());
        awaitState(PlaybackState.PLAYING);
        player.seek(Duration.ofMillis(1500));
        assertEquals(Duration.ofMillis(1500), player.status().position());
        await(status -> sink.clears() > 0 && sink.capturedSamples() > rate / 10);

        var captured = sink.captured();
        var target = rate * 3 / 2;
        for (var i = 0; i < 64; i++) {
            assertEquals(
                    Wav.sineSample(rate, target + i, 50, 20_000) / 32768f,
                    captured[2 * i],
                    0f,
                    "sample " + i + " after the seek");
        }
        sink.playAll();
        await(status -> status.position().compareTo(Duration.ofMillis(1500)) > 0);
    }

    @Test
    @DisplayName("seeking after the end plays again")
    void seekAfterEnd() {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() / 4), true, List.of());
        awaitState(PlaybackState.ENDED);
        player.seek(Duration.ZERO);
        // With an instant sink the replay is over in a few milliseconds, so what is
        // checked is its result: the sink was cleared once, and received the whole
        // file again.
        await(status -> status.state() == PlaybackState.ENDED
                && sink.clears() == 1
                && sink.capturedSamples() == FORMAT.sampleRate() / 4);
    }

    @Test
    @DisplayName("pauses the sink and the clock, and plays on")
    void pauses() {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() * 2), false, List.of());
        awaitState(PlaybackState.PLAYING);
        player.pause();
        assertEquals(PlaybackState.PAUSED, player.status().state());
        assertTrue(sink.paused());
        player.play();
        assertEquals(PlaybackState.PLAYING, player.status().state());
        assertTrue(!sink.paused());
    }

    @Test
    @DisplayName("play straight after a paused seek starts the sink only once the old position's samples are gone")
    void playAfterPausedSeek() {
        var rate = FORMAT.sampleRate();
        var inner = new VirtualSink(FORMAT, false);
        // How many times the sink had been cleared, at each resume.
        var clearsAtResume = Collections.synchronizedList(new ArrayList<Integer>());
        var io = new MemoryIO(Wav.silence(rate, 2, rate * 3));
        sink = inner;
        player = MediaPlayer.builder()
                .sink(() -> new ForwardingSink(inner) {
                    @Override
                    public void resume() {
                        clearsAtResume.add(inner.clears());
                        super.resume();
                    }
                })
                .ioProviders(List.of(new MediaIOProvider() {
                    @Override
                    public Set<String> schemes() {
                        return Set.of("mem");
                    }

                    @Override
                    public MediaIO open(Source source) {
                        return io;
                    }
                }))
                .decoderProviders(List.of())
                // No high water mark to wait for, so play() starts the output at
                // once if the Engine lets it.
                .highWaterMark(Duration.ZERO)
                .build();
        player.open(Source.of(URI.create("mem:///clip.wav")));
        awaitState(PlaybackState.PLAYING);
        await(status -> inner.queuedSamples() >= rate / 10);
        player.pause();
        // The sink holds samples from before the seek, and the demuxer is held
        // in the middle of carrying it out.
        io.holdSeeks();
        player.seek(Duration.ofSeconds(2));
        awaitTrue(io::seekHeld);
        player.play();
        assertEquals(PlaybackState.PLAYING, player.status().state());
        assertTrue(inner.paused(), "the sink started with the old position's samples in it");

        io.releaseSeeks();
        await(status -> !inner.paused());
        assertEquals(1, clearsAtResume.getLast(), clearsAtResume.toString());
    }

    @Test
    @DisplayName("plays at a rate: the sink is told, the status says so, and the next source keeps it")
    void rate() {
        var rate = FORMAT.sampleRate();
        // A new sink for every source, so the second starts empty.
        player = MediaPlayer.builder()
                .sink(() -> sink = new VirtualSink(FORMAT, false))
                .ioProviders(List.of(new MemoryProtocol(Wav.silence(rate, 2, rate * 3))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///clip.wav")));
        awaitState(PlaybackState.PLAYING);
        assertEquals(1f, player.status().rate());
        player.setRate(2f);
        assertEquals(2f, sink.rate());
        assertEquals(2f, player.status().rate());
        // Twice the speed drains the sink twice as fast, so it keeps twice the
        // stream time queued.
        await(status -> sink.queuedSamples() >= rate * 3 / 10);

        player.open(Source.of(URI.create("mem:///again.wav")));
        awaitState(PlaybackState.PLAYING);
        assertEquals(2f, sink.rate());
        assertEquals(2f, player.status().rate());

        assertThrows(IllegalArgumentException.class, () -> player.setRate(0.1f));
        assertThrows(IllegalArgumentException.class, () -> player.setRate(5f));
        assertThrows(IllegalArgumentException.class, () -> player.setRate(Float.NaN));
        assertEquals(2f, player.status().rate());
    }

    @Test
    @DisplayName("a sink that plays at 1 only refuses another rate, and nothing changes")
    void rateRefused() {
        var rate = FORMAT.sampleRate();
        sink = new VirtualSink(FORMAT, false);
        player = MediaPlayer.builder()
                .sink(() -> new ForwardingSink(sink))
                .ioProviders(List.of(new MemoryProtocol(Wav.silence(rate, 2, rate))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///clip.wav")));
        awaitState(PlaybackState.PLAYING);
        assertThrows(IllegalStateException.class, () -> player.setRate(1.5f));
        assertEquals(1f, player.status().rate());
        assertEquals(1f, sink.rate());
        player.setRate(1f);
    }

    @Test
    @DisplayName("stepping a picture needs a picture: audio alone, or nothing open, does not step")
    void stepNeedsVideo() {
        var rate = FORMAT.sampleRate();
        try (var idle =
                MediaPlayer.builder().sink(() -> new VirtualSink(FORMAT, true)).build()) {
            assertFalse(idle.step(1));
        }
        open(Wav.silence(rate, 2, rate), false, List.of());
        awaitState(PlaybackState.PLAYING);
        assertFalse(player.step(1));
        assertEquals(PlaybackState.PLAYING, player.status().state());
    }

    @Test
    @DisplayName(
            "switches the audio track mid-play: the new track comes in at the position, the old one's metadata read")
    void selectTrack() {
        var rate = FORMAT.sampleRate();
        sink = new VirtualSink(FORMAT, false);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new MemoryProtocol(CodecFixturesTest.fixture("tones-two-tracks.mkv"))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///tones.mkv")));
        var playing = awaitState(PlaybackState.PLAYING);
        var tracks = playing.info().orElseThrow().tracks(MediaType.AUDIO);
        assertEquals(2, tracks.size());
        var english = tracks.get(0);
        var french = tracks.get(1);
        assertEquals(Optional.of("eng"), english.language());
        assertEquals(Optional.of("Concert pitch"), english.title());
        assertEquals(Optional.of("fra"), french.language());
        assertEquals(Optional.empty(), french.title());
        assertEquals(Optional.of(english), playing.audioTrack());
        assertEquals(Optional.empty(), playing.videoTrack());

        // Half a second of the first track, then the second.
        var half = rate / 2;
        var played = 0L;
        while (played < half) {
            var step = Math.min(half - played, sink.queuedSamples());
            sink.advance(step);
            played += step;
            Thread.onSpinWait();
        }
        var clearsBefore = sink.clears();
        player.selectTrack(french);
        var switched = await(status -> status.audioTrack().equals(Optional.of(french))
                && sink.clears() > clearsBefore
                && sink.capturedSamples() > rate / 10);
        assertEquals(PlaybackState.PLAYING, switched.state());

        // The first samples after the switch are the 880 Hz tone at half a
        // second, within Matroska's millisecond (48 samples at 48 kHz).
        var captured = sink.captured();
        var best = Long.MAX_VALUE;
        for (var offset = -48; offset <= 48; offset++) {
            var error = 0L;
            for (var i = 0; i < 480; i++) {
                var expected = Math.round(12_000 * Math.sin(2 * Math.PI * 880 * (half + offset + i) / rate));
                error += Math.abs(Math.round(captured[2 * i] * 32768f) - expected);
            }
            best = Math.min(best, error);
        }
        assertTrue(best <= 480, "the switched-to samples are not the 880 Hz tone at 0.5 s; error " + best);

        // Asking again for the playing track changes nothing.
        var clears = sink.clears();
        player.selectTrack(french);
        assertEquals(clears, sink.clears());

        assertThrows(
                IllegalArgumentException.class,
                () -> player.selectTrack(
                        new Track(7, CodecId.FLAC, "flac", french.params(), Optional.empty(), false, false)));
    }

    @Test
    @DisplayName("volume and mute set the sink's gain")
    void volume() {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate()), false, List.of());
        player.setVolume(0.5f);
        assertEquals(0.5f, sink.gain());
        player.setMuted(true);
        assertEquals(0f, sink.gain());
        assertEquals(0.5f, player.status().volume());
        player.setMuted(false);
        assertEquals(0.5f, sink.gain());
    }

    @Test
    @DisplayName("a codec nothing decodes is ERROR with UnsupportedCodec, naming it (S7)")
    void unsupportedCodec() {
        open(Wav.withFormatTag(Wav.silence(8_000, 1, 800), 6), true, List.of());
        var status = awaitState(PlaybackState.ERROR);
        assertEquals(
                new MediaError.UnsupportedCodec(List.of("pcm_alaw")),
                status.error().orElseThrow());
        assertEquals("pcm_alaw", status.info().orElseThrow().tracks().getFirst().codecName());
    }

    @Test
    @DisplayName("a source with no audio track is an error, not a silent success")
    void noAudio() {
        var srt = "1\n00:00:00,000 --> 00:00:01,000\nHello\n".getBytes(StandardCharsets.UTF_8);
        sink = new VirtualSink(FORMAT, true);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new MemoryProtocol(srt)))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///subtitles.srt")));
        var status = awaitState(PlaybackState.ERROR);
        assertInstanceOf(MediaError.InvalidData.class, status.error().orElseThrow());
    }

    @Test
    @DisplayName("a scheme nothing opens is ERROR with UnsupportedScheme")
    void unsupportedScheme() {
        sink = new VirtualSink(FORMAT, true);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of())
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("s3://bucket/clip.webm")));
        assertEquals(
                new MediaError.UnsupportedScheme("s3"),
                awaitState(PlaybackState.ERROR).error().orElseThrow());
    }

    /// Decodes `good` packets as silence, then throws: a provider failing
    /// mid-stream.
    static final class FlakyProvider implements DecoderProvider {
        private final int good;

        FlakyProvider(int good) {
            this.good = good;
        }

        @Override
        public String name() {
            return "flaky";
        }

        @Override
        public int priority() {
            return 10;
        }

        @Override
        public boolean supports(DecoderRequest request) {
            return request.codec() == CodecId.PCM_S16LE;
        }

        @Override
        public Decoder open(DecoderRequest request) {
            return new Decoder() {
                private final Arena arena = Arena.ofConfined();
                private final MemorySegment plane = arena.allocate(4L * 2 * 1024);
                private int sent;
                private int pending;
                private boolean ending;

                @Override
                public boolean send(Packet packet) {
                    if (++sent > good) {
                        throw new IllegalStateException("the flaky decoder gives up");
                    }
                    pending++;
                    return true;
                }

                @Override
                public void sendEnd() {
                    ending = true;
                }

                @Override
                public Received receive() {
                    if (pending > 0) {
                        pending--;
                        return new Received.Decoded(
                                new AudioFrame(SampleFormat.F32, 48_000, 2, 1024, List.of(plane), Frame.NO_PTS));
                    }
                    return ending ? Received.ENDED : Received.NEEDS_INPUT;
                }

                @Override
                public void flush() {
                    pending = 0;
                    ending = false;
                }

                @Override
                public void close() {
                    arena.close();
                }
            };
        }
    }

    @Test
    @DisplayName("a provider is preferred, and one that fails mid-stream hands over to the next (S8)")
    void fallbackMidStream() {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate()), true, List.of(new FlakyProvider(3)));
        var ended = awaitState(PlaybackState.ENDED);
        assertEquals("ffmpeg", ended.audioDecoder().orElseThrow());
        assertTrue(sink.capturedSamples() > 3 * 1024, "the built-in decoder carried on");
    }

    @Test
    @DisplayName("closing stops the threads and closes the sink; reopening replaces the playback")
    void closes() throws IOException {
        open(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() * 2), false, List.of());
        awaitState(PlaybackState.PLAYING);
        var first = sink;
        player.open(Source.of(URI.create("mem:///again.wav")));
        assertTrue(first.closed());
        player.close();
        assertTrue(sink.closed());
        assertEquals(PlaybackState.IDLE, player.status().state());
        player = null;
    }

    @Test
    @DisplayName("the default player plays through SDL in real time, with the audio clock following the device")
    void playsThroughSdl() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                io.github.digitalsmile.goldberry.natives.NativeLibrary.isAvailable(), "libgoldberry is not built");
        player = MediaPlayer.builder()
                .ioProviders(
                        List.of(new MemoryProtocol(Wav.silence(FORMAT.sampleRate(), 2, FORMAT.sampleRate() * 3 / 10))))
                .decoderProviders(List.of())
                .build();
        var started = System.nanoTime();
        player.open(Source.of(URI.create("mem:///clip.wav")));
        var playing = awaitState(PlaybackState.PLAYING);
        assertTrue(playing.position().compareTo(Duration.ofMillis(300)) < 0);
        var ended = awaitState(PlaybackState.ENDED);
        // Real time: the dummy device consumes at the stream's rate.
        assertTrue(System.nanoTime() - started >= 250_000_000L, "300 ms of audio ended too soon");
        assertEquals(Duration.ofMillis(300), ended.position());
    }

    @Test
    @DisplayName("before anything is opened the status is IDLE")
    void idle() {
        sink = new VirtualSink(FORMAT, true);
        try (var idle = MediaPlayer.builder().sink(() -> sink).build()) {
            assertEquals(PlaybackState.IDLE, idle.status().state());
            idle.play();
            idle.seek(Duration.ofSeconds(1));
            assertEquals(Duration.ZERO, idle.status().position());
        }
    }
}
