package dev.goldberry.media.container.ogg;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.decoder.opus.OpusProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// The Ogg demuxer against what FFmpeg's read of the same files said:
/// `ffprobe`'s packet list is the fixture, so the two demuxers are held to the
/// same packets, byte counts, timestamps and keyframe flags, for Opus, Vorbis
/// and FLAC in Ogg.
@DisplayName("OggContainer")
class OggContainerTest {

    private static final int RATE = 48_000;
    private static final long HALF_SECOND = Duration.ofMillis(500).toNanos();

    private static OggContainer open(String name) {
        return OggContainer.open(ClipFixtures.source(name), ClipFixtures.io(name));
    }

    private static OggContainer open(byte[] bytes) {
        return OggContainer.open(Source.of(URI.create("mem:///repaged")), new MemoryIO(bytes));
    }

    /// Every packet of `container`, closed as it goes, as the expectation's shape.
    static List<ClipFixtures.Expected> drain(Container container) {
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
    @ValueSource(strings = {"tone.ogg", "tone.opus", "tone-flac.oga"})
    @DisplayName("hands over the packets ffprobe listed: stream, time, size, keyframe and duration, in file order")
    void packetsAsFfprobe(String name) {
        List<ClipFixtures.Expected> actual;
        try (var container = open(name)) {
            assertEquals(new Rational(1, RATE), container.timeBase(0), "one tick a sample");
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
            if (got.duration() != 0) {
                assertEquals(want.duration(), got.duration(), at + ": duration");
            }
        }
    }

    @Test
    @DisplayName("describes an Opus stream: 48 kHz stereo fltp, the OpusHead as extradata, a second long")
    void describesOpus() {
        try (var container = open("tone.opus")) {
            var info = container.info();
            assertTrue(info.seekable());
            assertFalse(info.live());
            assertEquals(1, info.tracks().size());
            var track = info.tracks().getFirst();
            assertEquals(CodecId.OPUS, track.codec());
            assertEquals("opus", track.codecName());
            assertTrue(track.isDefault());
            var sound = assertInstanceOf(TrackParams.Audio.class, track.params());
            assertAll(
                    () -> assertEquals(RATE, sound.sampleRate()),
                    () -> assertEquals(2, sound.channels()),
                    () -> assertEquals(Optional.of("fltp"), sound.sampleFormat()));
            // The pre-skip is taken off the last granule: one second of sound.
            assertEquals(Optional.of(Duration.ofSeconds(1)), info.duration());
            assertEquals(info.duration(), track.duration());

            var request = container.request(0);
            assertEquals(CodecId.OPUS, request.codec());
            assertEquals(19, request.extradata().byteSize());
            assertEquals("OpusHead", ascii(request.extradata().asSlice(0, 8).toArray(JAVA_BYTE)));
            assertEquals(new Rational(1, RATE), request.timeBase());
        }
    }

    @Test
    @DisplayName(
            "describes a Vorbis stream: the rate and channels of the identification header, the three headers laced")
    void describesVorbis() {
        try (var container = open("tone.ogg")) {
            var track = container.info().tracks().getFirst();
            assertEquals(CodecId.VORBIS, track.codec());
            assertEquals("vorbis", track.codecName());
            var sound = assertInstanceOf(TrackParams.Audio.class, track.params());
            assertAll(
                    () -> assertEquals(RATE, sound.sampleRate()),
                    () -> assertEquals(2, sound.channels()),
                    () -> assertEquals(Optional.of("fltp"), sound.sampleFormat()));
            assertEquals(Optional.of(Duration.ofSeconds(1)), container.info().duration());

            var extradata = container.request(0).extradata().toArray(JAVA_BYTE);
            assertEquals(2, extradata[0], "two lengths before three packets");
            var text = new String(extradata, StandardCharsets.ISO_8859_1);
            assertTrue(text.contains("\u0001vorbis"), "the identification header");
            assertTrue(text.contains("\u0003vorbis"), "the comment header");
            assertTrue(text.contains("\u0005vorbis"), "the setup header");
            // The lengths: 30 for the identification header, then the comment's.
            assertEquals(30, extradata[1]);
        }
    }

    @Test
    @DisplayName("describes a FLAC stream: s16 from STREAMINFO, a whole stream header as extradata")
    void describesFlac() {
        try (var container = open("tone-flac.oga")) {
            var track = container.info().tracks().getFirst();
            assertEquals(CodecId.FLAC, track.codec());
            var sound = assertInstanceOf(TrackParams.Audio.class, track.params());
            assertAll(
                    () -> assertEquals(RATE, sound.sampleRate()),
                    () -> assertEquals(2, sound.channels()),
                    () -> assertEquals(Optional.of("s16"), sound.sampleFormat()));
            assertEquals(Optional.of(Duration.ofSeconds(1)), container.info().duration());

            var extradata = container.request(0).extradata().toArray(JAVA_BYTE);
            assertEquals(4 + 4 + 34, extradata.length);
            assertEquals("fLaC", ascii(Arrays.copyOf(extradata, 4)));
            assertEquals((byte) 0x80, extradata[4], "STREAMINFO, the last block");
            assertEquals(34, extradata[7], "STREAMINFO's length");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tone.ogg", "tone.opus", "tone-flac.oga"})
    @DisplayName("seeks to the page holding the target, at or before it, and back to the start")
    void seeks(String name) {
        try (var container = open(name)) {
            var all = drain(container);
            assertNull(container.read(), "the end stays the end");
            container.seek(HALF_SECOND);
            var fromSeek = drain(container);
            assertFalse(fromSeek.isEmpty());
            assertTrue(
                    fromSeek.getFirst().pts() <= RATE / 2,
                    "at or before the target: " + fromSeek.getFirst().pts());
            assertEquals(all.subList(all.size() - fromSeek.size(), all.size()), fromSeek, "the tail from there on");
            container.seek(0);
            assertEquals(all, drain(container), "back to the start");
        }
    }

    @Test
    @DisplayName("select() keeps the other streams' packets out, and a seek follows the selected stream")
    void selects() {
        try (var container = open("tone.opus")) {
            container.select(Set.of(7));
            assertNull(container.read());
            container.select(Set.of(0));
            container.seek(0);
            assertEquals(51, drain(container).size());
        }
    }

    @Test
    @DisplayName("isMagic: OggS, and nothing else")
    void magic() {
        assertTrue(OggContainer.isMagic(ClipFixtures.bytes("tone.opus")));
        assertTrue(OggContainer.isMagic("OggS".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(OggContainer.isMagic("Ogg".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(OggContainer.isMagic(ClipFixtures.bytes("tone.flac")));
        assertFalse(OggContainer.isMagic(ClipFixtures.bytes("clip-av1.mkv")));
    }

    @Test
    @DisplayName("refuses bytes with no page in them")
    void refusesJunk() {
        var e = assertThrows(MediaException.class, () -> open(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
        assertInstanceOf(MediaError.InvalidData.class, e.error());
    }

    private static String ascii(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /// The same Opus stream laid out in other pages, written by [OggWriter]:
    /// what the fixtures, two pages each, cannot show of the page handling.
    @Nested
    @DisplayName("re-paged")
    class Repaged {

        private static final long LAST_GRANULE = 48_312;

        private final byte[] original = ClipFixtures.bytes("tone.opus");

        @Test
        @DisplayName("five packets a page: the same packets, timed from each page's granule")
        void manyPages() {
            List<ClipFixtures.Expected> expected;
            try (var container = open("tone.opus")) {
                expected = drain(container);
            }
            try (var container = open(OggWriter.repagedOpus(original, 5, LAST_GRANULE))) {
                assertEquals(
                        Optional.of(Duration.ofSeconds(1)), container.info().duration());
                assertEquals(expected, drain(container));
            }
        }

        @ParameterizedTest(name = "{0} ms")
        @ValueSource(longs = {100, 250, 500, 900, 1000, 5000})
        @DisplayName("a seek bisects the pages: the first packet out is at or before the target, within two pages")
        void bisects(long millis) {
            List<ClipFixtures.Expected> all;
            var bytes = OggWriter.repagedOpus(original, 5, LAST_GRANULE);
            try (var container = open(bytes)) {
                all = drain(container);
                container.seek(Duration.ofMillis(millis).toNanos());
                var fromSeek = drain(container);
                assertFalse(fromSeek.isEmpty());
                var first = fromSeek.getFirst().pts();
                var target = Math.min(millis * RATE / 1000, all.getLast().pts());
                assertTrue(first <= target, "at or before " + target + ": " + first);
                assertTrue(target - first <= 2 * 5 * 960, "within two pages of " + target + ": " + first);
                assertEquals(all.subList(all.size() - fromSeek.size(), all.size()), fromSeek);
            }
        }

        @Test
        @DisplayName("a packet cut across two pages is joined, and the page that completes none has no granule")
        void continuedPacket() {
            var packets = OggWriter.packets(original);
            var audio = packets.subList(2, packets.size());
            // The third audio packet, over 255 bytes: 255 on one page, the rest
            // on the next.
            var cut = audio.get(2);
            assertTrue(cut.length > 255, cut.length + " bytes");
            var head = Arrays.copyOf(cut, 255);
            var tail = Arrays.copyOfRange(cut, 255, cut.length);
            var pages = new ArrayList<OggWriter.Page>();
            pages.add(new OggWriter.Page(OggPage.BOS, 0, List.of(packets.get(0))));
            pages.add(new OggWriter.Page(0, 0, List.of(packets.get(1))));
            pages.add(new OggWriter.Page(0, 2 * 960, List.of(audio.get(0), audio.get(1))));
            pages.add(new OggWriter.Page(0, -1, List.of(head), true));
            pages.add(new OggWriter.Page(OggPage.CONTINUED, 5 * 960, List.of(tail, audio.get(3), audio.get(4))));
            pages.add(new OggWriter.Page(0, 50 * 960, audio.subList(5, 50)));
            pages.add(new OggWriter.Page(OggPage.EOS, LAST_GRANULE, audio.subList(50, audio.size())));
            List<ClipFixtures.Expected> expected;
            try (var container = open("tone.opus")) {
                expected = drain(container);
            }
            try (var container = open(OggWriter.write(99, pages))) {
                assertEquals(expected, drain(container));
                // A seek that lands on the page completing the cut packet drops
                // the half it cannot see the start of, and goes on from there.
                container.seek(Duration.ofMillis(100).toNanos());
                var fromSeek = drain(container);
                assertEquals(expected.subList(3, expected.size()), fromSeek);
            }
        }

        @Test
        @DisplayName("a stream nobody here decodes is a track named by its first bytes, with no packets")
        void unknownCodec() {
            var pages = List.of(
                    new OggWriter.Page(
                            OggPage.BOS, 0, List.of("\u0080theora headers".getBytes(StandardCharsets.ISO_8859_1))),
                    new OggWriter.Page(0, 0, List.of(new byte[] {1, 2, 3})),
                    new OggWriter.Page(OggPage.EOS, 100, List.of(new byte[] {4, 5, 6})));
            try (var container = open(OggWriter.write(7, pages))) {
                var track = container.info().tracks().getFirst();
                assertEquals(CodecId.UNKNOWN, track.codec());
                assertEquals("theora", track.codecName());
                assertEquals(MediaType.DATA, track.type());
                assertNull(container.read());
            }
        }
    }

    /// libopus through the SPI, over this container: the Ogg Opus fixture
    /// decodes to the tone, as the WebM one does.
    @Nested
    @DisplayName("with the Opus decoder")
    class WithOpus {

        private static final double RMS = 12_000d / 32_768 / Math.sqrt(2);

        @Test
        @DisplayName("decodes tone.opus: 48 000 samples within a frame, the right loudness and pitch, from zero")
        void decodesTheTone() {
            MediaNativesRequirement.enforce();
            var provider = new OpusProvider();
            var frames = new ArrayList<float[]>();
            var firstPts = new long[] {Long.MAX_VALUE};
            try (var container = open("tone.opus")) {
                var request = container.request(0);
                assertTrue(provider.supports(request));
                try (var decoder = provider.open(request)) {
                    decodeAll(container, decoder, frame -> {
                        var audio = assertInstanceOf(AudioFrame.class, frame);
                        assertEquals(SampleFormat.F32, audio.format());
                        assertEquals(RATE, audio.sampleRate());
                        assertEquals(2, audio.channels());
                        if (frames.isEmpty()) {
                            firstPts[0] = audio.ptsNanos();
                        }
                        frames.add(audio.planes()
                                .getFirst()
                                .asSlice(0, (long) audio.samples() * 2 * Float.BYTES)
                                .toArray(JAVA_FLOAT));
                    });
                }
            }
            var total = frames.stream().mapToInt(f -> f.length / 2).sum();
            assertTrue(Math.abs(total - RATE) <= 2 * 960, total + " samples");
            assertTrue(Math.abs(firstPts[0]) < 1_000_000, "first pts " + firstPts[0]);
            var left = new float[total];
            var at = 0;
            for (var frame : frames) {
                for (var i = 0; i < frame.length / 2; i++) {
                    left[at++] = frame[i * 2];
                }
            }
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
            assertTrue(Math.abs(rms - RMS) / RMS < 0.1, "RMS " + rms + ", expected about " + RMS);
            var expected = 2 * 440 * (total - from) / RATE;
            assertTrue(Math.abs(crossings - expected) <= 6, crossings + " crossings, expected " + expected);
        }
    }

    /// Runs the SPI's loop to the end.
    static void decodeAll(Container container, Decoder decoder, Consumer<Frame> consumer) {
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
