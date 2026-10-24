package dev.goldberry.media.container.mp3;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.io.MemoryIO;

/// The MP3 demuxer against what FFmpeg's read of the same files said:
/// `ffprobe`'s packet list is the fixture. FFmpeg times MP3 packets in
/// 1/14112000 s and this demuxer in samples, so the times are compared in
/// nanoseconds, to the millisecond, after moving this demuxer's forward by the
/// priming it times the packets back by.
@DisplayName("Mp3Container")
class Mp3ContainerTest {

    private static final int RATE = 48_000;
    private static final int FRAME = 1152;
    private static final int FRAME_BYTES = 384;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};

    private static Mp3Container open(String name) {
        return Mp3Container.open(ClipFixtures.source(name), ClipFixtures.io(name));
    }

    private static Mp3Container open(byte[] bytes) {
        return Mp3Container.open(ClipFixtures.source("made.mp3"), new MemoryIO(bytes));
    }

    /// Every packet of `container`, closed as it goes, as the expectation's shape.
    private static List<ClipFixtures.Expected> drain(Mp3Container container) {
        var packets = new ArrayList<ClipFixtures.Expected>();
        for (var next = container.read(); next != null; next = container.read()) {
            try (var packet = next) {
                packets.add(new ClipFixtures.Expected(
                        packet.streamIndex(),
                        packet.pts(),
                        packet.duration(),
                        packet.data().byteSize(),
                        packet.keyframe()));
            }
        }
        return packets;
    }

    /// `tone.mp3` without its ID3v2 tag: the Info frame and 43 audio frames.
    private static byte[] frames() {
        var bytes = ClipFixtures.bytes("tone.mp3");
        var tagEnd = 10 + Id3v2.syncsafe(bytes, 6);
        return Arrays.copyOfRange(bytes, tagEnd, bytes.length);
    }

    private static byte[] concat(byte[]... parts) {
        var out = new ByteArrayOutputStream();
        for (var part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tone.mp3", "tone-cover.mp3"})
    @DisplayName(
            "hands over the packets ffprobe listed: stream, size and keyframe exactly, the times to the millisecond")
    void packetsAsFfprobe(String name) {
        List<ClipFixtures.Expected> actual;
        long priming;
        try (var container = open(name)) {
            assertEquals(new Rational(1, RATE), container.timeBase(0), "a sample time base");
            priming = container.priming();
            actual = drain(container);
        }
        var expected = ClipFixtures.packets(name);
        var ffmpegBase = new Rational(1, 14_112_000);
        var ours = new Rational(1, RATE);
        assertEquals(expected.size(), actual.size(), "packet count");
        for (var i = 0; i < expected.size(); i++) {
            var want = expected.get(i);
            var got = actual.get(i);
            var at = "packet " + i + " of " + name;
            assertEquals(want.stream(), got.stream(), at + ": stream");
            assertEquals(want.size(), got.size(), at + ": size");
            assertEquals(want.keyframe(), got.keyframe(), at + ": keyframe");
            if (want.pts() == Packet.NO_TIMESTAMP) {
                // The attached picture, which ffprobe gives no time.
                continue;
            }
            var wantPts = ffmpegBase.toNanos(want.pts());
            var gotPts = ours.toNanos(got.pts() + (got.stream() == 0 ? priming : 0));
            assertTrue(Math.abs(wantPts - gotPts) < 1_000_000, at + ": pts " + gotPts + " ns, ffprobe " + wantPts);
            // The last packet is the one place the two disagree on purpose: its
            // duration here is what plays after the encoder's padding, where
            // FFmpeg reports the whole frame and trims at decode time.
            var lastOfStream = expected.stream().skip(i + 1).noneMatch(later -> later.stream() == want.stream());
            var wantDuration = ffmpegBase.toNanos(want.duration());
            var gotDuration = ours.toNanos(got.duration());
            if (!(lastOfStream && gotDuration < wantDuration)) {
                assertTrue(
                        Math.abs(wantDuration - gotDuration) < 1_000_000,
                        at + ": duration " + gotDuration + " ns, ffprobe " + wantDuration);
            }
        }
    }

    @Test
    @DisplayName("isMagic: the fixtures' first bytes and a bare frame sync, not a Matroska file's")
    void isMagic() {
        assertTrue(Mp3Container.isMagic(Arrays.copyOf(ClipFixtures.bytes("tone.mp3"), 16)));
        assertTrue(Mp3Container.isMagic(Arrays.copyOf(ClipFixtures.bytes("tone-cover.mp3"), 16)));
        assertTrue(Mp3Container.isMagic(Arrays.copyOf(frames(), 16)), "a stream without a tag");
        assertFalse(Mp3Container.isMagic(Arrays.copyOf(ClipFixtures.bytes("clip-av1.mkv"), 16)));
        assertFalse(Mp3Container.isMagic(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0xF4, 0}), "a bad bit rate");
        assertFalse(Mp3Container.isMagic(new byte[] {(byte) 0xFF, (byte) 0xF1, 0x50, 0}), "ADTS AAC");
        assertFalse(Mp3Container.isMagic(new byte[] {'I', 'D'}));
    }

    @Test
    @DisplayName(
            "describes the tone: mp3, 48 kHz stereo at 128 kbit/s, one second by the Info frame's count less the LAME delay and padding")
    void describes() {
        try (var container = open("tone.mp3")) {
            var info = container.info();
            assertEquals(Optional.of(Duration.ofSeconds(1)), info.duration());
            assertTrue(info.seekable());
            assertFalse(info.live());
            assertEquals(1, info.tracks().size());
            var track = info.tracks().getFirst();
            assertAll(
                    () -> assertEquals(CodecId.MP3, track.codec()),
                    () -> assertEquals("mp3", track.codecName()),
                    () -> assertEquals(MediaType.AUDIO, track.type()),
                    () -> assertTrue(track.isDefault()),
                    () -> assertFalse(track.attachedPicture()),
                    () -> assertEquals(Optional.empty(), track.language()),
                    () -> assertEquals(Optional.of(Duration.ofSeconds(1)), track.duration()));
            assertEquals(
                    new TrackParams.Audio(RATE, 2, Optional.of("fltp"), OptionalInt.empty(), OptionalLong.of(128_000)),
                    track.params());

            var request = container.request(0);
            assertEquals(CodecId.MP3, request.codec());
            assertEquals(0, request.extradata().byteSize());
            assertEquals(new Rational(1, RATE), request.timeBase());
            assertThrows(IllegalArgumentException.class, () -> container.request(1));

            var vbr = container.vbrHeader().orElseThrow();
            assertAll(
                    () -> assertFalse(vbr.variable(), "an Info frame: constant bit rate"),
                    () -> assertEquals(43, vbr.frames()),
                    () -> assertEquals(16_896, vbr.bytes()),
                    () -> assertTrue(vbr.hasToc()),
                    () -> assertEquals(576, vbr.delay()),
                    () -> assertEquals(960, vbr.padding()));
            assertEquals(10 + 34 + FRAME_BYTES, container.audioStart(), "after the tag and the Info frame");
        }
    }

    @Test
    @DisplayName("the cover art is an attached-picture track, handed over as one PNG packet when selected")
    void coverArt() {
        try (var container = open("tone-cover.mp3")) {
            var tracks = container.info().tracks();
            assertEquals(2, tracks.size());
            var cover = tracks.get(1);
            assertAll(
                    () -> assertEquals(1, cover.index()),
                    () -> assertEquals(CodecId.PNG, cover.codec()),
                    () -> assertEquals("png", cover.codecName()),
                    () -> assertTrue(cover.attachedPicture()),
                    () -> assertFalse(cover.isDefault()),
                    () -> assertEquals(MediaType.VIDEO, cover.type()));
            assertInstanceOf(TrackParams.Video.class, cover.params());
            assertEquals(
                    tracks.getFirst(),
                    container.info().defaultTrack(MediaType.AUDIO).orElseThrow());

            container.select(Set.of(1));
            var packet = container.read();
            assertNotNull(packet);
            try (packet) {
                assertEquals(1, packet.streamIndex());
                assertEquals(178, packet.data().byteSize());
                assertTrue(packet.keyframe());
                assertArrayEquals(PNG_SIGNATURE, packet.data().asSlice(0, 4).toArray(JAVA_BYTE));
            }
            assertNull(container.read(), "only the picture was selected");

            container.seek(0);
            container.select(Set.of(0));
            var audio = drain(container);
            assertEquals(43, audio.size());
            assertTrue(audio.stream().allMatch(p -> p.stream() == 0), "the picture stays out");
        }
    }

    @Test
    @DisplayName("seeks to the frame nearest the target, by the constant bit rate, and back to the start")
    void seeks() {
        try (var container = open("tone.mp3")) {
            container.select(Set.of(0));
            var all = drain(container);
            assertNull(container.read(), "the end stays the end");
            container.seek(Duration.ofMillis(500).toNanos());
            var fromSeek = drain(container);
            assertFalse(fromSeek.isEmpty());
            var first = fromSeek.getFirst();
            assertTrue(first.keyframe());
            assertTrue(Math.abs(first.pts() - RATE / 2) <= 2 * FRAME, "near the target: " + first.pts());
            assertEquals(all.subList(all.size() - fromSeek.size(), all.size()), fromSeek, "the tail from there on");
            container.seek(Duration.ofSeconds(5).toNanos());
            assertNull(container.read(), "past the end is the end");
            container.seek(0);
            assertEquals(all, drain(container), "back to the start");
        }
    }

    @Test
    @DisplayName("a Xing frame makes the stream variable: the bit rate is the average, a seek goes by the table")
    void variableBitRate() {
        var bytes = ClipFixtures.bytes("tone.mp3");
        var tagEnd = 10 + Id3v2.syncsafe(bytes, 6);
        var info = tagEnd + 4 + 32;
        assertEquals("Info", new String(bytes, info, 4, StandardCharsets.ISO_8859_1));
        System.arraycopy("Xing".getBytes(StandardCharsets.ISO_8859_1), 0, bytes, info, 4);
        try (var container = open(bytes)) {
            var vbr = container.vbrHeader().orElseThrow();
            assertTrue(vbr.variable());
            var sound = assertInstanceOf(
                    TrackParams.Audio.class,
                    container.info().tracks().getFirst().params());
            assertEquals(
                    OptionalLong.of(16_896L * 8 * RATE / (43 * FRAME)), sound.bitRate(), "bytes over the frames' time");
            assertEquals(Optional.of(Duration.ofSeconds(1)), container.info().duration());
            var all = drain(container);
            assertEquals(43, all.size());
            container.seek(Duration.ofMillis(500).toNanos());
            var fromSeek = drain(container);
            var first = fromSeek.getFirst();
            assertTrue(Math.abs(first.pts() - RATE / 2) <= 2 * FRAME, "near the target: " + first.pts());
            // The table is approximate: the frame found and the frame the time
            // was taken from may differ by a frame or two.
            var found = all.size() - fromSeek.size();
            assertTrue(
                    Math.abs(found - first.pts() / FRAME) <= 2, "frame " + found + " timed as " + first.pts() / FRAME);
        }
    }

    @Test
    @DisplayName("garbage before, between and after the frames is passed over; an ID3v1 tag is not audio")
    void resyncs() {
        var frames = frames();
        var junk = new byte[37];
        Arrays.fill(junk, (byte) 0x55);
        // A false sync: a header whose frame is not followed by another.
        var falseSync = new byte[100];
        Arrays.fill(falseSync, (byte) 0x11);
        falseSync[20] = (byte) 0xFF;
        falseSync[21] = (byte) 0xFB;
        falseSync[22] = (byte) 0x94;
        var id3v1 = new byte[128];
        System.arraycopy("TAG".getBytes(StandardCharsets.ISO_8859_1), 0, id3v1, 0, 3);
        var split = 11 * FRAME_BYTES;
        var bytes = concat(
                junk,
                Arrays.copyOfRange(frames, 0, split),
                falseSync,
                Arrays.copyOfRange(frames, split, frames.length),
                id3v1);
        try (var container = open(bytes)) {
            assertEquals(Optional.of(Duration.ofSeconds(1)), container.info().duration());
            var priming = 576 + Mp3Container.DECODER_DELAY;
            assertEquals(priming, container.priming(), "the LAME tag's delay and the decoder's");
            var packets = drain(container);
            assertEquals(43, packets.size());
            for (var i = 0; i < packets.size(); i++) {
                assertEquals((long) i * FRAME - priming, packets.get(i).pts(), "packet " + i);
                assertEquals(FRAME_BYTES, packets.get(i).size());
            }
            assertEquals(FRAME, packets.get(41).duration());
            assertEquals(
                    FRAME - (960 - Mp3Container.DECODER_DELAY),
                    packets.getLast().duration(),
                    "the last frame plays up to the encoder's padding");
            container.seek(0);
            assertEquals(-priming, drain(container).getFirst().pts(), "back to the start, the priming again");
        }
    }

    @Test
    @DisplayName("without an Info frame the duration is the bytes over the bit rate, and nothing is trimmed")
    void constantBitRate() {
        var frames = frames();
        var audio = Arrays.copyOfRange(frames, FRAME_BYTES, frames.length);
        try (var container = open(audio)) {
            assertTrue(container.vbrHeader().isEmpty());
            assertEquals(0, container.audioStart());
            assertEquals(0, container.priming(), "no tag, so nothing known to drop");
            assertEquals(
                    Optional.of(Duration.ofMillis(1032)), container.info().duration(), "43 × 384 bytes at 128 kbit/s");
            var packets = drain(container);
            assertEquals(43, packets.size());
            assertEquals(FRAME, packets.getLast().duration());
            container.seek(Duration.ofMillis(500).toNanos());
            var fromSeek = drain(container);
            // One frame before the one holding the half second: the bit reservoir.
            assertEquals(19L * FRAME, fromSeek.getFirst().pts(), "frame 19 primes the one holding the half second");
            assertEquals(24, fromSeek.size());
        }
    }

    @Test
    @DisplayName("a source that cannot seek is read through, and refuses a seek")
    void unseekable() {
        var io = new MemoryIO(ClipFixtures.bytes("tone-cover.mp3"));
        io.seekable = false;
        io.sizeKnown = false;
        try (var container = Mp3Container.open(ClipFixtures.source("tone-cover.mp3"), io)) {
            assertFalse(container.info().seekable());
            assertEquals(2, container.info().tracks().size());
            var packets = drain(container);
            assertEquals(44, packets.size(), "the picture and 43 frames");
            var e = assertThrows(MediaException.class, () -> container.seek(1));
            assertInstanceOf(MediaError.Io.class, e.error());
        }
    }

    @Test
    @DisplayName("bytes with no frame in them are not an MP3; a closed container refuses a read")
    void refuses() {
        var e = assertThrows(MediaException.class, () -> open(new byte[1000]));
        assertInstanceOf(MediaError.InvalidData.class, e.error());
        var container = open("tone.mp3");
        container.close();
        container.close();
        assertThrows(IllegalStateException.class, container::read);
    }
}
