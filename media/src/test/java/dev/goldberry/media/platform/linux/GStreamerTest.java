package dev.goldberry.media.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
