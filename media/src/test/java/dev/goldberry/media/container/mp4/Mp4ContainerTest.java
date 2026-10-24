package dev.goldberry.media.container.mp4;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.FrameRate;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.decoder.FrameHashes;
import dev.goldberry.media.decoder.av1.Dav1dProvider;
import dev.goldberry.media.decoder.opus.OpusHead;
import dev.goldberry.media.decoder.opus.OpusProvider;
import dev.goldberry.media.io.MemoryIO;

/// The MP4 demuxer against what FFmpeg's read of the same files said:
/// `ffprobe`'s packet list is the fixture, so the two demuxers are held to the
/// same packets, byte counts, timestamps and keyframe flags, edit lists and
/// all.
@DisplayName("Mp4Container")
class Mp4ContainerTest {

    private static Mp4Container open(String name) {
        return Mp4Container.open(ClipFixtures.source(name), ClipFixtures.io(name));
    }

    /// Every packet of `container`, closed as it goes, as the expectation's shape.
    private static List<ClipFixtures.Expected> drain(Mp4Container container) {
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

    private static void assertPackets(String name, List<ClipFixtures.Expected> actual) {
        var expected = ClipFixtures.packets(name);
        assertEquals(expected.size(), actual.size(), "packet count of " + name);
        for (var i = 0; i < expected.size(); i++) {
            var want = expected.get(i);
            var got = actual.get(i);
            var at = "packet " + i + " of " + name;
            assertEquals(want.stream(), got.stream(), at + ": stream");
            assertEquals(want.pts(), got.pts(), at + ": pts");
            assertEquals(want.duration(), got.duration(), at + ": duration");
            assertEquals(want.size(), got.size(), at + ": size");
            assertEquals(want.keyframe(), got.keyframe(), at + ": keyframe");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "clip-av1.mp4",
                "tone-flac.mp4",
                "tone-opus.mp4",
                "clip-h264-aac.mp4",
                "tone-flac-faststart.mp4",
                "clip-av1-fragmented.mp4",
            })
    @DisplayName("hands over the packets ffprobe listed: stream, time, duration, size and keyframe, in order")
    void packetsAsFfprobe(String name) {
        List<ClipFixtures.Expected> actual;
        try (var container = open(name)) {
            actual = drain(container);
            assertNull(container.read(), "the end stays the end");
        }
        assertPackets(name, actual);
    }

    @Test
    @DisplayName("describes an AV1 and FLAC clip: codecs, size, pixel format, frame rate, rate and channels, duration")
    void describesAv1() {
        try (var container = open("clip-av1.mp4")) {
            var info = container.info();
            assertEquals(Optional.of(Duration.ofSeconds(1)), info.duration());
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
                    () -> assertEquals(OptionalInt.of(0), picture.level()),
                    () -> assertEquals(Optional.of(new FrameRate(25, 1)), picture.frameRate()),
                    () -> assertFalse(picture.alpha()));
            assertEquals(Optional.of(Duration.ofSeconds(1)), video.duration());
            assertEquals(OptionalLong.of(25), video.frameCount());
            assertTrue(video.isDefault());
            assertEquals(Optional.empty(), video.language(), "'und' is no language");
            assertEquals(new Rational(1, 12_800), container.timeBase(0), "the mdhd timescale");

            var audio = info.tracks().get(1);
            assertEquals(CodecId.FLAC, audio.codec());
            assertEquals("flac", audio.codecName());
            var sound = assertInstanceOf(TrackParams.Audio.class, audio.params());
            assertEquals(48_000, sound.sampleRate());
            assertEquals(2, sound.channels());
            assertEquals(Optional.of("s16"), sound.sampleFormat());
            assertEquals(new Rational(1, 48_000), container.timeBase(1));
            assertEquals(Optional.of(Duration.ofSeconds(1)), audio.duration());

            var request = container.request(0);
            assertEquals(CodecId.AV1, request.codec());
            assertTrue(request.extradata().byteSize() > 4, "the av1C record");
            assertEquals(0x81, request.extradata().get(JAVA_BYTE, 0) & 0xFF, "av1C's marker and version");
            assertEquals(new Rational(1, 12_800), request.timeBase());
        }
    }

