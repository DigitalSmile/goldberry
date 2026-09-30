package io.github.digitalsmile.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.codec.Packet;
import io.github.digitalsmile.goldberry.media.codec.Rational;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.TrackParams;
import io.github.digitalsmile.goldberry.media.platform.bitstream.ParameterSets;
import io.github.digitalsmile.goldberry.media.platform.bitstream.ParameterSetsTest;
import io.github.digitalsmile.goldberry.media.platform.fixtures.Fixtures;

/// The decoders when things go wrong, and when a stream is played twice: what
/// the Engine's fallback ladder and its seeks rely on (`docs/goldberry-media.md`
/// §3, §5).
@DisplayName("The platform decoders, failing and recovering")
class DecoderFailureTest {

    private static final TrackParams VIDEO = new TrackParams.Video(
            160, 90, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty());
    private static final TrackParams STEREO =
            new TrackParams.Audio(48_000, 2, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
    private static final Rational MILLISECONDS = new Rational(1, 1000);

    private Frameworks fw;

    @BeforeEach
    void requireMac() {
        fw = PlatformRequirement.enforce();
    }

    /// Sends `count` packets of noise, receiving between them, and answers the
    /// failure that stops it, if one does.
    private static Optional<RuntimeException> feedNoise(Decoder decoder, int count, Arena arena) {
        var noise = arena.allocate(64).fill((byte) 0x5A);
        // A length prefix that claims the whole packet, so the noise reaches the
        // decoder rather than being refused as framing.
        noise.set(JAVA_BYTE, 3, (byte) 60);
        try {
            for (var i = 0; i < count; i++) {
                while (decoder.receive() instanceof Received.Decoded) {
                    // Whatever noise decodes to is not the point.
                }
                decoder.send(Packet.of(noise, 0, i * 40L, i * 40L, 40, true, MILLISECONDS));
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.of(e);
        }
    }

    @Test
    @DisplayName("VideoToolbox drops a bad packet, and gives up on a run of them so the Engine can fall back")
    void videoGivesUpOnARun() {
        try (var arena = Arena.ofConfined()) {
            var request = new DecoderRequest(
                    CodecId.H264,
                    "h264",
                    VIDEO,
                    arena.allocateFrom(JAVA_BYTE, ParameterSetsTest.AVCC_HIGH),
                    MILLISECONDS);
            try (var decoder = new VideoToolboxProvider().open(request)) {
                // One bad packet is dropped: the decoder goes on.
                assertTrue(feedNoise(decoder, 1, arena).isEmpty());
                var failure = feedNoise(decoder, VideoToolboxDecoder.MAX_CONSECUTIVE_FAILURES + 5, arena);
                var status = assertInstanceOf(OsStatus.Failure.class, failure.orElseThrow());
                assertTrue(status.getMessage().contains("in a row"), status.getMessage());
            }
        }
    }

    @Test
    @DisplayName("AudioToolbox drops a bad packet, and gives up on a run of them")
    void audioGivesUpOnARun() {
        try (var arena = Arena.ofConfined()) {
            var request = new DecoderRequest(
                    CodecId.AAC, "aac", STEREO, arena.allocateFrom(JAVA_BYTE, (byte) 0x11, (byte) 0x90), MILLISECONDS);
            try (var decoder = new AudioToolboxProvider().open(request)) {
                assertTrue(feedNoise(decoder, 1, arena).isEmpty());
                var failure = feedNoise(decoder, AudioToolboxDecoder.MAX_CONSECUTIVE_FAILURES + 5, arena);
                assertInstanceOf(OsStatus.Failure.class, failure.orElseThrow());
            }
        }
    }

    @Test
    @DisplayName("parameter sets VideoToolbox cannot read fail the open, and leave nothing behind")
    void videoOpenFails() {
        // An SPS that is noise, with the fixture's PPS: what Core Media parses
        // first, it cannot read.
        var sets = List.of(
                new byte[] {0x67, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                ParameterSets.h264(ParameterSetsTest.AVCC_HIGH).parameterSets().getLast());
        var configuration = new ParameterSets.Configuration(sets, 4, new ParameterSets.Shape(2, 8, 1));
        try (var arena = Arena.ofConfined()) {
            var request = new DecoderRequest(CodecId.H264, "h264", VIDEO, arena.allocate(1), MILLISECONDS);
            assertThrows(OsStatus.Failure.class, () -> new VideoToolboxDecoder(fw, request, configuration));
        }
    }

    @Test
    @DisplayName("an AAC configuration AudioToolbox cannot read fails the open")
    void audioOpenFails() {
        try (var arena = Arena.ofConfined()) {
            // Audio object type 0, which is no codec at all.
            var request = new DecoderRequest(
                    CodecId.AAC, "aac", STEREO, arena.allocateFrom(JAVA_BYTE, (byte) 0, (byte) 0), MILLISECONDS);
            assertThrows(RuntimeException.class, () -> new AudioToolboxProvider().open(request));
        }
    }

    @Test
    @DisplayName("a decoder that was drained is ready again after a flush: the same pictures, the same times")
    void videoPlaysTwice() {
        assertPlaysTwice("clip-hevc.mp4", MediaType.VIDEO, new VideoToolboxProvider()::open);
    }

    @Test
    @DisplayName("an audio decoder that was drained is ready again after a flush")
    void audioPlaysTwice() {
        assertPlaysTwice("tone-eac3.mp4", MediaType.AUDIO, new AudioToolboxProvider()::open);
    }

    private static void assertPlaysTwice(String name, MediaType type, Function<DecoderRequest, Decoder> open) {
        try (var demuxer = Fixtures.demux(name)) {
            var track = demuxer.info().defaultTrack(type).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = open.apply(demuxer.request(track.index()))) {
                var first = new ArrayList<Long>();
                Fixtures.run(demuxer, decoder, frame -> first.add(frame.ptsNanos()));
                // Ended stays ended until a flush.
                assertEquals(Received.ENDED, decoder.receive());
                decoder.flush();
                demuxer.seek(0);
                var second = new ArrayList<Long>();
                Fixtures.run(demuxer, decoder, frame -> second.add(frame.ptsNanos()));
                assertEquals(first, second);
            }
        }
    }
}
