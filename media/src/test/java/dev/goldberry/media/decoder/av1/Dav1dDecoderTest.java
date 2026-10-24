package dev.goldberry.media.decoder.av1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.decoder.FrameHashes;
import dev.goldberry.media.ffm.MediaNatives;

/// dav1d through the SPI, against FFmpeg's checksums of the same pictures: the
/// spike that showed an AV1 clip demuxed in Java decodes to the bytes FFmpeg's
/// dav1d produced.
@DisplayName("Dav1dDecoder")
class Dav1dDecoderTest {

    private static final long FRAME_NANOS = 40_000_000L;

    private final Dav1dProvider provider = new Dav1dProvider();

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    @Test
    @DisplayName("decodes clip-av1.mkv to the 25 pictures FFmpeg's dav1d decoded, byte for byte")
    void matchesFfmpeg() {
        assertEquals(25, FrameHashes.assertMatchesFfmpeg("clip-av1.mkv", provider));
    }

    @Test
    @DisplayName("every picture is 160×90 I420, limited-range BT.601, 40 ms after the one before")
    void describesPictures() {
        var pts = new ArrayList<Long>();
        FrameHashes.decodeVideo("clip-av1.mkv", provider, frame -> {
            assertEquals(PixelFormat.I420, frame.format());
            assertEquals(160, frame.width());
            assertEquals(90, frame.height());
            assertEquals(VideoFrame.ColorMatrix.BT601, frame.matrix());
            assertFalse(frame.fullRange());
            assertTrue(frame.strides().get(0) >= 160);
            pts.add(frame.ptsNanos());
        });
        for (var i = 0; i < pts.size(); i++) {
            assertEquals(i * FRAME_NANOS, pts.get(i), "picture " + i);
        }
    }

    @Test
    @DisplayName("a flush starts over: the first picture again after a seek to the start")
    void flushes() {
        var name = "clip-av1.mkv";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            container.select(Set.of(track.index()));
            try (var decoder = provider.open(container.request(track.index()))) {
                var first = firstPicture(container, decoder);
                container.seek(0);
                decoder.flush();
                assertEquals(first, firstPicture(container, decoder));
            }
        }
    }

    private static String firstPicture(MatroskaContainer container, dev.goldberry.media.codec.Decoder decoder) {
        while (true) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> {
                    return FrameHashes.md5(assertInstanceOf(VideoFrame.class, frame));
                }
                case Received.NeedsInput _ -> {
                    try (var packet = container.read()) {
                        assertTrue(packet != null && decoder.send(packet));
                    }
                }
                case Received.Ended _ -> throw new AssertionError("ended before a picture");
            }
        }
    }

    @Test
    @DisplayName("the provider is dav1d, for AV1 alone")
    void provider() {
        assertEquals("dav1d", provider.name());
        var name = "clip-vp8.webm";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            assertFalse(provider.supports(container.request(0)), "VP8 is not dav1d's");
        }
        assertTrue(MediaNatives.isAvailable());
    }
}
