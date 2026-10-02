package dev.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// Hardware decode against FFmpeg and the machine's own device: the smoke lane,
/// and the fall to software with injected failures.
///
/// Every test makes its own [Hardware], so what one learns ("this device cannot
/// decode AV1") does not leak into the next, or into the process-wide
/// [HardwareDecoding#AUTO] other tests use.
///
/// The tests that need a real device skip where there is none: on Linux, whose
/// build has no hwaccel by default, and wherever the device will not open. On an
/// Apple Silicon Mac they run against VideoToolbox, which decodes VP9 there, and
/// AV1 only from the M3.
@DisplayName("Hardware decode, against FFmpeg and the machine's device")
class HardwareDecodeTest {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final long FRAME_NANOS = 40_000_000L;

    private Ffmpeg ffmpeg;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
        ffmpeg = FfmpegLibraries.get();
    }

    /// A policy of this platform's device types, with a record of its own.
    private static Hardware policy(Hardware.Calls calls) {
        return new Hardware(Hardware.deviceTypes(FfmpegPlatform.current()), calls);
    }

    private static Hardware policy() {
        return policy(Hardware.Calls.FFMPEG);
    }

    /// Opens `name`'s video track, and hands the demuxer and the track's index to
    /// `body`.
    private void withVideo(String name, VideoBody body) {
        try (var demuxer = Demuxer.open(
                ffmpeg, Source.of(URI.create("mem:///" + name)), new MemoryIO(VideoDecodeTest.fixture(name)))) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            body.run(demuxer, track.index());
        }
    }

    @FunctionalInterface
    private interface VideoBody {
        void run(Demuxer demuxer, int stream);
    }

    /// Decodes every picture `demuxer` has left through `decoder`, handing each to
    /// `consumer` while it is borrowed.
    private static void decodeAll(Demuxer demuxer, FfmpegDecoder decoder, Consumer<VideoFrame> consumer) {
        var ended = false;
        while (!ended) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> consumer.accept(assertInstanceOf(VideoFrame.class, frame));
                case Received.NeedsInput _ -> {
                    try (var packet = demuxer.read()) {
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

    /// The luma of every picture of `name`, as 8-bit values, decoded with
    /// `hardware`, and the pixel formats they came in.
    private record Decoded(List<byte[]> luma, List<PixelFormat> formats, List<Long> pts, String name) {}

    private Decoded decode(String name, Hardware hardware) {
        var luma = new ArrayList<byte[]>();
        var formats = new ArrayList<PixelFormat>();
        var pts = new ArrayList<Long>();
        var described = new String[1];
        withVideo(name, (demuxer, stream) -> {
            try (var decoder =
                    FfmpegDecoder.open(ffmpeg, demuxer.codecParameters(stream), demuxer.timeBase(stream), hardware)) {
                decodeAll(demuxer, decoder, frame -> {
                    assertEquals(WIDTH, frame.width());
                    assertEquals(HEIGHT, frame.height());
                    formats.add(frame.format());
                    pts.add(frame.ptsNanos());
                    luma.add(luma8(frame));
                });
                described[0] = decoder.describe();
            }
        });
        return new Decoded(luma, formats, pts, described[0]);
    }

    /// Plane 0 of `frame`, row by row without the padding, at 8 bits a sample.
    /// Every format here keeps luma alone in plane 0: 8-bit in I420 and NV12,
    /// 10-bit in the low bits of I010 and the high bits of P010.
    private static byte[] luma8(VideoFrame frame) {
        var plane = frame.planes().getFirst();
        var stride = frame.strides().getFirst();
        var out = new byte[frame.width() * frame.height()];
        for (var y = 0; y < frame.height(); y++) {
            for (var x = 0; x < frame.width(); x++) {
                var value =
                        switch (frame.format()) {
                            case I420, NV12 -> plane.get(JAVA_BYTE, (long) y * stride + x) & 0xFF;
                            case I010 -> (plane.get(JAVA_SHORT_UNALIGNED, (long) y * stride + 2L * x) & 0x3FF) >> 2;
                            case P010 -> (plane.get(JAVA_SHORT_UNALIGNED, (long) y * stride + 2L * x) & 0xFFFF) >> 8;
                        };
                out[y * frame.width() + x] = (byte) value;
            }
        }
        return out;
    }

    private static final java.lang.foreign.ValueLayout.OfShort JAVA_SHORT_UNALIGNED = JAVA_SHORT.withByteAlignment(1);

    /// The codec id of `name`'s video track.
    private int codecId(String name) {
        var id = new int[1];
        withVideo(name, (demuxer, stream) -> id[0] = AvCodecParametersView.codecId(demuxer.codecParameters(stream)));
        return id[0];
    }

    /// Skips unless this build has a hardware path for `name`'s codec on this
    /// platform's device.
    private void assumeHardwarePath(String name) {
        Assumptions.assumeTrue(
                HardwareDecoder.choose(ffmpeg, codecId(name), policy(), false) != null,
                "this build has no hardware path for " + name + " on "
                        + Hardware.deviceTypes(FfmpegPlatform.current()));
    }

    /// Skips unless the device opens, and decodes `name` on it, and returns what
    /// it decoded.
    private Decoded assumeDecodedOnDevice(String name) {
        assumeHardwarePath(name);
        var decoded = decode(name, policy());
        Assumptions.assumeTrue(
                decoded.name().startsWith(Decoders.BUILT_IN + " ("),
                "the device did not decode " + name + " here: " + decoded.name());
        return decoded;
    }

    @Test
    @DisplayName("off decodes in software, and the ladder has no hardware rung")
    void off() {
        var decoded = decode("clip-vp9.webm", Hardware.OFF);
        assertEquals(Decoders.BUILT_IN, decoded.name());
        assertTrue(decoded.formats().stream().allMatch(PixelFormat.I420::equals));
        withVideo(
                "clip-vp9.webm",
                (demuxer, stream) -> assertFalse(
                        FfmpegDecoder.hardwareCandidate(ffmpeg, demuxer.codecParameters(stream), Hardware.OFF)));
    }

    @Test
    @DisplayName("AUTO is the platform's device types, and one policy per process; OFF is none")
    void policies() {
        assertTrue(Hardware.of(HardwareDecoding.OFF).deviceTypes().isEmpty());
        assertEquals(
                Hardware.deviceTypes(FfmpegPlatform.current()),
                Hardware.of(HardwareDecoding.AUTO).deviceTypes());
        assertTrue(Hardware.of(HardwareDecoding.AUTO) == Hardware.of(HardwareDecoding.AUTO));
        assertEquals(List.of("videotoolbox"), Hardware.deviceTypes(FfmpegPlatform.of("Mac OS X", "aarch64")));
        assertEquals(List.of("d3d11va"), Hardware.deviceTypes(FfmpegPlatform.of("Windows 11", "amd64")));
        assertEquals(List.of("vaapi"), Hardware.deviceTypes(FfmpegPlatform.of("Linux", "amd64")));
        assertEquals(List.of(), Hardware.deviceTypes(null));
    }

    @Test
    @DisplayName("audio never goes to a device")
    void audioStaysInSoftware() {
        try (var demuxer = Demuxer.open(
                ffmpeg,
                Source.of(URI.create("mem:///tone.opus")),
                new MemoryIO(VideoDecodeTest.fixture("tone.opus")))) {
            var track = demuxer.info().defaultTrack(MediaType.AUDIO).orElseThrow();
            assertFalse(FfmpegDecoder.hardwareCandidate(ffmpeg, demuxer.codecParameters(track.index()), policy()));
        }
    }

    @Test
    @DisplayName("VP9 on the device: NV12 pictures, copied back, with the software decoder's luma exactly")
    void vp9OnTheDevice() {
        var hardware = assumeDecodedOnDevice("clip-vp9.webm");
        var software = decode("clip-vp9.webm", Hardware.OFF);
        assertEquals(
                FfmpegDecoder.describe(
                        Hardware.deviceTypes(FfmpegPlatform.current()).getFirst()),
                hardware.name());
        assertEquals(25, hardware.formats().size());
        assertTrue(
                hardware.formats().stream().allMatch(PixelFormat.NV12::equals),
                hardware.formats().toString());
        assertEquals(software.pts(), hardware.pts(), "the same times, copied over from the surface");
        for (var i = 0; i < 25; i++) {
            // VP9 decoding is bit-exact by specification, on a device as anywhere.
            assertArrayEquals(software.luma().get(i), hardware.luma().get(i), "picture " + i);
        }
        assertEquals(24 * FRAME_NANOS, hardware.pts().getLast());
    }

    @Test
    @DisplayName("10-bit VP9 on the device comes back as P010, with the software decoder's luma")
    void vp9TenBitOnTheDevice() {
        var hardware = assumeDecodedOnDevice("clip-vp9-10bit.webm");
        var software = decode("clip-vp9-10bit.webm", Hardware.OFF);
        assertTrue(
                hardware.formats().stream().allMatch(PixelFormat.P010::equals),
                hardware.formats().toString());
        assertTrue(software.formats().stream().allMatch(PixelFormat.I010::equals));
        for (var i = 0; i < software.luma().size(); i++) {
            assertArrayEquals(software.luma().get(i), hardware.luma().get(i), "picture " + i);
        }
    }

    @Test
    @DisplayName("a device that will not open is software from the start, and is not asked again")
    void deviceWillNotOpen() {
        assumeHardwarePath("clip-vp9.webm");
        var asked = new AtomicInteger();
        var failing = policy(new Hardware.Calls() {
            @Override
            public int createDevice(Ffmpeg ffmpeg, MemorySegment holder, int type) {
                asked.incrementAndGet();
                return ffmpeg.constants().averrorEio();
            }

            @Override
            public int transfer(Ffmpeg ffmpeg, MemorySegment destination, MemorySegment source) {
                throw new AssertionError("no device, nothing to copy back");
            }
        });
        var decoded = decode("clip-vp9.webm", failing);
        assertEquals(Decoders.BUILT_IN, decoded.name());
        assertEquals(25, decoded.formats().size());
        assertTrue(decoded.formats().stream().allMatch(PixelFormat.I420::equals));
        assertEquals(1, asked.get());

        decode("clip-vp9.webm", failing);
        assertEquals(1, asked.get(), "a device type that failed for VP9 is not tried again");
        withVideo(
                "clip-vp9.webm",
                (demuxer, stream) -> assertTrue(
                        FfmpegDecoder.hardwareCandidate(ffmpeg, demuxer.codecParameters(stream), failing),
                        "the rung stays, so the ladder's count does not move"));
    }

    @Test
    @DisplayName("copy-back failing mid-stream is thrown for the ladder, not as a playback error")
    void copyBackFailsMidStream() {
        assumeDecodedOnDevice("clip-vp9.webm");
        var copies = new AtomicInteger();
        var failing = policy(new Hardware.Calls() {
            @Override
            public int createDevice(Ffmpeg ffmpeg, MemorySegment holder, int type) {
                return Hardware.Calls.FFMPEG.createDevice(ffmpeg, holder, type);
            }

            @Override
            public int transfer(Ffmpeg ffmpeg, MemorySegment destination, MemorySegment source) {
                return copies.incrementAndGet() > 5
                        ? ffmpeg.constants().averrorEio()
                        : Hardware.Calls.FFMPEG.transfer(ffmpeg, destination, source);
            }
        });
        var pictures = new AtomicInteger();
        withVideo("clip-vp9.webm", (demuxer, stream) -> {
            try (var decoder =
                    FfmpegDecoder.open(ffmpeg, demuxer.codecParameters(stream), demuxer.timeBase(stream), failing)) {
                var thrown = assertThrows(
                        FfmpegException.class, () -> decodeAll(demuxer, decoder, _ -> pictures.incrementAndGet()));
                assertEquals("av_hwframe_transfer_data", thrown.function());
            }
        });
        assertEquals(5, pictures.get());
        var vp9 = codecId("clip-vp9.webm");
        assertFalse(
                failing.failed(
                        vp9, Hardware.deviceTypes(FfmpegPlatform.current()).getFirst()),
                "a device that has decoded is not written off for one failed copy");
    }

    @Test
    @DisplayName(
            "a device with no engine for the codec fails before its first picture, for the ladder, and is written off")
    void noEngineForTheCodec() {
        assumeHardwarePath("clip-av1.mkv");
        var policy = policy();
        var av1 = codecId("clip-av1.mkv");
        var device = Hardware.deviceTypes(FfmpegPlatform.current()).getFirst();
        var first = new ArrayList<VideoFrame.ColorMatrix>();
        var failure = new RuntimeException[1];
        withVideo("clip-av1.mkv", (demuxer, stream) -> {
            try (var decoder =
                    FfmpegDecoder.open(ffmpeg, demuxer.codecParameters(stream), demuxer.timeBase(stream), policy)) {
                try {
                    decodeAll(demuxer, decoder, frame -> first.add(frame.matrix()));
                } catch (RuntimeException e) {
                    failure[0] = e;
                }
            }
        });
        if (failure[0] == null) {
            // Every picture came: from a device that decodes AV1 (Apple M3 and
            // later), or from software in the same decoder, when the device refused
            // AV1 as the formats were agreed and FFmpeg went on without it, as a
            // GPU-less Windows runner's Direct3D does. A device that refused is
            // written off, so the next AV1 track goes to software at once.
            assertEquals(25, first.size());
            return;
        }
        assertInstanceOf(FfmpegException.class, failure[0], "for the ladder, not a MediaException");
        assertFalse(failure[0] instanceof MediaException);
        assertTrue(policy.failed(av1, device), "written off for AV1");

        // The next decoder of AV1 is dav1d, in software, at once.
        var decoded = decode("clip-av1.mkv", policy);
        assertEquals(Decoders.BUILT_IN, decoded.name());
        assertEquals(25, decoded.formats().size());
    }

    @Test
    @DisplayName("the ladder: the built-in decoder on the device, then in software, then nothing")
    void ladder() {
        assumeDecodedOnDevice("clip-vp9.webm");
        var policy = policy();
        withVideo("clip-vp9.webm", (demuxer, stream) -> {
            var onDevice = Decoders.open(ffmpeg, demuxer, stream, List.of(), policy, 0);
            try (var _ = onDevice.decoder()) {
                assertTrue(onDevice.provider().startsWith(Decoders.BUILT_IN + " ("), onDevice.provider());
            }
            var software = Decoders.open(ffmpeg, demuxer, stream, List.of(), policy, 1);
            try (var _ = software.decoder()) {
                assertEquals(Decoders.BUILT_IN, software.provider());
            }
            var none = assertThrows(
                    MediaException.class, () -> Decoders.open(ffmpeg, demuxer, stream, List.of(), policy, 2));
            assertInstanceOf(MediaError.UnsupportedCodec.class, none.error());
        });
    }

    @Test
    @DisplayName("get_format takes the device's format when offered, FFmpeg's choice when not, and never throws")
    void getFormat() {
        assumeHardwarePath("clip-vp9.webm");
        var policy = policy();
        var vp9 = codecId("clip-vp9.webm");
        var choice = HardwareDecoder.choose(ffmpeg, vp9, policy, true);
        assertNotNull(choice);
        var hardware = HardwareDecoder.open(ffmpeg, policy, choice);
        Assumptions.assumeTrue(hardware != null, "the device did not open");
        var video = ffmpeg.constants().video();
        var context = ffmpeg.codec().allocContext3().call(choice.codec());
        try (hardware;
                var arena = Arena.ofConfined()) {
            var both = arena.allocateFrom(JAVA_INT, choice.pixelFormat(), video.pixFmtYuv420p(), video.pixFmtNone());
            assertEquals(choice.pixelFormat(), hardware.getFormat(context, both));
            assertTrue(hardware.negotiated());

            var softwareOnly = arena.allocateFrom(JAVA_INT, video.pixFmtYuv420p(), video.pixFmtNone());
            assertEquals(video.pixFmtYuv420p(), hardware.getFormat(context, softwareOnly));
            assertFalse(hardware.negotiated());

            var nothing = arena.allocateFrom(JAVA_INT, video.pixFmtNone());
            assertEquals(video.pixFmtNone(), hardware.getFormat(context, nothing));
        } finally {
            Pointers.freeThrough(context, ffmpeg.codec().freeContext()::call);
        }
    }
}
