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
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
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
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;
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

    @Test
    @DisplayName("8-bit samples come out at full scale in 16 bits, not 256 times too quiet")
    void eightBitAtFullScale() {
        byte[] samples = {0, 1, -1, 127, -128, 64, -64, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        try (var arena = Arena.ofConfined()) {
            var request = new DecoderRequest(
                    CodecId.FLAC,
                    "flac",
                    new TrackParams.Audio(44_100, 1, Optional.of("s16"), OptionalInt.empty(), OptionalLong.empty()),
                    arena.allocateFrom(JAVA_BYTE, eightBitStreamInfo(samples.length)),
                    new Rational(1, 44_100));
            var frame = eightBitVerbatimFrame(samples);
            try (var decoder = provider.open(request);
                    var packet = Packet.of(
                            arena.allocateFrom(JAVA_BYTE, frame), 0, 0, 0, samples.length, true, request.timeBase())) {
                assertTrue(decoder.send(packet));
                var decoded = copy(assertInstanceOf(Received.Decoded.class, decoder.receive())
                        .frame());
                var expected = new short[samples.length];
                for (var i = 0; i < samples.length; i++) {
                    expected[i] = (short) (samples[i] << 8);
                }
                assertArrayEquals(expected, decoded.planes().getFirst());
            }
        }
    }

    /// The STREAMINFO body of a mono 8-bit stream at 44.1 kHz of one block of
    /// `blocksize` samples.
    private static byte[] eightBitStreamInfo(int blocksize) {
        var info = new byte[FlacDecoder.STREAMINFO_SIZE];
        info[1] = (byte) blocksize;
        info[3] = (byte) blocksize;
        // 20 bits of rate, 3 of channels - 1, 5 of bits - 1, 36 of total samples.
        var packed = (44_100L << 44) | (7L << 36) | blocksize;
        for (var i = 0; i < 8; i++) {
            info[10 + i] = (byte) (packed >>> (56 - 8 * i));
        }
        return info;
    }

    /// One FLAC frame of `samples`, mono, 8 bits, in a VERBATIM subframe: the
    /// frame header with its CRC-8, the subframe, and the frame's CRC-16.
    private static byte[] eightBitVerbatimFrame(byte[] samples) {
        var frame = new byte[6 + 1 + 1 + samples.length + 2];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xF8; // sync, fixed blocking
        frame[2] = 0x69; // block size from the end of the header; 44.1 kHz
        frame[3] = 0x02; // mono; 8 bits a sample
        frame[4] = 0; // frame number 0
        frame[5] = (byte) (samples.length - 1);
        frame[6] = (byte) crc8(frame, 6);
        frame[7] = 0x02; // VERBATIM, no wasted bits
        System.arraycopy(samples, 0, frame, 8, samples.length);
        var crc = crc16(frame, frame.length - 2);
        frame[frame.length - 2] = (byte) (crc >>> 8);
        frame[frame.length - 1] = (byte) crc;
        return frame;
    }

    private static int crc8(byte[] bytes, int length) {
        var crc = 0;
        for (var i = 0; i < length; i++) {
            crc ^= bytes[i] & 0xFF;
            for (var bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }

    private static int crc16(byte[] bytes, int length) {
        var crc = 0;
        for (var i = 0; i < length; i++) {
            crc ^= (bytes[i] & 0xFF) << 8;
            for (var bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x8005) & 0xFFFF : (crc << 1) & 0xFFFF;
            }
        }
        return crc;
    }
}
