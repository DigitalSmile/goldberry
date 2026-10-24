package dev.goldberry.media.decoder.opus;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.decoder.FrameHashes;
import dev.goldberry.media.ffm.MediaNatives;

/// libopus through the SPI. The clips are a 440 Hz sine at 12000/32768, so
/// what comes out is checked as a signal: its length, its loudness and its
/// pitch, the way the Engine's own fixture test checks a lossy codec.
@DisplayName("OpusDecoder")
class OpusDecoderTest {

    private static final int RATE = 48_000;
    private static final double RMS = 12_000d / 32_768 / Math.sqrt(2);

    private final OpusProvider provider = new OpusProvider();

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    /// Every frame of `name`'s first audio track, with its samples copied out.
    private record Decoded(long ptsNanos, int channels, float[] samples) {}

    private List<Decoded> decode(String name) {
        var frames = new ArrayList<Decoded>();
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.AUDIO).orElseThrow();
            container.select(Set.of(track.index()));
            var request = container.request(track.index());
            assertTrue(provider.supports(request));
            try (var decoder = provider.open(request)) {
                FrameHashes.drain(container, decoder, frame -> {
                    var audio = assertInstanceOf(AudioFrame.class, frame);
                    assertEquals(SampleFormat.F32, audio.format());
                    assertEquals(RATE, audio.sampleRate());
                    var plane = audio.planes().getFirst();
                    var samples = plane.asSlice(0, (long) audio.samples() * audio.channels() * Float.BYTES)
                            .toArray(JAVA_FLOAT);
                    frames.add(new Decoded(audio.ptsNanos(), audio.channels(), samples));
                });
            }
        }
        return frames;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tone-opus.webm", "clip-av1.mkv", "clip-vp8.webm"})
    @DisplayName("decodes a second of the tone: 48 000 samples within a frame, the right loudness and pitch")
    void decodesTheTone(String name) {
        var frames = decode(name);
        assertFalse(frames.isEmpty());
        var channels = frames.getFirst().channels();
        assertEquals(2, channels);
        var total = frames.stream().mapToInt(f -> f.samples().length / channels).sum();
        assertTrue(Math.abs(total - RATE) <= 2 * 960, name + " gave " + total + " samples");

        // The first frame starts where the stream does, the pre-skip dropped:
        // within a millisecond of zero, the container's rounding.
        assertTrue(
                Math.abs(frames.getFirst().ptsNanos()) < 1_000_000,
                "first pts " + frames.getFirst().ptsNanos());

        var left = new float[total];
        var at = 0;
        for (var frame : frames) {
            for (var i = 0; i < frame.samples().length / channels; i++) {
                left[at++] = frame.samples()[i * channels];
            }
        }
        // Skip the first 20 ms, where the encoder is still converging.
        var from = 960;
        var sum = 0d;
        var crossings = 0;
        for (var i = from; i < total; i++) {
            sum += left[i] * left[i];
            if (i > from && (left[i - 1] < 0) != (left[i] < 0)) {
                crossings++;
            }
        }
        var rms = Math.sqrt(sum / (total - from));
        assertTrue(Math.abs(rms - RMS) / RMS < 0.1, name + ": RMS " + rms + ", expected about " + RMS);
        var expected = 2 * 440 * (total - from) / RATE;
        assertTrue(Math.abs(crossings - expected) <= 6, name + ": " + crossings + " crossings, expected " + expected);
    }

    @Test
    @DisplayName("reads the OpusHead: stereo, a pre-skip of 312, no gain")
    void readsTheHead() {
        var name = "tone-opus.webm";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            try (var decoder = (OpusDecoder) provider.open(container.request(0))) {
                var head = decoder.head();
                assertEquals(2, head.channels());
                assertEquals(312, head.preSkip());
                assertEquals(0, head.gain());
                assertTrue(head.isSingleStream());
            }
        }
    }

    @Test
    @DisplayName("a flush drops the pre-skip again, and decodes on")
    void flushes() {
        var name = "tone-opus.webm";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            try (var decoder = provider.open(container.request(0))) {
                var before = new ArrayList<Long>();
                FrameHashes.drain(container, decoder, frame -> before.add(frame.ptsNanos()));
                container.seek(0);
                decoder.flush();
                var after = new ArrayList<Long>();
                FrameHashes.drain(container, decoder, frame -> after.add(frame.ptsNanos()));
                assertEquals(before, after);
            }
        }
    }

    @Test
    @DisplayName("the provider is opus, for Opus alone, and refuses a record that is not an OpusHead")
    void provider() {
        assertEquals("opus", provider.name());
        assertTrue(MediaNatives.isAvailable());
        var name = "clip-av1.mkv";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            assertFalse(provider.supports(container.request(0)), "AV1 is not Opus");
        }
        assertThrows(IllegalArgumentException.class, () -> OpusHead.parse(new byte[] {1, 2, 3}));
        assertEquals(1, OpusHead.plain(1).channels());
    }
}
