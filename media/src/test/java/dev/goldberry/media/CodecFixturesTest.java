package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// Real codecs in real containers: the clips under `fixtures/`, made by
/// `make-fixtures.sh` from one second of a 440 Hz sine at 12000/32768, 48 kHz
/// stereo, and one second of a 160×90 test pattern.
///
/// FLAC is lossless, so its samples are compared exactly, after an accurate seek
/// as well. The lossy codecs are checked for what survives a lossy codec: the
/// length, the loudness, and the pitch counted in zero crossings. The video
/// clips are probed here and played for their audio. Their pictures are phase 3.
@DisplayName("Encoded clips, through the whole Engine")
class CodecFixturesTest {

    private static final AudioFormat FORMAT = AudioFormat.DEFAULT;
    private static final double RMS = 12000 / 32768.0 / Math.sqrt(2);

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

    static byte[] fixture(String name) {
        try (var in = CodecFixturesTest.class.getResourceAsStream("fixtures/" + name)) {
            return Objects.requireNonNull(in, name).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record Fixture(byte[] data) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return new MemoryIO(data);
        }
    }

    private static MediaInfo probe(String name) {
        return MediaProbe.probe(Source.of(URI.create("mem:///" + name)), new MemoryIO(fixture(name)));
    }

    private PlayerStatus play(String name, boolean instant) {
        sink = new VirtualSink(FORMAT, instant);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new Fixture(fixture(name))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///" + name)));
        return awaitState(instant ? PlaybackState.ENDED : PlaybackState.PLAYING);
    }

