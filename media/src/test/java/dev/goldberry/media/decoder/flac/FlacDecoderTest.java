package dev.goldberry.media.decoder.flac;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.Wav;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.decoder.FrameHashes;

/// libFLAC through the SPI. FLAC is lossless and the clips are a sine of
/// known amplitude, so every sample that comes out is held to the formula
/// that made it.
@DisplayName("FlacDecoder")
class FlacDecoderTest {

    private static final int RATE = 48_000;
    private static final int AMPLITUDE = 12_000;

    private final FlacProvider provider = new FlacProvider();

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    /// One frame of a track, its planes copied out.
    private record Decoded(long ptsNanos, int sampleRate, int channels, List<short[]> planes) {

        int samples() {
            return planes.getFirst().length;
        }
    }

    private static Decoded copy(Frame frame) {
        var audio = assertInstanceOf(AudioFrame.class, frame);
        assertEquals(SampleFormat.S16_PLANAR, audio.format());
        assertEquals(audio.channels(), audio.planes().size(), "a plane per channel");
        var planes = new ArrayList<short[]>();
        for (var plane : audio.planes()) {
            planes.add(plane.asSlice(0, (long) audio.samples() * Short.BYTES).toArray(JAVA_SHORT));
        }
        return new Decoded(audio.ptsNanos(), audio.sampleRate(), audio.channels(), planes);
    }