    @Test
    @DisplayName("opens the H.264 and AAC clip and names both, with the avcC and the AudioSpecificConfig")
    void describesH264Aac() {
        try (var container = open("clip-h264-aac.mp4")) {
            var tracks = container.info().tracks();
            assertEquals(2, tracks.size());
            var video = tracks.get(0);
            assertEquals(CodecId.H264, video.codec());
            assertEquals("h264", video.codecName());
            var picture = assertInstanceOf(TrackParams.Video.class, video.params());
            assertEquals(160, picture.width());
            assertEquals(90, picture.height());
            assertEquals(Optional.of("yuv420p"), picture.pixelFormat(), "from the SPS in the avcC");
            assertEquals(OptionalInt.of(66), picture.profile(), "Constrained Baseline");
            assertEquals(OptionalInt.of(11), picture.level());
            assertEquals(Optional.of(new FrameRate(25, 1)), picture.frameRate());
            assertEquals(1, container.request(0).extradata().get(JAVA_BYTE, 0), "an avcC record, version 1");

            var audio = tracks.get(1);
            assertEquals(CodecId.AAC, audio.codec());
            assertEquals("aac", audio.codecName());
            var sound = assertInstanceOf(TrackParams.Audio.class, audio.params());
            assertEquals(48_000, sound.sampleRate());
            assertEquals(2, sound.channels());
            assertEquals(Optional.of("fltp"), sound.sampleFormat());
            assertEquals(OptionalInt.of(1), sound.profile(), "LC, as FFmpeg numbers it");
            assertEquals(OptionalLong.of(61_942), sound.bitRate(), "the btrt box");
            var extradata = container.request(1).extradata().toArray(JAVA_BYTE);
            assertArrayEquals(
                    new byte[] {0x11, (byte) 0x90, 0x56, (byte) 0xE5, 0x00},
                    extradata,
                    "the AudioSpecificConfig out of esds");
        }
    }

    @Test
    @DisplayName("turns dOps into an OpusHead the Opus decoder reads: stereo, a pre-skip of 312")
    void opusExtradata() {
        try (var container = open("tone-opus.mp4")) {
            var track = container.info().tracks().getFirst();
            assertEquals(CodecId.OPUS, track.codec());
            assertEquals(
                    Optional.of("fltp"),
                    assertInstanceOf(TrackParams.Audio.class, track.params()).sampleFormat());
            var request = container.request(0);
            var extradata = request.extradata().toArray(JAVA_BYTE);
            assertEquals("OpusHead", new String(extradata, 0, 8, StandardCharsets.US_ASCII));
            var head = OpusHead.parse(extradata);
            assertEquals(2, head.channels());
            assertEquals(312, head.preSkip(), "ffprobe's initial_padding");
            assertEquals(48_000, head.inputRate());
            assertEquals(0, head.gain());
            assertTrue(head.isSingleStream());
            assertEquals(Optional.of(Duration.ofSeconds(1)), track.duration(), "the edit's length, not the media's");
        }
    }

    @Test
    @DisplayName("prefixes dfLa's metadata blocks with fLaC, as the FLAC decoder reads a stream's header")
    void flacExtradata() {
        try (var container = open("tone-flac.mp4")) {
            var extradata = container.request(0).extradata().toArray(JAVA_BYTE);
            assertEquals("fLaC", new String(extradata, 0, 4, StandardCharsets.US_ASCII));
            assertEquals(0x80, extradata[4] & 0xFF, "the last block, a STREAMINFO");
            assertEquals(34, (extradata[5] & 0xFF) << 16 | (extradata[6] & 0xFF) << 8 | (extradata[7] & 0xFF));
            assertEquals(4 + 4 + 34, extradata.length);
        }
    }

