package dev.goldberry.media.container.matroska;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.bitstream.TheoraHeaders;
import dev.goldberry.media.bitstream.XiphLacing;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.FrameRate;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.io.MemoryIO;

/// The Matroska demuxer against what FFmpeg's read of the same files said:
/// `ffprobe`'s packet list is the fixture, so the two demuxers are held to the
/// same packets, byte counts, timestamps and keyframe flags.
@DisplayName("MatroskaContainer")
class MatroskaContainerTest {

    private static MatroskaContainer open(String name) {
        return MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name));
    }

    /// Every packet of `container`, closed as it goes, as the expectation's shape.
    private static List<ClipFixtures.Expected> drain(MatroskaContainer container) {
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

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "clip-av1.mkv",
                "clip-theora.mkv",
                "clip-vp8.webm",
                "clip-two-angles.mkv",
                "clip-vp9-subs.mkv",
                "sticker-vp9-alpha.webm",
                "tone-flac.mkv",
                "tone-opus.webm",
                "tones-two-tracks.mkv",
            })
    @DisplayName("hands over the packets ffprobe listed: stream, time, size and keyframe, in file order")
    void packetsAsFfprobe(String name) {
        List<ClipFixtures.Expected> actual;
        try (var container = open(name)) {
            assertEquals(new Rational(1, 1000), container.timeBase(0), "Matroska's millisecond time base");
            actual = drain(container);
        }
        var expected = ClipFixtures.packets(name);
        assertEquals(expected.size(), actual.size(), "packet count");
        for (var i = 0; i < expected.size(); i++) {
            var want = expected.get(i);
            var got = actual.get(i);
            var at = "packet " + i + " of " + name;
            assertEquals(want.stream(), got.stream(), at + ": stream");
            assertEquals(want.pts(), got.pts(), at + ": pts");
            assertEquals(want.size(), got.size(), at + ": size");
            assertEquals(want.keyframe(), got.keyframe(), at + ": keyframe");
            // FFmpeg's parsers measure FLAC frames; this demuxer states a duration
            // only where the container or an Opus TOC does. A stream's last Opus
            // packet is the one place the two disagree on purpose: its BlockGroup
            // states the 7 ms that play after the DiscardPadding, where FFmpeg
            // reports the TOC's 20 ms and trims at decode time.
            var lastOfStream = expected.stream().skip(i + 1).noneMatch(later -> later.stream() == want.stream());
            if (got.duration() != 0 && !(lastOfStream && got.duration() < want.duration())) {
                assertEquals(want.duration(), got.duration(), at + ": duration");
            }
        }
    }

    @Test
    @DisplayName("describes a Theora track: V_THEORA, its pixel format from the laced headers in CodecPrivate")
    void describesTheora() {
        try (var container = open("clip-theora.mkv")) {
            var video = container.info().tracks().get(0);
            assertEquals(CodecId.THEORA, video.codec());
            assertEquals("theora", video.codecName());
            var picture = assertInstanceOf(TrackParams.Video.class, video.params());
            assertAll(
                    () -> assertEquals(160, picture.width()),
                    () -> assertEquals(90, picture.height()),
                    () -> assertEquals(Optional.of("yuv420p"), picture.pixelFormat()),
                    () -> assertEquals(Optional.of(new FrameRate(25, 1)), picture.frameRate()));
            var headers = XiphLacing.split(container.request(0).extradata().toArray(JAVA_BYTE));
            assertEquals(3, headers.size());
            assertTrue(TheoraHeaders.isHeader(headers.getFirst(), TheoraHeaders.IDENTIFICATION));
        }
    }

    @Test
    @DisplayName("describes an AV1 and Opus clip: codecs, size, frame rate, rate and channels, duration")
    void describesAv1() {
        try (var container = open("clip-av1.mkv")) {
            var info = container.info();
            assertEquals(Optional.of(Duration.ofMillis(1008)), info.duration());
            assertTrue(info.seekable());
            assertFalse(info.live());
            assertEquals(2, info.tracks().size());

            var video = info.tracks().get(0);
            assertEquals(CodecId.AV1, video.codec());
            assertEquals("av1", video.codecName());
            var picture = assertInstanceOf(TrackParams.Video.class, video.params());
            assertAll(
                    () -> assertEquals(160, picture.width()),
                    () -> assertEquals(90, picture.height()),
                    () -> assertEquals(Optional.of("yuv420p"), picture.pixelFormat()),
                    () -> assertEquals(OptionalInt.of(0), picture.profile()),
                    () -> assertEquals(Optional.of(new FrameRate(25, 1)), picture.frameRate()),
                    () -> assertFalse(picture.alpha()));
            assertEquals(Optional.of(Duration.ofMillis(1008)), video.duration());

            var audio = info.tracks().get(1);
            assertEquals(CodecId.OPUS, audio.codec());
            var sound = assertInstanceOf(TrackParams.Audio.class, audio.params());
            assertEquals(48_000, sound.sampleRate());
            assertEquals(2, sound.channels());
            assertEquals(Optional.of("fltp"), sound.sampleFormat());

            var request = container.request(1);
            assertEquals(CodecId.OPUS, request.codec());
            assertEquals(
                    "OpusHead",
                    new String(
                            request.extradata().asSlice(0, 8).toArray(ValueLayout.JAVA_BYTE),
                            StandardCharsets.US_ASCII));
            assertEquals(new Rational(1, 1000), request.timeBase());
        }
    }

    @Test
    @DisplayName("reads languages, titles and the default flag: two FLAC tracks, one Concert pitch")
    void describesTwoTracks() {
        try (var container = open("tones-two-tracks.mkv")) {
            var tracks = container.info().tracks();
            assertEquals(2, tracks.size());
            assertEquals(Optional.of("eng"), tracks.get(0).language());
            assertEquals(Optional.of("Concert pitch"), tracks.get(0).title());
            assertTrue(tracks.get(0).isDefault());
            assertEquals(Optional.of("fra"), tracks.get(1).language());
            assertEquals(Optional.empty(), tracks.get(1).title());
            assertFalse(tracks.get(1).isDefault());
            assertEquals(
                    Optional.of("s16"),
                    assertInstanceOf(TrackParams.Audio.class, tracks.get(0).params())
                            .sampleFormat());
            assertEquals(Optional.of(Duration.ofSeconds(2)), container.info().duration());
        }
    }

    @Test
    @DisplayName("names the subtitle tracks, and hands their text over with the block's duration")
    void subtitles() {
        try (var container = open("clip-vp9-subs.mkv")) {
            var tracks = container.info().tracks();
            assertEquals(CodecId.SUBRIP, tracks.get(1).codec());
            assertEquals(Optional.of("eng"), tracks.get(1).language());
            assertEquals(CodecId.ASS, tracks.get(2).codec());
            assertEquals(Optional.of("fra"), tracks.get(2).language());
            assertEquals(MediaType.SUBTITLE, tracks.get(1).type());

            container.select(Set.of(1));
            var cue = container.read();
            assertNotNull(cue);
            try (cue) {
                assertEquals(1, cue.streamIndex());
                assertTrue(cue.duration() > 0, "a subtitle block carries its duration");
                var text = new String(cue.data().toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
                assertFalse(text.isBlank());
            }
        }
    }

    @Test
    @DisplayName("a WebM sticker: the alpha plane rides the packet as a block addition")
    void alpha() {
        try (var container = open("sticker-vp9-alpha.webm")) {
            var picture = assertInstanceOf(
                    TrackParams.Video.class,
                    container.info().tracks().getFirst().params());
            assertTrue(picture.alpha());
            var count = 0;
            for (var next = container.read(); next != null; next = container.read()) {
                try (var packet = next) {
                    assertTrue(packet.alpha().byteSize() > 0, "packet " + count + " has an alpha plane");
                    count++;
                }
            }
            assertEquals(10, count);
        }
    }

    @Test
    @DisplayName("select() keeps the other streams' packets out")
    void selects() {
        try (var container = open("clip-av1.mkv")) {
            container.select(Set.of(0));
            var packets = drain(container);
            assertEquals(25, packets.size());
            assertTrue(packets.stream().allMatch(p -> p.stream() == 0));
        }
    }

    @Test
    @DisplayName("seeks by the Cues to the cluster holding the keyframe at or before the target")
    void seeks() {
        try (var container = open("clip-vp9-subs.mkv")) {
            container.select(Set.of(0));
            var all = drain(container);
            assertNull(container.read(), "the end stays the end");
            container.seek(Duration.ofMillis(500).toNanos());
            var fromSeek = drain(container);
            assertFalse(fromSeek.isEmpty());
            assertTrue(fromSeek.getFirst().keyframe(), "a seek lands on a keyframe");
            assertTrue(
                    fromSeek.getFirst().pts() <= 500,
                    "at or before the target: " + fromSeek.getFirst().pts());
            assertEquals(all.subList(all.size() - fromSeek.size(), all.size()), fromSeek, "the tail from there on");
            container.seek(0);
            assertEquals(all, drain(container), "back to the start");
        }
    }

    @Test
    @DisplayName("a laced block's packets share one allocation, freed when the last closes")
    void lacedPackets() {
        try (var container = open("tone-flac.mkv")) {
            var packets = new ArrayList<Packet>();
            for (var packet = container.read(); packet != null; packet = container.read()) {
                packets.add(packet);
            }
            assertEquals(12, packets.size());
            // Closing in any order, twice, is harmless.
            packets.reversed().forEach(Packet::close);
            packets.forEach(Packet::close);
        }
    }

    @Test
    @DisplayName("a PixelHeight wider than an int is a height not stated, not a track that cannot be described")
    void heightBeyondAnInt() {
        // The sticker's PixelHeight (BA 81 40) and FlagInterlaced (9A 81 02),
        // six bytes, rewritten as one four-byte PixelHeight of 2³¹ + 64, the
        // robustness tests' find: cast to an int it was negative, and the track's
        // parameters refused it with an IllegalArgumentException.
        var bytes = ClipFixtures.bytes("sticker-vp9-alpha.webm");
        var at = indexOf(bytes, new byte[] {(byte) 0xBA, (byte) 0x81, 0x40, (byte) 0x9A, (byte) 0x81, 0x02});
        System.arraycopy(new byte[] {(byte) 0xBA, (byte) 0x84, (byte) 0x80, 0, 0, 0x40}, 0, bytes, at, 6);
        try (var container = MatroskaContainer.open(ClipFixtures.source("sticker"), new MemoryIO(bytes))) {
            var picture = assertInstanceOf(
                    TrackParams.Video.class,
                    container.info().tracks().getFirst().params());
            assertEquals(64, picture.width());
            assertEquals(0, picture.height());
        }
        assertEquals(0, MatroskaContainer.intOrUnknown(1L << 32));
        assertEquals(0, MatroskaContainer.intOrUnknown(-1));
        assertEquals(1080, MatroskaContainer.intOrUnknown(1080));
    }

    private static int indexOf(byte[] bytes, byte[] pattern) {
        outer:
        for (var i = 0; i + pattern.length <= bytes.length; i++) {
            for (var j = 0; j < pattern.length; j++) {
                if (bytes[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError("pattern not in the fixture");
    }

    @Test
    @DisplayName("the time base is the TimestampScale over a second, reduced")
    void timeBase() {
        assertEquals(new Rational(1, 1000), MatroskaContainer.timeBaseOf(1_000_000));
        assertEquals(new Rational(1, 1_000_000_000), MatroskaContainer.timeBaseOf(1));
        assertEquals(new Rational(11_111, 1_000_000_000), MatroskaContainer.timeBaseOf(11_111));
    }

    @Test
    @DisplayName("a WebM track's Colour: HLG in BT.2020, which VP9's own bitstream cannot say")
    void colour() {
        try (var container = open("clip-vp9-hlg.webm")) {
            var picture = assertInstanceOf(
                    TrackParams.Video.class,
                    container.info().tracks().getFirst().params());
            assertEquals(ColorDescription.Primaries.BT2020, picture.colour().primaries());
            assertEquals(ColorDescription.Transfer.HLG, picture.colour().transfer());
        }
        try (var container = open("clip-vp9.webm")) {
            var picture = assertInstanceOf(
                    TrackParams.Video.class,
                    container.info().tracks().getFirst().params());
            assertEquals(ColorDescription.UNSPECIFIED, picture.colour(), "a clip that states none");
        }
    }
}
