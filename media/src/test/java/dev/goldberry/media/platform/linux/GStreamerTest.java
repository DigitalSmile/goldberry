package dev.goldberry.media.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.platform.fixtures.Fixtures;

/// GStreamer itself, as the providers find it: the layout the bindings rely on,
/// the registry, and choosing among decoders.
@DisplayName("GStreamer, bound")
class GStreamerTest {

    private GStreamer gs;

    @BeforeEach
    void requireGStreamer() {
        gs = GStreamerRequirement.enforce();
    }

    @Test
    @DisplayName("lays its structs out where the bindings read and write them")
    void layout() {
        assertEquals(List.of(), GstLayout.check(gs.gst(), gs.video()));
    }

    @Test
    @DisplayName("has a parser and a decoder for the H.264 of the fixtures, and none for a codec nobody makes")
    void registry() {
        assertTrue(gs.gst().hasElement("h264parse"));
        assertFalse(gs.gst().hasElement("goldberry-no-such-element"));
        assertFalse(gs.gst()
                .decoders("video/x-h264, width=(int)160, height=(int)90", true)
                .isEmpty());
        assertTrue(gs.gst().decoders("video/x-goldberry-no-such-codec", true).isEmpty());
    }

    @Test
    @DisplayName("marks the decoders whose class says Hardware, and a software-only request gets none of them")
    void hardwareDecoders() {
        var all = gs.gst().decoders("video/x-h264, width=(int)1280, height=(int)720", true);
        assertTrue(all.stream().anyMatch(candidate -> !candidate.hardware()), "a software H.264 decoder: " + all);
        assertTrue(Gst.Candidate.software(all).stream().noneMatch(Gst.Candidate::hardware));
        // NVIDIA's decoder, where the machine has one, is the hardware one.
        all.stream()
                .filter(candidate -> candidate.name().equals("nvh264dec"))
                .forEach(candidate -> assertTrue(candidate.hardware(), candidate.toString()));
    }

    @Test
    @DisplayName("with hardware decoding off, the provider opens a software decoder, which decodes every picture")
    void softwareOnly() {
        var hardware = gs.gst().decoders("video/x-h264, width=(int)1280, height=(int)720", true).stream()
                .filter(Gst.Candidate::hardware)
                .map(Gst.Candidate::name)
                .toList();
        var provider = new GStreamerVideoProvider();
        var chosen = new ArrayList<String>();
        var pictures = new ArrayList<VideoFrame>();
        Fixtures.decode(
                "clip-h264-720p.mp4",
                MediaType.VIDEO,
                request -> {
                    var decoder = (GstVideoDecoder) provider.open(request.withHardware(HardwareDecoding.OFF));
                    chosen.add(decoder.decoder());
                    return decoder;
                },
                frame -> pictures.add((VideoFrame) frame));
        assertEquals(1, chosen.size());
        assertFalse(hardware.contains(chosen.getFirst()), chosen.getFirst() + " is one of " + hardware);
        assertEquals(5, pictures.size(), "0.2 s at 25 fps");
        if (!hardware.isEmpty()) {
            // Allowed, the machine's video engine is GStreamer's first choice.
            try (var demuxer = Fixtures.demux("clip-h264-720p.mp4")) {
                var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
                try (var auto = (GstVideoDecoder) provider.open(demuxer.request(track.index()))) {
                    assertTrue(hardware.contains(auto.decoder()), auto.decoder() + " with hardware allowed");
                }
            }
        }
    }

    @Test
    @DisplayName("a decoder that does not exist is passed over at the start")
    void passesOverAMissingDecoder() {
        var frames = decode(List.of(new Gst.Candidate("goldberry-no-such-decoder", 512), avdec()));
        assertEquals(25, frames.size());
    }

    @Test
    @DisplayName("a decoder that starts and cannot decode is replaced before its first frame, with every packet")
    void replacesADecoderThatFails() {
        // `funnel` starts like any element: its caps are anything until data
        // flows. Then it hands H.264 to videoconvert, which refuses it, and the
        // pipeline fails, which is how a hardware decoder that cannot take the
        // stream fails.
        var hashes = new ArrayList<String>();
        var decoders = new ArrayList<GstVideoDecoder>();
        Fixtures.decode(
                "clip-h264-high.mp4",
                MediaType.VIDEO,
                request -> {
                    var decoder = new GstVideoDecoder(
                            gs, GstCodec.H264, request, List.of(new Gst.Candidate("funnel", 512), avdec()));
                    assertEquals("funnel", decoder.decoder(), "the first candidate starts");
                    decoders.add(decoder);
                    return decoder;
                },
                frame -> {
                    assertEquals("avdec_h264", decoders.getFirst().decoder(), "the next one decodes");
                    hashes.add(Fixtures.md5(assertInstanceOf(VideoFrame.class, frame)));
                });
        var expected = Fixtures.frameHashes("clip-h264-high.mp4");
        assertEquals(expected.size(), hashes.size(), "the pictures of every packet, the first ones included");
        for (var i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).md5(), hashes.get(i), "picture " + i);
        }
    }

    @Test
    @DisplayName("a packet asked for is taken when the failure lands between the asking and the sending")
    void takesThePacketItAskedForAcrossAFallback() throws InterruptedException {
        // `funnel` fails on the first packet, and the failure is left to land before
        // the next send. That send hands avdec_h264 the packet kept, which fills a
        // one-packet queue at once; the packet was asked for, so it is taken all the
        // same. Under load the same happens with the real queue and a late failure.
        var frames = new ArrayList<VideoFrame>();
        try (var demuxer = Fixtures.demux("clip-h264-high.mp4")) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = new GstVideoDecoder(
                    gs,
                    GstCodec.H264,
                    demuxer.request(track.index()),
                    List.of(new Gst.Candidate("funnel", 512), avdec()),
                    1)) {
                try (var packet = demuxer.read()) {
                    assertTrue(decoder.send(packet), "the first packet");
                }
                Thread.sleep(FAILURE_LANDS_MILLIS);
                try (var packet = demuxer.read()) {
                    assertTrue(decoder.send(packet), "the packet after the failure");
                }
                assertEquals("avdec_h264", decoder.decoder(), "the next one decodes");
                Fixtures.run(demuxer, decoder, frame -> frames.add(assertInstanceOf(VideoFrame.class, frame)));
            }
        }
        assertEquals(Fixtures.frameHashes("clip-h264-high.mp4").size(), frames.size(), "every picture");
    }

    /// Long enough for `funnel`'s streaming thread to post its error.
    private static final long FAILURE_LANDS_MILLIS = 500;

    @Test
    @DisplayName("with no decoder that works, the open fails and names the codec")
    void noDecoder() {
        var e = assertThrows(GstException.class, () -> decode(List.of(new Gst.Candidate("goldberry-nope", 1))));
        assertTrue(e.getMessage().contains("h264"), e.getMessage());
    }

    private Gst.Candidate avdec() {
        return new Gst.Candidate("avdec_h264", 256);
    }

    private List<VideoFrame> decode(List<Gst.Candidate> candidates) {
        var frames = new ArrayList<VideoFrame>();
        Fixtures.decode(
                "clip-h264-high.mp4",
                MediaType.VIDEO,
                request -> new GstVideoDecoder(gs, GstCodec.H264, request, candidates),
                frame -> frames.add((VideoFrame) frame));
        return frames;
    }
}