    @Test
    @DisplayName("select() keeps the other streams' packets out")
    void selects() {
        try (var container = open("clip-av1.mp4")) {
            container.select(Set.of(1));
            var packets = drain(container);
            assertEquals(12, packets.size());
            assertTrue(packets.stream().allMatch(p -> p.stream() == 1));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-av1.mp4", "clip-av1-fragmented.mp4"})
    @DisplayName("seeks to the sync sample at or before the target in the selected video track")
    void seeks(String name) {
        try (var container = open(name)) {
            container.select(Set.of(0));
            var all = drain(container);
            assertEquals(25, all.size());
            container.seek(Duration.ofMillis(500).toNanos());
            var fromSeek = drain(container);
            assertFalse(fromSeek.isEmpty());
            assertTrue(fromSeek.getFirst().keyframe(), "a seek lands on a keyframe");
            assertTrue(
                    fromSeek.getFirst().pts() <= 6_400,
                    "at or before 0.5 s: " + fromSeek.getFirst().pts());
            assertEquals(all.subList(all.size() - fromSeek.size(), all.size()), fromSeek, "the tail from there on");
            container.seek(0);
            assertEquals(all, drain(container), "back to the start");
        }
    }

    @Test
    @DisplayName("a seek with both tracks selected puts the audio at or before the keyframe's time")
    void seeksBothTracks() {
        try (var container = open("clip-h264-aac.mp4")) {
            var all = drain(container);
            container.seek(Duration.ofMillis(500).toNanos());
            var fromSeek = drain(container);
            // One keyframe, at zero: the whole file again, but for the AAC
            // priming packet the edit list puts before zero, which is behind
            // the keyframe's time.
            var expected =
                    all.stream().filter(p -> p.stream() == 0 || p.pts() >= 0).toList();
            assertEquals(all.size() - 1, expected.size());
            assertEquals(expected, fromSeek);
        }
    }

    @Test
    @DisplayName("the cover art is a picture track after the others, handed over first")
    void coverArt() {
        try (var container = open("tone-cover.m4a")) {
            var tracks = container.info().tracks();
            assertEquals(2, tracks.size());
            var cover = tracks.get(1);
            assertTrue(cover.attachedPicture());
            assertEquals(CodecId.PNG, cover.codec());
            assertEquals(1, cover.index());
            assertEquals(Optional.of(cover), container.info().attachedPicture());
            assertEquals(
                    CodecId.OPUS,
                    container.info().defaultTrack(MediaType.AUDIO).orElseThrow().codec());

            var packets = drain(container);
            assertEquals(1, packets.getFirst().stream());
            assertEquals(178, packets.getFirst().size());
            assertEquals(
                    ClipFixtures.packets("tone-opus.mp4"),
                    packets.subList(1, packets.size()),
                    "the same Opus packets as the file it was remuxed from");

            container.select(Set.of(0));
            container.seek(0);
            assertTrue(drain(container).stream().noneMatch(p -> p.stream() == 1), "not selected, not sent");
        }
    }

    @Test
    @DisplayName("a source that cannot seek reads a faststart file forward, and refuses one whose moov is last")
    void unseekable() {
        var faststart = ClipFixtures.io("tone-flac-faststart.mp4");
        faststart.seekable = false;
        try (var container = Mp4Container.open(ClipFixtures.source("tone-flac-faststart.mp4"), faststart)) {
            assertFalse(container.info().seekable());
            assertPackets("tone-flac-faststart.mp4", drain(container));
            var e = assertThrows(MediaException.class, () -> container.seek(0));
            assertInstanceOf(MediaError.Io.class, e.error());
        }

        var late = ClipFixtures.io("tone-flac.mp4");
        late.seekable = false;
        var e = assertThrows(MediaException.class, () -> Mp4Container.open(ClipFixtures.source("tone-flac.mp4"), late));
        var error = assertInstanceOf(MediaError.InvalidData.class, e.error());
        assertEquals("moov after mdat on a source that cannot seek", error.detail());
    }

    @Test
    @DisplayName("a fragmented file's tracks come from the moov, its samples from every moof")
    void fragmented() {
        try (var container = open("clip-av1-fragmented.mp4")) {
            var tracks = container.info().tracks();
            assertEquals(2, tracks.size());
            assertEquals(CodecId.AV1, tracks.get(0).codec());
            assertEquals(CodecId.FLAC, tracks.get(1).codec());
            assertEquals(OptionalLong.of(25), tracks.get(0).frameCount());
            assertEquals(
                    Optional.of(new FrameRate(25, 1)),
                    assertInstanceOf(TrackParams.Video.class, tracks.get(0).params())
                            .frameRate());
            assertEquals(Optional.of(Duration.ofSeconds(1)), tracks.get(0).duration(), "from the fragments");
            assertEquals(
                    Optional.of(Duration.ofSeconds(1)),
                    container.info().duration(),
                    "the longest track's, since the mvhd says 0");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-av1.mp4", "tone-flac.mp4", "tone-opus.mp4", "clip-h264-aac.mp4", "tone-cover.m4a"})
    @DisplayName("isMagic knows an MP4 by its first box")
    void magic(String name) {
        assertTrue(Mp4Container.isMagic(Arrays.copyOf(ClipFixtures.bytes(name), 16)));
    }

    @Test
    @DisplayName("isMagic does not take Matroska, or too few bytes")
    void notMagic() {
        assertFalse(Mp4Container.isMagic(Arrays.copyOf(ClipFixtures.bytes("clip-av1.mkv"), 16)));
        assertFalse(Mp4Container.isMagic(Arrays.copyOf(ClipFixtures.bytes("clip-av1.mp4"), 7)));
        assertFalse(Mp4Container.isMagic(new byte[0]));
    }

    @Test
    @DisplayName("bytes that are not an MP4 are invalid data, not an exception of another kind")
    void garbage() {
        var bytes = new byte[64];
        Arrays.fill(bytes, (byte) 'x');
        var e = assertThrows(
                MediaException.class, () -> Mp4Container.open(ClipFixtures.source("x.mp4"), new MemoryIO(bytes)));
        assertInstanceOf(MediaError.InvalidData.class, e.error());
    }

    @Test
    @DisplayName("an stsz of one size for two billion samples is invalid data, not a heap's worth of tables")
    void uniformSizesBeyondTheFile() {
        // The robustness tests' find: with a sample size the box holds no
        // table, and its count went unchecked into the sample tables' arrays.
        var bytes = ClipFixtures.bytes("clip-av1.mp4");
        var stsz = indexOf(bytes, "stsz".getBytes(StandardCharsets.US_ASCII));
        // After the type: version and flags, then sample_size and sample_count.
        System.arraycopy(new byte[] {0, 0, 0, 1, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, 0, bytes, stsz + 8, 8);
        var e = assertThrows(
                MediaException.class, () -> Mp4Container.open(ClipFixtures.source("x.mp4"), new MemoryIO(bytes)));
        assertInstanceOf(MediaError.InvalidData.class, e.error());
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
    @DisplayName("closing is idempotent, and a read after it is a programming error")
    void closes() {
        var container = open("tone-flac.mp4");
        container.close();
        container.close();
        assertThrows(IllegalStateException.class, container::read);
    }

    /// Decodes every frame of `name`'s default track of `type` through
    /// `provider`, handing each to `consumer` while it is still borrowed: what
    /// [FrameHashes#decodeVideo] does over Matroska.
    private static void decode(String name, MediaType type, DecoderProvider provider, Consumer<Frame> consumer) {
        MediaNativesRequirement.enforce();
        try (var container = open(name)) {
            var track = container.info().defaultTrack(type).orElseThrow();
            container.select(Set.of(track.index()));
            var request = container.request(track.index());
            assertTrue(provider.supports(request), provider.name() + " supports " + request.codecName());
            try (var decoder = provider.open(request)) {
                var ended = false;
                while (!ended) {
                    switch (decoder.receive()) {
                        case Received.Decoded(var frame) -> consumer.accept(frame);
                        case Received.NeedsInput _ -> {
                            try (var packet = container.read()) {
                                if (packet == null) {
                                    decoder.sendEnd();
                                } else {
                                    assertTrue(decoder.send(packet));
                                }
                            }
                        }
                        case Received.Ended _ -> ended = true;
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("decodes clip-av1.mp4 through dav1d to the pictures FFmpeg decoded from it, byte for byte")
    void decodesThroughDav1d() {
        var name = "clip-av1.mp4";
        var expected = FrameHashes.expected(name);
        var actual = new ArrayList<String>();
        decode(
                name,
                MediaType.VIDEO,
                new Dav1dProvider(),
                frame -> actual.add(FrameHashes.md5(assertInstanceOf(VideoFrame.class, frame))));
        assertEquals(expected.size(), actual.size(), "picture count");
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("decodes tone-opus.mp4 through libopus with the OpusHead made from dOps: a second, from zero")
    void decodesThroughOpus() {
        var samples = new ArrayList<Integer>();
        var firstPts = new ArrayList<Long>();
        decode("tone-opus.mp4", MediaType.AUDIO, new OpusProvider(), frame -> {
            var audio = assertInstanceOf(AudioFrame.class, frame);
            assertEquals(2, audio.channels());
            if (firstPts.isEmpty()) {
                firstPts.add(audio.ptsNanos());
            }
            samples.add(audio.samples());
        });
        var total = samples.stream().mapToInt(Integer::intValue).sum();
        assertTrue(Math.abs(total - 48_000) <= 2 * 960, "gave " + total + " samples");
        // The pre-skip is dropped, so the first frame plays from zero: the
        // first packet's -312 and the head's 312 cancel.
        assertEquals(0L, firstPts.getFirst(), "first pts");
    }
}
