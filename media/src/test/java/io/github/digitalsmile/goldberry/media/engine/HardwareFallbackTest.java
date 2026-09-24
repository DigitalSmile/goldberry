package io.github.digitalsmile.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.FfmpegRequirement;
import io.github.digitalsmile.goldberry.media.HardwareDecoding;
import io.github.digitalsmile.goldberry.media.MediaClock;
import io.github.digitalsmile.goldberry.media.PictureGolden;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.VideoPicture;
import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.VirtualSink;
import io.github.digitalsmile.goldberry.media.ffi.Ffmpeg;
import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibraries;
import io.github.digitalsmile.goldberry.media.ffi.Hardware;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// The Engine on a hardware decoder, and its fall to software: scenario S4 of
/// `docs/goldberry-media.md` §7, with the failures injected through
/// [Hardware.Calls] (ADR-0470).
///
/// It drives a [Playback] directly, since the player's public options name
/// [HardwareDecoding] and not a policy a test can make fail. The clip is the VP9
/// one, 25 pictures of `testsrc2` at 160×90 with one keyframe at zero, so a
/// software decoder that took over mid-stream without going back to that
/// keyframe could show no whole picture at all. Its goldens are the ones
/// `VideoPlaybackTest` compares against.
///
/// Where the device does not decode VP9 (no hwaccel in the build, or none on the
/// machine), the tests skip.
@DisplayName("The Engine on a hardware decoder, and its fall to software (S4)")
class HardwareFallbackTest {

    private static final AudioFormat FORMAT = AudioFormat.DEFAULT;
    private static final long FRAME = 40_000_000L;
    private static final long WAIT_NANOS = 10_000_000_000L;

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