    private PlayerStatus awaitState(PlaybackState state) {
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            var status = player.status();
            if (status.state() == state) {
                return status;
            }
            if (status.state() == PlaybackState.ERROR && state != PlaybackState.ERROR) {
                throw new AssertionError("failed: " + status.error());
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("timed out waiting for " + state + "; last " + player.status());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "tone.flac, FLAC",
        "tone-flac.mp4, FLAC",
        "tone-flac.mkv, FLAC",
        "tone-opus.mp4, OPUS",
        "tone.opus, OPUS",
        "tone-opus.webm, OPUS",
        "tone.ogg, VORBIS",
        "tone.mp3, MP3",
    })
    @DisplayName("probes the audio codec and a one-second duration")
    void probes(String name, CodecId codec) {
        var info = probe(name);
        var track = info.defaultTrack(MediaType.AUDIO).orElseThrow();
        assertEquals(codec, track.codec());
        var audio = assertInstanceOf(TrackParams.Audio.class, track.params());
        assertEquals(48_000, audio.sampleRate());
        assertEquals(2, audio.channels());
        var duration = info.duration().orElseThrow();
        assertTrue(Math.abs(duration.toMillis() - 1000) <= 60, name + " lasts " + duration);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tone.flac", "tone-flac.mp4", "tone-flac.mkv"})
    @DisplayName("FLAC decodes every sample exactly, from each container")
    void flacExact(String name) {
        play(name, true);
        assertEquals(FORMAT.sampleRate(), sink.capturedSamples());
        var captured = sink.captured();
        for (var i = 0; i < FORMAT.sampleRate(); i++) {
            var expected = Wav.sineSample(FORMAT.sampleRate(), i, 440, 12_000) / 32768f;
            assertEquals(expected, captured[2 * i], 0f, "left sample " + i);
            assertEquals(expected, captured[2 * i + 1], 0f, "right sample " + i);
        }
    }

    /// An accurate seek is exact to the container's timestamps. FLAC and MP4 count
    /// in samples, so the first sample after the seek is the target's. Matroska
    /// counts in milliseconds, so a frame's start is known to half a millisecond
    /// (24 samples at 48 kHz), and the seek lands within that.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"tone.flac, 0", "tone-flac.mp4, 0", "tone-flac.mkv, 24"})
    @DisplayName("FLAC seeks accurately, to the precision of the container's timestamps")
    void flacSeek(String name, int tolerance) {
        play(name, false);
        player.seek(Duration.ofMillis(500));
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (!(sink.clears() > 0 && sink.capturedSamples() >= 256) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        var captured = sink.captured();
        var target = FORMAT.sampleRate() / 2;
        var offset = alignment(captured, target, 2 * tolerance + 1);
        assertTrue(Math.abs(offset) <= tolerance, name + " landed " + offset + " samples from the target");
        for (var i = 0; i < 256; i++) {
            assertEquals(
                    Wav.sineSample(FORMAT.sampleRate(), target + offset + i, 440, 12_000) / 32768f,
                    captured[2 * i],
                    0f,
                    "sample " + i + " after the seek");
        }
    }

    /// The offset from `target`, within ±`range`, at which `captured` matches the
    /// tone exactly for its first 64 samples; `range + 1` when it matches nowhere.
    private static int alignment(float[] captured, int target, int range) {
        for (var offset = 0; offset <= range; offset++) {
            for (var sign : new int[] {1, -1}) {
                var candidate = sign * offset;
                var matches = true;
                for (var i = 0; i < 64 && matches; i++) {
                    matches = captured[2 * i]
                            == Wav.sineSample(FORMAT.sampleRate(), target + candidate + i, 440, 12_000) / 32768f;
                }
                if (matches) {
                    return candidate;
                }
            }
        }
        return range + 1;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tone-opus.mp4", "tone.opus", "tone-opus.webm", "tone.ogg", "tone.mp3"})
    @DisplayName("lossy codecs keep the length, the loudness and the pitch")
    void lossy(String name) {
        var ended = play(name, true);
        var samples = sink.capturedSamples();
        // Encoder delay and padding are trimmed where the container records them;
        // MP3 may keep up to a frame of either.
        assertTrue(Math.abs(samples - FORMAT.sampleRate()) <= 2 * 1152, name + " gave " + samples + " samples");
        assertSine(sink.captured(), samples, name);
        assertEquals("ffmpeg", ended.audioDecoder().orElseThrow());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tone.opus", "tone.ogg", "tone.mp3", "tone-opus.webm"})
    @DisplayName("lossy codecs seek: the tone carries on from about the target")
    void lossySeek(String name) {
        play(name, false);
        player.seek(Duration.ofMillis(400));
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (!(sink.clears() > 0 && sink.capturedSamples() >= FORMAT.sampleRate() / 5)
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Duration.ofMillis(400), player.status().position());
        playToTheEnd();
        // Six tenths of a second remain, give or take the codec's frame.
        var samples = sink.capturedSamples();
        assertTrue(Math.abs(samples - FORMAT.sampleRate() * 6 / 10) <= 2 * 1152, name + " gave " + samples);
        assertSine(sink.captured(), samples, name + " after the seek");
    }

    /// Advances the virtual speaker until the Engine has written and played
    /// everything.
    private void playToTheEnd() {
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (player.status().state() != PlaybackState.ENDED && System.nanoTime() < deadline) {
            sink.playAll();
            Thread.onSpinWait();
        }
        assertEquals(PlaybackState.ENDED, player.status().state());
    }

    /// The middle half of `captured` is the 440 Hz tone at the right loudness.
    private static void assertSine(float[] captured, int samples, String what) {
        var from = samples / 4;
        var to = samples * 3 / 4;
        var energy = 0.0;
        var crossings = 0;
        // With hysteresis: the sign flips only past ±0.02, so a codec's noise near
        // zero does not count as a crossing of its own.
        var positive = captured[2 * from] >= 0;
        for (var i = from; i < to; i++) {
            var left = captured[2 * i];
            energy += left * left;
            if (positive && left < -0.02f || !positive && left > 0.02f) {
                positive = !positive;
                crossings++;
            }
        }
        var rms = Math.sqrt(energy / (to - from));
        assertTrue(Math.abs(rms - RMS) / RMS < 0.1, what + ": RMS " + rms + ", expected about " + RMS);
        // 440 Hz crosses zero 880 times a second, over however long the window is.
        var expected = 880.0 * (to - from) / FORMAT.sampleRate();
        assertTrue(Math.abs(crossings - expected) <= 2, what + ": " + crossings + " crossings, expected " + expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "clip-vp8.webm, VP8, OPUS",
        "clip-vp9.webm, VP9, OPUS",
        "clip-av1.mkv, AV1, OPUS",
        "clip-av1.mp4, AV1, FLAC",
    })
    @DisplayName("probes the video clips: the picture's codec and size, and the sound's codec")
    void probesVideo(String name, CodecId video, CodecId audio) {
        var info = probe(name);
        var picture = info.defaultTrack(MediaType.VIDEO).orElseThrow();
        assertEquals(video, picture.codec());
        var params = assertInstanceOf(TrackParams.Video.class, picture.params());
        assertEquals(160, params.width());
        assertEquals(90, params.height());
        assertEquals(audio, info.defaultTrack(MediaType.AUDIO).orElseThrow().codec());
        assertTrue(MediaCapabilities.current().canDecode(video), video + " is built");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-vp9.webm", "clip-av1.mkv", "clip-av1.mp4"})
    @DisplayName("plays a video clip's sound to the end (the picture is phase 3)")
    void playsVideoSound(String name) {
        play(name, true);
        assertTrue(Math.abs(sink.capturedSamples() - FORMAT.sampleRate()) <= 2 * 1152);
    }

    /// Opens `name` and waits for its error.
    private MediaError error(String name) {
        sink = new VirtualSink(FORMAT, true);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new Fixture(fixture(name))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///" + name)));
        return awaitState(PlaybackState.ERROR).error().orElseThrow();
    }

    @Test
    @DisplayName("an Xvid and AC-3 AVI opens, lists both tracks, and names both codecs it cannot decode (ADR-0471)")
    void aviOfPatentPoolCodecs() {
        var info = probe("clip-xvid-ac3.avi");
        assertEquals("mpeg4", info.defaultTrack(MediaType.VIDEO).orElseThrow().codecName());
        assertEquals("ac3", info.defaultTrack(MediaType.AUDIO).orElseThrow().codecName());
        assertEquals(new MediaError.UnsupportedCodec(List.of("mpeg4", "ac3")), error("clip-xvid-ac3.avi"));
    }

    @Test
    @DisplayName("MP3 in AVI plays to the end: the AVI demuxer is built")
    void mp3InAvi() {
        play("tone-mp3.avi", true);
        assertSine(sink.captured(), sink.capturedSamples(), "MP3 in AVI");
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"clip-mpeg2.ts, MPEG-TS", "clip-flv1.flv, FLV"})
    @DisplayName("a container this build has no demuxer for is named, not called invalid data (ADR-0471)")
    void unsupportedContainer(String name, String format) {
        var error = error(name);
        assertEquals(new MediaError.UnsupportedContainer(format), error);
        assertEquals("no demuxer for " + format + " in this build", error.message());
    }

    @Test
    @DisplayName("bytes that are no container at all are still invalid data")
    void notMedia() {
        sink = new VirtualSink(FORMAT, true);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new Fixture(
                        "These are notes about music, not music.\n".repeat(200).getBytes(StandardCharsets.UTF_8))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///notes.bin")));
        assertInstanceOf(
                MediaError.InvalidData.class,
                awaitState(PlaybackState.ERROR).error().orElseThrow());
    }

    @Test
    @DisplayName("finds the cover art of an MP3, and never plays it as the video")
    void coverArt() {
        var info = probe("tone-cover.mp3");
        var cover = info.attachedPicture().orElseThrow();
        assertEquals(CodecId.PNG, cover.codec());
        assertTrue(info.defaultTrack(MediaType.VIDEO).isEmpty());
        play("tone-cover.mp3", true);
    }

    @Test
    @DisplayName("an H.264/AAC MP4 opens, lists both tracks, and fails with UnsupportedCodec naming both (S7)")
    void patentPool() {
        var info = probe("clip-h264-aac.mp4");
        assertEquals(
                CodecId.H264, info.defaultTrack(MediaType.VIDEO).orElseThrow().codec());
        assertEquals(
                CodecId.AAC, info.defaultTrack(MediaType.AUDIO).orElseThrow().codec());

        sink = new VirtualSink(FORMAT, true);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new Fixture(fixture("clip-h264-aac.mp4"))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///clip-h264-aac.mp4")));
        var failed = awaitState(PlaybackState.ERROR);
        // Both, video first: every track about to play is checked before any
        // plays, so the error is the whole answer and not the first half of it.
        assertEquals(
                new MediaError.UnsupportedCodec(List.of("h264", "aac")),
                failed.error().orElseThrow());
        assertTrue(failed.info().isPresent(), "the tracks are still listed");
    }
}
