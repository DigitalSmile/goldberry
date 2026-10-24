package dev.goldberry.media.decoder.mp3;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.container.mp3.Mp3Container;
import dev.goldberry.media.ffm.MediaNatives;

/// minimp3 through the SPI, fed by the MP3 demuxer. The clips are a 440 Hz
/// sine at 12000/32768, so what comes out is checked as a signal: its length,
/// its loudness and its pitch.
@DisplayName("Mp3Decoder")
class Mp3DecoderTest {

    private static final int RATE = 48_000;
    private static final int FRAME = 1152;
    private static final double RMS = 12_000d / 32_768 / Math.sqrt(2);

    private final Mp3Provider provider = new Mp3Provider();

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    /// One frame, with its samples copied out.
    private record Decoded(long ptsNanos, int channels, float[] samples) {}

    /// Runs the SPI's loop to the end.
    private static void drain(Container container, Decoder decoder, Consumer<Frame> consumer) {
        var ended = false;
        while (!ended) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> consumer.accept(frame);
                case Received.NeedsInput _ -> {
                    try (var packet = container.read()) {
                        if (packet == null) {
                            decoder.sendEnd();
                        } else {
                            assertTrue(decoder.send(packet), "a decoder that asked for input takes the packet");
                        }
                    }
                }
                case Received.Ended _ -> ended = true;
            }
        }
    }

    private static Decoded copy(Frame frame) {
        var audio = assertInstanceOf(AudioFrame.class, frame);
        assertEquals(SampleFormat.F32, audio.format());
        assertEquals(RATE, audio.sampleRate());
        var plane = audio.planes().getFirst();
        var samples = plane.asSlice(0, (long) audio.samples() * audio.channels() * Float.BYTES)
                .toArray(JAVA_FLOAT);
        return new Decoded(audio.ptsNanos(), audio.channels(), samples);
    }

    private List<Decoded> decode(String name) {
        var frames = new ArrayList<Decoded>();
        try (var container = Mp3Container.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.AUDIO).orElseThrow();
            container.select(Set.of(track.index()));
            var request = container.request(track.index());
            assertTrue(provider.supports(request));
            try (var decoder = provider.open(request)) {
                drain(container, decoder, frame -> frames.add(copy(frame)));
            }
        }
        return frames;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tone.mp3", "tone-cover.mp3"})
    @DisplayName("decodes a second of the tone: 48 000 samples within two frames, the right loudness and pitch")
    void decodesTheTone(String name) {
        var frames = decode(name);
        assertFalse(frames.isEmpty());
        var channels = frames.getFirst().channels();
        assertEquals(2, channels);
        // FFmpeg hands over 48 000 samples: the 43 frames' 49 536 less the
        // encoder's delay and the decoder's own at the start, 1105, and the
        // padding after the music, 431. The demuxer trims the padding through
        // the last packet's duration, and times the start before zero, where
        // the Engine drops it; a decoder alone hands it over.
        var total = frames.stream().mapToInt(f -> f.samples().length / channels).sum();
        assertTrue(Math.abs(total - RATE) <= 2 * FRAME, name + " gave " + total + " samples");
        assertTrue(
                Math.abs(frames.getFirst().ptsNanos()) < FRAME * 1_000_000_000L / RATE,
                "first pts " + frames.getFirst().ptsNanos());

        var left = new float[total];
        var at = 0;
        for (var frame : frames) {
            for (var i = 0; i < frame.samples().length / channels; i++) {
                left[at++] = frame.samples()[i * channels];
            }
        }
        // Skip the first frame, where the encoder's delay and the decoder's
        // warm-up are.
        var from = FRAME;
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
    @DisplayName("a flush starts the decoder over: the same first frame comes out again")
    void flushes() {
        var name = "tone.mp3";
        try (var container = Mp3Container.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            try (var decoder = provider.open(container.request(0))) {
                var before = new ArrayList<Decoded>();
                drain(container, decoder, frame -> before.add(copy(frame)));
                container.seek(0);
                decoder.flush();
                var after = new ArrayList<Decoded>();
                drain(container, decoder, frame -> after.add(copy(frame)));
                assertEquals(before.size(), after.size());
                var first = before.stream()
                        .filter(f -> f.samples().length > 0)
                        .findFirst()
                        .orElseThrow();
                var again = after.stream()
                        .filter(f -> f.samples().length > 0)
                        .findFirst()
                        .orElseThrow();
                assertEquals(first.ptsNanos(), again.ptsNanos());
                assertArrayEquals(first.samples(), again.samples());
            }
        }
    }

    @Test
    @DisplayName("after a seek the first frame, whose reservoir reaches back, is passed over without an error")
    void seeksOn() {
        var name = "tone.mp3";
        try (var container = Mp3Container.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            try (var decoder = provider.open(container.request(0))) {
                container.seek(500_000_000L);
                var frames = new ArrayList<Decoded>();
                drain(container, decoder, frame -> frames.add(copy(frame)));
                assertFalse(frames.isEmpty());
                assertTrue(
                        frames.getFirst().ptsNanos() >= 400_000_000L,
                        "from the seek on: " + frames.getFirst().ptsNanos());
                assertTrue(frames.size() >= 20, frames.size() + " frames from the half second");
            }
        }
    }

    @Test
    @DisplayName("the provider is minimp3, for MP3 and the layers below, not for Opus")
    void provider() {
        assertEquals("minimp3", provider.name());
        assertEquals(Mp3Provider.NAME, provider.name());
        assertTrue(MediaNatives.isAvailable());
        var name = "tone-opus.webm";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            assertFalse(provider.supports(container.request(0)), "Opus is not minimp3's");
        }
        var mp3 = "tone.mp3";
        try (var container = Mp3Container.open(ClipFixtures.source(mp3), ClipFixtures.io(mp3))) {
            var request = container.request(0);
            assertTrue(provider.supports(request));
            assertTrue(provider.supports(new DecoderRequest(
                    CodecId.UNKNOWN, "mp2", request.params(), request.extradata(), request.timeBase())));
            assertFalse(provider.supports(new DecoderRequest(
                    CodecId.UNKNOWN, "aac", request.params(), request.extradata(), request.timeBase())));
        }
    }
}