    /// Every frame of track `index` of `name`, through the provider.
    private List<Decoded> decode(String name, int index) {
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            container.select(Set.of(index));
            var request = container.request(index);
            assertTrue(provider.supports(request));
            try (var decoder = provider.open(request)) {
                return drain(container, decoder);
            }
        }
    }

    private static List<Decoded> drain(MatroskaContainer container, Decoder decoder) {
        var frames = new ArrayList<Decoded>();
        FrameHashes.drain(container, decoder, frame -> frames.add(copy(frame)));
        return frames;
    }

    /// Asserts that `frames` are `seconds` of the `frequency` Hz tone, sample
    /// for sample on both channels, timed from zero a block apart.
    private static void assertTone(List<Decoded> frames, double frequency, int seconds) {
        assertFalse(frames.isEmpty());
        var total = 0;
        var expectedPts = 0L;
        for (var frame : frames) {
            assertEquals(RATE, frame.sampleRate());
            assertEquals(2, frame.channels());
            // Matroska timestamps are milliseconds, so a block of 4096
            // samples lands within a millisecond of where it belongs.
            assertTrue(
                    Math.abs(frame.ptsNanos() - expectedPts) <= 1_000_000,
                    "frame at " + frame.ptsNanos() + " ns, expected about " + expectedPts);
            var expected = new short[frame.samples()];
            for (var i = 0; i < expected.length; i++) {
                expected[i] = Wav.sineSample(RATE, total + i, frequency, AMPLITUDE);
            }
            assertArrayEquals(expected, frame.planes().get(0), "left channel at sample " + total);
            assertArrayEquals(expected, frame.planes().get(1), "right channel at sample " + total);
            total += frame.samples();
            expectedPts += frame.samples() * 1_000_000_000L / RATE;
        }
        assertEquals(0L, frames.getFirst().ptsNanos());
        assertEquals(seconds * RATE, total);
    }

    /// Asserts that `after` is `before` again, frame for frame and sample for
    /// sample. (A record of arrays compares its arrays by identity.)
    private static void assertSameFrames(List<Decoded> before, List<Decoded> after) {
        assertEquals(before.size(), after.size(), "frame count");
        for (var i = 0; i < before.size(); i++) {
            var expected = before.get(i);
            var actual = after.get(i);
            assertEquals(expected.ptsNanos(), actual.ptsNanos(), "pts of frame " + i);
            assertEquals(expected.channels(), actual.channels(), "channels of frame " + i);
            for (var channel = 0; channel < expected.channels(); channel++) {
                assertArrayEquals(
                        expected.planes().get(channel), actual.planes().get(channel), "frame " + i);
            }
        }
    }

    @Test
    @DisplayName("decodes the second of 440 Hz in tone-flac.mkv, every sample exact")
    void decodesTheTone() {
        assertTone(decode("tone-flac.mkv", 0), 440, 1);
    }

    @Test
    @DisplayName("decodes both tracks of tones-two-tracks.mkv: 440 Hz, then 880 Hz, two seconds each")
    void decodesBothTracks() {
        assertTone(decode("tones-two-tracks.mkv", 0), 440, 2);
        assertTone(decode("tones-two-tracks.mkv", 1), 880, 2);
    }

    @Test
    @DisplayName("a flush and a seek to the start decode the first frame again")
    void flushes() {
        var name = "tone-flac.mkv";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            try (var decoder = provider.open(container.request(0))) {
                var before = drain(container, decoder);
                container.seek(0);
                decoder.flush();
                var after = drain(container, decoder);
                assertSameFrames(before, after);
            }
        }
    }

    @Test
    @DisplayName("a bare STREAMINFO, as MP4 and Ogg hand it over, opens and decodes the same")
    void bareStreamInfo() {
        var name = "tone-flac.mkv";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name));
                var arena = Arena.ofConfined()) {
            var whole = container.request(0);
            var record = whole.extradata().toArray(JAVA_BYTE);
            assertEquals(42, record.length, "fLaC, a block header and STREAMINFO");
            var streamInfo = arena.allocateFrom(JAVA_BYTE, Arrays.copyOfRange(record, 8, 42));
            var request =
                    new DecoderRequest(whole.codec(), whole.codecName(), whole.params(), streamInfo, whole.timeBase());
            assertArrayEquals(record, FlacDecoder.header(request), "the wrapped record is the whole one again");
            try (var decoder = provider.open(request)) {
                assertTone(drain(container, decoder), 440, 1);
            }
        }
    }

    @Test
    @DisplayName("without a record, a header is made up from the track's parameters, and the frames still decode")
    void synthesisedHeader() {
        var name = "tone-flac.mkv";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var whole = container.request(0);
            var request = new DecoderRequest(
                    whole.codec(), whole.codecName(), whole.params(), MemorySegment.NULL, whole.timeBase());
            var header = FlacDecoder.header(request);
            assertEquals(42, header.length);
            assertEquals('f', header[0]);
            assertEquals((byte) 0x80, header[4], "the one block is the last");
            try (var decoder = provider.open(request)) {
                assertTone(drain(container, decoder), 440, 1);
            }
        }
    }

    @Test
    @DisplayName("a record that is neither a header nor a STREAMINFO is refused")
    void refusesAStrangeRecord() {
        var name = "tone-flac.mkv";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name));
                var arena = Arena.ofConfined()) {
            var whole = container.request(0);
            var request = new DecoderRequest(
                    whole.codec(),
                    whole.codecName(),
                    whole.params(),
                    arena.allocateFrom(JAVA_BYTE, new byte[] {1, 2, 3}),
                    whole.timeBase());
            assertThrows(MediaException.class, () -> provider.open(request));
        }
    }

    @Test
    @DisplayName("the provider is flac, for FLAC alone, and refuses Opus")
    void provider() {
        assertEquals("flac", provider.name());
        var name = "tone-opus.webm";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var request = container.request(0);
            assertEquals(CodecId.OPUS, request.codec());
            assertFalse(provider.supports(request), "Opus is not FLAC");
        }
    }

    @Test
    @DisplayName("a packet that is not a whole frame is an error, and the decoder goes on from a seek")
    void refusesATruncatedPacket() {
        var name = "tone-flac.mkv";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            try (var decoder = provider.open(container.request(0))) {
                try (var whole = container.read()) {
                    assertTrue(whole != null);
                    var half = Packet.of(
                            whole.data().asSlice(0, whole.data().byteSize() / 2),
                            whole.streamIndex(),
                            whole.pts(),
                            whole.dts(),
                            whole.duration(),
                            whole.keyframe(),
                            whole.timeBase());
                    assertThrows(MediaException.class, () -> decoder.send(half));
                }
                decoder.flush();
                container.seek(0);
                assertTone(drain(container, decoder), 440, 1);
            }
        }
    }
}
