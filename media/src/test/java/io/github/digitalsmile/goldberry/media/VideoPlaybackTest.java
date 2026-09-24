package io.github.digitalsmile.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.VirtualSink;
import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.codec.Packet;
import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.SampleFormat;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// The Engine playing video, end to end against FFmpeg: phase 3's exit criteria
/// (`docs/goldberry-media.md` §8) and the scenarios they name.
///
/// - **S5**, deterministic goldens: the master clock is moved by hand, through
///   a [VirtualSink] where the source has audio and a [MediaClock] where it does
///   not, and the picture shown at each time is compared byte for byte.
/// - **S2**, scrub and release: keyframe seeks while paused show the keyframe,
///   and the accurate seek on release shows the picture that covers the target.
/// - **S7** is in `CodecFixturesTest`; **S8**, bring your own codec, is here with
///   a fake H.264 and AAC provider.
/// - **Track switching** (§6, ADR-0469): `clip-two-angles.mkv` has the VP9 clip
///   at 160×90 and SMPTE bars in VP8 at 96×54, so which track shows is the
///   picture's width.
///
/// The clips are `fixtures/`'s `testsrc2` at 160×90, 25 fps: one picture every
/// 40 ms, and a single keyframe at zero.
@DisplayName("MediaPlayer playing video, against FFmpeg")
class VideoPlaybackTest {

    private static final AudioFormat FORMAT = AudioFormat.DEFAULT;
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final long FRAME = 40_000_000L;

    /// Serves one fixture under `mem:`.
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