    private final List<String> decoders = Collections.synchronizedList(new ArrayList<>());
    private final List<PlaybackState> states = Collections.synchronizedList(new ArrayList<>());
    private Ffmpeg ffmpeg;
    private Playback playback;
    private VirtualSink sink;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
        ffmpeg = FfmpegLibraries.get();
    }

    @AfterEach
    void close() {
        if (playback != null) {
            playback.close();
        }
    }

    /// A policy of this platform's device types, with a record of its own.
    private static Hardware platform(Hardware.Calls calls) {
        return new Hardware(Hardware.of(HardwareDecoding.AUTO).deviceTypes(), calls);
    }

    /// Starts `clip-vp9.webm` on `hardware`, recording every video decoder name
    /// and state it goes through.
    private void play(Hardware hardware, boolean instant) {
        close();
        decoders.clear();
        states.clear();
        sink = new VirtualSink(FORMAT, instant);
        playback = new Playback(
                ffmpeg,
                Source.of(URI.create("mem:///clip-vp9.webm")),
                List.of(new Fixture(fixture())),
                List.of(),
                hardware,
                sink,
                MediaClock.system(),
                Playback.HIGH_WATER_NANOS,
                changed -> {
                    var name = changed.videoDecoderName();
                    synchronized (decoders) {
                        if (name != null
                                && (decoders.isEmpty() || !decoders.getLast().equals(name))) {
                            decoders.add(name);
                        }
                    }
                    synchronized (states) {
                        if (states.isEmpty() || states.getLast() != changed.state()) {
                            states.add(changed.state());
                        }
                    }
                });
        playback.start();
    }

    private static byte[] fixture() {
        try (var in = HardwareFallbackTest.class.getResourceAsStream(
                "/io/github/digitalsmile/goldberry/media/fixtures/clip-vp9.webm")) {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void await(BooleanSupplier condition, String what) {
        var deadline = System.nanoTime() + WAIT_NANOS;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for " + what + "; state " + playback.state() + ", states "
                        + states + ", decoders " + decoders);
            }
            sleep();
        }
    }

    /// Lets the virtual speaker play until the position reaches `nanos`. Reads
    /// the position rather than counting samples, because a fallback's seek
    /// clears the sink and starts the count again.
    private void playTo(long nanos) {
        await(
                () -> {
                    var position = playback.positionNanos();
                    if (position >= nanos) {
                        return true;
                    }
                    if (!sink.paused()) {
                        sink.advance(Math.min(sink.queuedSamples(), Math.max(FORMAT.samples(nanos - position), 1)));
                    }
                    return false;
                },
                "the position to reach " + nanos + " ns");
    }

    /// The picture shown once it is the one at `ptsNanos`.
    private VideoPicture awaitPicture(long ptsNanos) {
        var shown = new VideoPicture[1];
        await(
                () -> {
                    var picture = playback.currentPicture();
                    shown[0] = picture.orElse(null);
                    return picture.isPresent() && picture.get().ptsNanos() == ptsNanos;
                },
                "the picture at " + ptsNanos + " ns");
        return shown[0];
    }

    private static void sleep() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /// Skips unless VP9 plays on this machine's device.
    private void assumeVp9OnTheDevice() {
        Assumptions.assumeTrue(Hardware.of(HardwareDecoding.AUTO).enabled(), "no device type on this platform");
        play(platform(Hardware.Calls.FFMPEG), true);
        await(() -> playback.state() == PlaybackState.ENDED, "the end");
        Assumptions.assumeTrue(
                decoders.getFirst().startsWith("ffmpeg ("), "VP9 did not play on the device here: " + decoders);
    }

    @Test
    @DisplayName("plays on the device, named for it, and its pictures are the software goldens")
    void playsOnTheDevice() {
        assumeVp9OnTheDevice();
        assertEquals(1, decoders.size(), "one decoder all the way: " + decoders);
        assertEquals(
                List.of(PlaybackState.BUFFERING, PlaybackState.PLAYING, PlaybackState.ENDED),
                states.stream().filter(state -> state != PlaybackState.OPENING).toList());

        play(platform(Hardware.Calls.FFMPEG), false);
        await(() -> playback.state() == PlaybackState.PLAYING, "playing");
        playTo(10 * FRAME + FRAME / 2);
        PictureGolden.assertExact("clip-vp9-webm-400ms", awaitPicture(10 * FRAME));
    }

    @Test
    @DisplayName("copy-back failing mid-stream falls to software from the keyframe, with no error and whole pictures")
    void copyBackFailsMidStream() {
        assumeVp9OnTheDevice();
        var copies = new AtomicInteger();
        play(
                platform(new Hardware.Calls() {
                    @Override
                    public int createDevice(Ffmpeg ffmpeg, MemorySegment holder, int type) {
                        return Hardware.Calls.FFMPEG.createDevice(ffmpeg, holder, type);
                    }

                    @Override
                    public int transfer(Ffmpeg ffmpeg, MemorySegment destination, MemorySegment source) {
                        return copies.incrementAndGet() > 6
                                ? ffmpeg.constants().averrorEio()
                                : Hardware.Calls.FFMPEG.transfer(ffmpeg, destination, source);
                    }
                }),
                false);
        await(() -> playback.state() == PlaybackState.PLAYING, "playing");
        playTo(3 * FRAME + FRAME / 2);
        awaitPicture(3 * FRAME);
        playTo(10 * FRAME + FRAME / 2);
        await(() -> decoders.size() == 2, "the fall to software");
        assertTrue(decoders.getFirst().startsWith("ffmpeg ("), decoders.toString());
        assertEquals("ffmpeg", decoders.getLast());

        // The software decoder went back to the keyframe at zero: its pictures are
        // whole, byte for byte the goldens.
        PictureGolden.assertExact("clip-vp9-webm-400ms", awaitPicture(10 * FRAME));
        while (playback.state() != PlaybackState.ENDED) {
            sink.advance(sink.queuedSamples());
            sleep();
        }
        assertFalse(states.contains(PlaybackState.ERROR), states.toString());
        PictureGolden.assertExact(
                "clip-vp9-webm-960ms", playback.currentPicture().orElseThrow());
    }

    @Test
    @DisplayName("a device that will not open is software from the first picture, and nothing else changes")
    void deviceWillNotOpen() {
        Assumptions.assumeTrue(Hardware.of(HardwareDecoding.AUTO).enabled(), "no device type on this platform");
        var asked = new AtomicInteger();
        play(
                platform(new Hardware.Calls() {
                    @Override
                    public int createDevice(Ffmpeg ffmpeg, MemorySegment holder, int type) {
                        asked.incrementAndGet();
                        return ffmpeg.constants().averrorEio();
                    }

                    @Override
                    public int transfer(Ffmpeg ffmpeg, MemorySegment destination, MemorySegment source) {
                        throw new AssertionError("no device, nothing to copy back");
                    }
                }),
                true);
        await(() -> playback.state() == PlaybackState.ENDED, "the end");
        Assumptions.assumeTrue(asked.get() > 0, "this build has no hardware path for VP9");
        assertEquals(List.of("ffmpeg"), decoders);
        assertFalse(states.contains(PlaybackState.ERROR), states.toString());
        assertEquals(24 * FRAME, playback.currentPicture().orElseThrow().ptsNanos());
    }
}
