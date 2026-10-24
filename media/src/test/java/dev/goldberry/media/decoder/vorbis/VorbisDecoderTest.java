package dev.goldberry.media.decoder.vorbis;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.container.ogg.OggContainer;
import dev.goldberry.media.ffm.MediaNatives;

/// libvorbis through the SPI. The clip is a 440 Hz sine at 12000/32768, so
/// what comes out is checked as a signal: its length, its loudness and its
/// pitch, the way the Opus decoder's test checks a lossy codec.
@DisplayName("VorbisDecoder")
class VorbisDecoderTest {

    private static final int RATE = 48_000;
    private static final double RMS = 12_000d / 32_768 / Math.sqrt(2);

    private final VorbisProvider provider = new VorbisProvider();

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    /// One frame of the first audio track, its left channel copied out.
    private record Decoded(long ptsNanos, int channels, float[] left) {}

    private static OggContainer open(String name) {
        return OggContainer.open(ClipFixtures.source(name), ClipFixtures.io(name));
    }

    private List<Decoded> decode(Container container, Decoder decoder) {
        var frames = new ArrayList<Decoded>();
        drain(container, decoder, frame -> {
            var audio = assertInstanceOf(AudioFrame.class, frame);
            assertEquals(SampleFormat.F32_PLANAR, audio.format());
            assertEquals(RATE, audio.sampleRate());
            assertEquals(audio.channels(), audio.planes().size());
            var left = audio.planes()
                    .getFirst()
                    .asSlice(0, (long) audio.samples() * Float.BYTES)
                    .toArray(JAVA_FLOAT);
            frames.add(new Decoded(audio.ptsNanos(), audio.channels(), left));
        });
        return frames;
    }

    @Test
    @DisplayName("decodes a second of the tone: 48 000 samples within a block, the right loudness and pitch")
    void decodesTheTone() {
        List<Decoded> frames;
        try (var container = open("tone.ogg")) {
            var track = container.info().defaultTrack(MediaType.AUDIO).orElseThrow();
            container.select(Set.of(track.index()));
            var request = container.request(track.index());
            assertTrue(provider.supports(request));
            try (var decoder = provider.open(request)) {
                assertEquals(2, ((VorbisDecoder) decoder).channels());
                assertEquals(RATE, ((VorbisDecoder) decoder).rate());
                frames = decode(container, decoder);
            }
        }
        assertFalse(frames.isEmpty());
        assertTrue(frames.stream().allMatch(f -> f.channels() == 2));
        var total = frames.stream().mapToInt(f -> f.left().length).sum();
        assertTrue(Math.abs(total - RATE) <= 2048, total + " samples");
        // The first packet yields nothing; the first frame is the second
        // packet's, at 0.
        assertEquals(0, frames.getFirst().ptsNanos(), "first pts");

        var left = new float[total];
        var at = 0;
        for (var frame : frames) {
            System.arraycopy(frame.left(), 0, left, at, frame.left().length);
            at += frame.left().length;
        }
        // Skip the first block, where the encoder is still converging.
        var from = 2048;
        var sum = 0d;
        var crossings = 0;
        for (var i = from; i < total; i++) {
            sum += left[i] * left[i];
            if (i > from && (left[i - 1] < 0) != (left[i] < 0)) {
                crossings++;
            }
        }
        var rms = Math.sqrt(sum / (total - from));
        assertTrue(Math.abs(rms - RMS) / RMS < 0.1, "RMS " + rms + ", expected about " + RMS);
        var expected = 2 * 440 * (total - from) / RATE;
        assertTrue(Math.abs(crossings - expected) <= 6, crossings + " crossings, expected " + expected);
    }

    @Test
    @DisplayName("a flush and a seek to the start decode the same samples again")
    void flushes() {
        try (var container = open("tone.ogg")) {
            try (var decoder = provider.open(container.request(0))) {
                var before = decode(container, decoder);
                container.seek(0);
                decoder.flush();
                var after = decode(container, decoder);
                assertEquals(before.size(), after.size(), "frames");
                assertEquals(
                        before.stream().mapToInt(f -> f.left().length).sum(),
                        after.stream().mapToInt(f -> f.left().length).sum(),
                        "samples");
                assertEquals(before.getFirst().ptsNanos(), after.getFirst().ptsNanos());
            }
        }
    }

    @Test
    @DisplayName("the provider is vorbis, for Vorbis alone, and refuses a record that is not three headers")
    void provider() {
        assertEquals("vorbis", provider.name());
        assertEquals(VorbisProvider.NAME, provider.name());
        assertTrue(MediaNatives.isAvailable());
        try (var container = open("tone.opus")) {
            assertFalse(provider.supports(container.request(0)), "Opus is not Vorbis");
        }
        try (var arena = Arena.ofConfined()) {
            var params = new TrackParams.Audio(RATE, 2, Optional.of("fltp"), OptionalInt.empty(), OptionalLong.empty());
            var junk = arena.allocateFrom(ValueLayout.JAVA_BYTE, new byte[] {2, 1, 1, 9, 9, 9});
            var request = new DecoderRequest(CodecId.VORBIS, "vorbis", params, junk, new Rational(1, RATE));
            var e = assertThrows(MediaException.class, () -> provider.open(request));
            assertInstanceOf(MediaError.InvalidData.class, e.error());
            var none = new DecoderRequest(CodecId.VORBIS, "vorbis", params, MemorySegment.NULL, new Rational(1, RATE));
            assertThrows(MediaException.class, () -> provider.open(none));
        }
    }

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
}