    static byte[] fixture(String name) {
        try (var in = VideoPlaybackTest.class.getResourceAsStream("fixtures/" + name)) {
            if (in == null) {
                throw new IllegalStateException("no fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void open(String name, boolean instant, MediaClock clock, List<? extends DecoderProvider> providers) {
        sink = new VirtualSink(FORMAT, instant);
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> sink)
                .clock(clock)
                .ioProviders(List.of(new Fixture(fixture(name))))
                .decoderProviders(providers)
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

    private void open(String name, boolean instant) {
        open(name, instant, MediaClock.system(), List.of());
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
        return awaitPicture(ptsNanos, picture -> true);
    }

    /// The picture shown once it is the one at `ptsNanos` and `wanted` holds of it.
    private VideoPicture awaitPicture(long ptsNanos, Predicate<VideoPicture> wanted) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        VideoPicture last = null;
        while (System.nanoTime() < deadline) {
            var picture = player.currentPicture();
            if (picture.isPresent()) {
                last = picture.get();
                if (last.ptsNanos() == ptsNanos && wanted.test(last)) {
                    return last;
                }
            }
            sleep();
        }
        throw new AssertionError(
                "no picture at " + ptsNanos + " ns; the last was " + last + ", status " + player.status());
    }

    /// A picture of the `Wide` track of `clip-two-angles.mkv`.
    private static boolean wide(VideoPicture picture) {
        return picture.width() == 160 && picture.height() == 90;
    }

    /// A picture of its `Close` track.
    private static boolean close(VideoPicture picture) {
        return picture.width() == 96 && picture.height() == 54;
    }

    /// Moves the audio clock to `nanos`, by letting the virtual speaker play up to
    /// it. The sink holds a fifth of a second, so this waits for the Engine to
    /// write each step before playing it. Like a device, it plays nothing while
    /// the Engine holds the sink paused.
    private void playAudioTo(long nanos) {
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
        assertEquals(target, played, "the Engine did not write enough audio to reach " + nanos + " ns");
    }

    private static void sleep() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("plays audio and video to the end, the picture handing over to a free clock when the sound ends")
    void playsToTheEnd() {
        // An instant sink plays all the audio at once, so the picture outlives the
        // sound and runs out on the system clock from where the sound stopped.
        open("clip-vp9.webm", true);
        var ended = await(status -> status.state() == PlaybackState.ENDED);
        assertEquals(
                List.of(PlaybackState.OPENING, PlaybackState.BUFFERING, PlaybackState.PLAYING, PlaybackState.ENDED),
                List.copyOf(states));
        assertEquals("ffmpeg", ended.audioDecoder().orElseThrow());
        assertEquals("ffmpeg", ended.videoDecoder().orElseThrow());
        assertTrue(ended.hasVideo());
        assertEquals(24 * FRAME, player.currentPicture().orElseThrow().ptsNanos(), "the last picture stays up");
    }

    @ParameterizedTest(name = "{0} at {1} ms")
    @CsvSource({
        "clip-vp8.webm, 0",
        "clip-vp8.webm, 400",
        "clip-vp9.webm, 0",
        "clip-vp9.webm, 400",
        "clip-vp9.webm, 960",
        "clip-av1.mkv, 0",
        "clip-av1.mkv, 400",
    })
    @DisplayName("shows the same bytes at the same time on the audio clock (S5)")
    void goldenOnTheAudioClock(String name, int millis) {
        open(name, false);
        await(status -> status.state() == PlaybackState.PLAYING);
        // Half a picture in, so the Opus track's 7 ms of pre-roll cannot put the
        // clock a hair short of the picture being asked for.
        playAudioTo(millis * 1_000_000L + FRAME / 2);
        var picture = awaitPicture(millis * 1_000_000L);
        PictureGolden.assertExact(name.replace('.', '-') + "-" + millis + "ms", picture);
    }

    @Test
    @DisplayName("times a source with no audio on the MediaClock, and ends when the last picture has had its time (S5)")
    void videoOnlyOnAVirtualClock() {
        var now = new AtomicLong(1_000_000_000L);
        open("clip-vp9-10bit.webm", false, now::get, List.of());
        await(status -> status.state() == PlaybackState.PLAYING);
        assertEquals(0, awaitPicture(0).ptsNanos());
        assertEquals(Duration.ZERO, player.status().position(), "the clock waits for the hand that moves it");

        now.addAndGet(2 * FRAME + FRAME / 2);
        PictureGolden.assertExact("clip-vp9-10bit-webm-80ms", awaitPicture(2 * FRAME));
        assertEquals(Duration.ofNanos(2 * FRAME + FRAME / 2), player.status().position());

        player.pause();
        now.addAndGet(10 * FRAME);
        assertEquals(Duration.ofNanos(2 * FRAME + FRAME / 2), player.status().position(), "paused, time stands");
        player.play();
        now.addAndGet(3 * FRAME);
        await(status -> status.state() == PlaybackState.ENDED);
        assertEquals(4 * FRAME, player.currentPicture().orElseThrow().ptsNanos());
    }

    @Test
    @DisplayName("with no audio, at twice the speed, the clock counts twice the time the MediaClock passes")
    void videoOnlyAtARate() {
        var now = new AtomicLong(1_000_000_000L);
        open("clip-vp9-10bit.webm", false, now::get, List.of());
        await(status -> status.state() == PlaybackState.PLAYING);
        player.setRate(2f);
        assertEquals(2f, player.status().rate());
        now.addAndGet(FRAME);
        awaitPicture(2 * FRAME);
        assertEquals(Duration.ofNanos(2 * FRAME), player.status().position());
    }

    @Test
    @DisplayName("steps a picture at a time, on and back, paused, and no further than the last picture")
    void frameStep() {
        open("clip-vp9.webm", false);
        await(status -> status.state() == PlaybackState.PLAYING);
        player.pause();
        awaitPicture(0);

        assertTrue(player.step(1));
        awaitPicture(FRAME);
        assertEquals(Duration.ofNanos(FRAME), player.status().position());
        assertEquals(PlaybackState.PAUSED, player.status().state());

        player.step(3);
        awaitPicture(4 * FRAME);
        player.step(-2);
        awaitPicture(2 * FRAME);
        player.step(-10);
        awaitPicture(0);
        assertEquals(Duration.ZERO, player.status().position());

        // The clip is 1.008 s: the last picture starts at 960 ms.
        player.step(1_000);
        awaitPicture(24 * FRAME);
        assertEquals(PlaybackState.PAUSED, player.status().state());
    }

    @Test
    @DisplayName("a step while playing pauses first")
    void stepWhilePlaying() {
        open("clip-vp9.webm", false);
        await(status -> status.state() == PlaybackState.PLAYING);
        awaitPicture(0);
        player.step(2);
        assertEquals(PlaybackState.PAUSED, player.status().state());
        awaitPicture(2 * FRAME);
    }

    @Test
    @DisplayName("an accurate seek shows the picture that covers the target, even while paused")
    void accurateSeek() {
        open("clip-vp9.webm", false);
        await(status -> status.state() == PlaybackState.PLAYING);
        player.pause();
        player.seek(Duration.ofMillis(500));
        assertEquals(Duration.ofMillis(500), player.status().position());
        // Pictures at 480 and 520 ms: 480 is the one showing at 500.
        awaitPicture(12 * FRAME);
        assertEquals(PlaybackState.PAUSED, player.status().state());
        assertEquals(Duration.ofMillis(500), player.status().position());
    }

    @Test
    @DisplayName("scrubbing shows each keyframe, the release shows the target, and play goes on from there (S2)")
    void scrubAndRelease() {
        open("clip-vp9.webm", false);
        await(status -> status.state() == PlaybackState.PLAYING);
        playAudioTo(3 * FRAME);
        awaitPicture(3 * FRAME);
        player.pause();

        // Dragging: keyframe seeks, coalesced. The clip's one keyframe is at zero.
        for (var millis : List.of(200, 450, 700)) {
            player.seek(Duration.ofMillis(millis), SeekMode.KEYFRAME);
        }
        awaitPicture(0);
        assertEquals(Duration.ofMillis(700), player.status().position());

        // Released: the accurate seek.
        player.seek(Duration.ofMillis(730), SeekMode.ACCURATE);
        awaitPicture(18 * FRAME);
        assertEquals(PlaybackState.PAUSED, player.status().state());

        // Playing again: the clock runs on from the target, and so do the pictures.
        player.play();
        playAudioTo(4 * FRAME + FRAME / 2);
        awaitPicture(18 * FRAME + 4 * FRAME);
    }

    @Test
    @DisplayName("seeking after the end, paused, shows the target; play runs to the end again")
    void seekAfterEnd() {
        open("clip-vp9.webm", true);
        await(status -> status.state() == PlaybackState.ENDED);
        // Paused first: an instant sink would otherwise play past the target
        // before the picture could be looked at.
        player.pause();
        player.seek(Duration.ofMillis(100));
        awaitPicture(2 * FRAME);
        assertEquals(PlaybackState.PAUSED, player.status().state());
        player.play();
        await(status -> status.state() == PlaybackState.ENDED);
    }

    @Test
    @DisplayName("switches the video track mid-play: the new track's picture at the position, on the same clock")
    void switchVideoTrack() {
        open("clip-two-angles.mkv", false);
        var playing = await(status -> status.state() == PlaybackState.PLAYING);
        var tracks = playing.info().orElseThrow().tracks(MediaType.VIDEO);
        assertEquals(2, tracks.size());
        var wide = tracks.get(0);
        var close = tracks.get(1);
        assertEquals(Optional.of("Wide"), wide.title());
        assertEquals(Optional.of("Close"), close.title());
        assertEquals(Optional.of(wide), playing.videoTrack());

        playAudioTo(10 * FRAME + FRAME / 2);
        awaitPicture(10 * FRAME, VideoPlaybackTest::wide);

        // The switch seeks to where the clock is, 420 ms: the new track comes in
        // on the picture that covers it, and the clock goes on from there. Until
        // the audio thread takes the seek's flush, the sink still plays what it
        // held, so the speaker is moved on only after the sink has been cleared.
        var clears = sink.clears();
        player.selectTrack(close);
        await(status -> status.videoTrack().equals(Optional.of(close)) && sink.clears() > clears);
        awaitPicture(10 * FRAME, VideoPlaybackTest::close);
        assertEquals(PlaybackState.PLAYING, player.status().state());
        // Within Matroska's millisecond, as any accurate seek of its Opus.
        var drift =
                player.status().position().minusNanos(10 * FRAME + FRAME / 2).abs();
        assertTrue(drift.compareTo(Duration.ofMillis(1)) <= 0, "the clock moved " + drift + " on the switch");
        assertEquals("ffmpeg", player.status().videoDecoder().orElseThrow());
        playAudioTo(4 * FRAME);
        awaitPicture(14 * FRAME, VideoPlaybackTest::close);

        // Asking for the track that shows changes nothing.
        player.selectTrack(close);
        assertEquals(Optional.of(close), player.status().videoTrack());

        // And plays to the end on the new track.
        while (player.status().state() != PlaybackState.ENDED) {
            sink.advance(sink.queuedSamples());
            sleep();
        }
        assertTrue(close(player.currentPicture().orElseThrow()));
        assertEquals(24 * FRAME, player.currentPicture().orElseThrow().ptsNanos());
    }

    @Test
    @DisplayName("switches the video track while paused: the new track's picture shows, and it stays paused")
    void switchVideoTrackPaused() {
        open("clip-two-angles.mkv", false);
        await(status -> status.state() == PlaybackState.PLAYING);
        playAudioTo(5 * FRAME + FRAME / 2);
        awaitPicture(5 * FRAME, VideoPlaybackTest::wide);
        player.pause();

        var close = player.status().info().orElseThrow().tracks(MediaType.VIDEO).get(1);
        player.selectTrack(close);
        awaitPicture(5 * FRAME, VideoPlaybackTest::close);
        assertEquals(PlaybackState.PAUSED, player.status().state());

        // A frame step moves through the new track's pictures.
        assertTrue(player.step(2));
        awaitPicture(7 * FRAME, VideoPlaybackTest::close);

        // And back again, still paused.
        var wide = player.status().info().orElseThrow().tracks(MediaType.VIDEO).get(0);
        player.selectTrack(wide);
        awaitPicture(7 * FRAME, VideoPlaybackTest::wide);
        assertEquals(PlaybackState.PAUSED, player.status().state());
        assertEquals(Optional.of(wide), player.status().videoTrack());
    }

    @Test
    @DisplayName("hardware decoding is AUTO unless built otherwise, and a change applies from the next source")
    void hardwareDecodingSetting() {
        sink = new VirtualSink(FORMAT, true);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new Fixture(fixture("clip-vp9.webm"))))
                .decoderProviders(List.of())
                .build();
        assertEquals(HardwareDecoding.AUTO, player.hardwareDecoding());
        player.setHardwareDecoding(HardwareDecoding.OFF);
        assertEquals(HardwareDecoding.OFF, player.hardwareDecoding());
        player.open(Source.of(URI.create("mem:///clip-vp9.webm")));
        var ended = await(status -> status.state() == PlaybackState.ENDED);
        assertEquals("ffmpeg", ended.videoDecoder().orElseThrow(), "decoded in software, as set before the open");
        assertThrows(NullPointerException.class, () -> player.setHardwareDecoding(null));
    }

    @Test
    @DisplayName("cover art cannot be chosen as the video track")
    void coverArtCannotBeChosen() {
        open("tone-cover.mp3", false);
        var playing = await(status -> status.state() == PlaybackState.PLAYING);
        var cover = playing.info().orElseThrow().attachedPicture().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> player.selectTrack(cover));
        assertEquals(Optional.empty(), player.status().videoTrack());
    }

    @Test
    @DisplayName("cover art is not played as video: an MP3 with a picture is audio only")
    void coverArtIsNotVideo() {
        open("tone-cover.mp3", true);
        var ended = await(status -> status.state() == PlaybackState.ENDED);
        assertTrue(!ended.hasVideo());
        assertTrue(ended.videoDecoder().isEmpty());
        assertTrue(player.currentPicture().isEmpty());
    }

    @Test
    @DisplayName("a provider decodes a codec the natives do not build, and the Engine presents it unchanged (S8)")
    void bringYourOwnCodec() {
        open("clip-h264-aac.mp4", true, MediaClock.system(), List.of(new FakeProvider(Integer.MAX_VALUE)));
        var ended = await(status -> status.state() == PlaybackState.ENDED);
        assertEquals("fake-h264", ended.videoDecoder().orElseThrow());
        assertEquals("fake-h264", ended.audioDecoder().orElseThrow());
        var picture = player.currentPicture().orElseThrow();
        assertEquals(160, picture.width());
        // Mid-grey in, mid-grey out: Y=128, U=V=128 in limited range is RGB 130.
        var argb = picture.argb(80, 45);
        assertEquals(0xFF, argb >>> 24);
        assertEquals((argb >> 16) & 0xFF, argb & 0xFF);
        assertTrue(Math.abs(((argb >> 8) & 0xFF) - 130) <= 2, Integer.toHexString(argb));
    }

    @Test
    @DisplayName("a provider failing mid-stream, with nothing after it, is UnsupportedCodec naming the codec (S8)")
    void providerFailsMidStream() {
        open("clip-h264-aac.mp4", false, MediaClock.system(), List.of(new FakeProvider(3)));
        var failed = await(status -> status.state() == PlaybackState.ERROR);
        assertEquals(
                new MediaError.UnsupportedCodec(List.of("h264")), failed.error().orElseThrow());
    }

    /// A test provider for H.264 and AAC: grey pictures and silence, one per
    /// packet, timed by the packets. The video decoder throws after `good`
    /// packets.
    static final class FakeProvider implements DecoderProvider {
        private final int good;

        FakeProvider(int good) {
            this.good = good;
        }

        @Override
        public String name() {
            return "fake-h264";
        }

        @Override
        public boolean supports(DecoderRequest request) {
            return request.codec() == CodecId.H264 || request.codec() == CodecId.AAC;
        }

        @Override
        public Decoder open(DecoderRequest request) {
            return request.params().type() == MediaType.VIDEO ? new Pictures(good) : new Silence();
        }
    }

    /// One grey 160×90 I420 picture per packet.
    private static final class Pictures implements Decoder {
        private final Arena arena = Arena.ofShared();
        private final MemorySegment y = arena.allocate(160 * 90).fill((byte) 128);
        private final MemorySegment u = arena.allocate(80 * 45).fill((byte) 128);
        private final MemorySegment v = arena.allocate(80 * 45).fill((byte) 128);
        private final int good;
        private int sent;
        private long pending = Frame.NO_PTS;
        private boolean ending;

        Pictures(int good) {
            this.good = good;
        }

        @Override
        public boolean send(Packet packet) {
            if (pending != Frame.NO_PTS) {
                return false;
            }
            if (++sent > good) {
                throw new IllegalStateException("the fake decoder gives up");
            }
            pending = packet.ptsNanos();
            return true;
        }

        @Override
        public void sendEnd() {
            ending = true;
        }

        @Override
        public Received receive() {
            if (pending != Frame.NO_PTS) {
                var pts = pending;
                pending = Frame.NO_PTS;
                return new Received.Decoded(new VideoFrame(
                        PixelFormat.I420,
                        160,
                        90,
                        List.of(y, u, v),
                        List.of(160, 80, 80),
                        VideoFrame.ColorMatrix.BT601,
                        false,
                        pts));
            }
            return ending ? Received.ENDED : Received.NEEDS_INPUT;
        }

        @Override
        public void flush() {
            pending = Frame.NO_PTS;
            ending = false;
        }

        @Override
        public void close() {
            arena.close();
        }
    }

    /// 1024 samples of stereo silence per packet.
    private static final class Silence implements Decoder {
        private final Arena arena = Arena.ofShared();
        private final MemorySegment plane = arena.allocate(4L * 2 * 1024);
        private long pending = Frame.NO_PTS;
        private boolean ending;

        @Override
        public boolean send(Packet packet) {
            if (pending != Frame.NO_PTS) {
                return false;
            }
            pending = packet.ptsNanos();
            return true;
        }

        @Override
        public void sendEnd() {
            ending = true;
        }

        @Override
        public Received receive() {
            if (pending != Frame.NO_PTS) {
                var pts = pending;
                pending = Frame.NO_PTS;
                return new Received.Decoded(new AudioFrame(SampleFormat.F32, 48_000, 2, 1024, List.of(plane), pts));
            }
            return ending ? Received.ENDED : Received.NEEDS_INPUT;
        }

        @Override
        public void flush() {
            pending = Frame.NO_PTS;
            ending = false;
        }

        @Override
        public void close() {
            arena.close();
        }
    }
}
