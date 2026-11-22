package dev.goldberry.camera.view;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.camera.synthetic.SyntheticCamera;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.PushedPictures;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;

@DisplayName("mirrored pictures")
class MirroredPicturesTest {

    @Test
    @DisplayName("reverse luma a byte at a time and chroma a pair at a time")
    void reverse() {
        var luma = new byte[] {1, 2, 3, 4, 5};
        MirroredPictures.reverse(luma, 5, 1);
        var chroma = new byte[] {1, 2, 3, 4, 5, 6};
        MirroredPictures.reverse(chroma, 6, 2);
        assertAll(
                () -> assertArrayEquals(new byte[] {5, 4, 3, 2, 1}, luma),
                () -> assertArrayEquals(new byte[] {5, 6, 3, 4, 1, 2}, chroma));
    }

    @Test
    @DisplayName("show the camera's picture left for right, once a picture, leaving the camera's alone")
    void mirrors() {
        var camera = SyntheticCamera.of(70, 60);
        var mirrored = new MirroredPictures(camera);
        assertTrue(mirrored.shownPicture().isEmpty());
        camera.advance(Duration.ofMillis(34));
        var original = (VideoPlanes) camera.shownPicture().orElseThrow();
        var flipped = (VideoPlanes) mirrored.shownPicture().orElseThrow();
        assertAll(
                () -> assertSame(flipped, mirrored.shownPicture().orElseThrow()),
                () -> assertEquals(original.sample(0, 0, 2), flipped.sample(0, 69, 2)),
                () -> assertEquals(original.sample(0, 66, 2), flipped.sample(0, 3, 2)),
                () -> assertEquals(original.sample(1, 0, 3), flipped.sample(1, 68, 3), "Cb stays Cb"),
                () -> assertEquals(original.sample(1, 1, 3), flipped.sample(1, 69, 3), "and Cr stays Cr"),
                () -> assertEquals(235, original.sample(0, 2, 2), "the camera's own picture is untouched"));
    }

    @Test
    @DisplayName("show a BGRA picture left for right a pixel at a time, keeping its alpha")
    void mirrorsBgra() {
        var pixels = ByteBuffer.allocateDirect(2 * 3 * 4);
        for (var index = 0; index < pixels.capacity(); index++) {
            pixels.put(index, (byte) index);
        }
        var pushed = new PushedPictures();
        pushed.push(new VideoPicture(3, 2, 12, pixels, 7, false));
        var flipped = (VideoPicture) new MirroredPictures(pushed).shownPicture().orElseThrow();
        var row = new byte[12];
        flipped.pixels().get(12, row);
        assertAll(
                () -> assertArrayEquals(new byte[] {20, 21, 22, 23, 16, 17, 18, 19, 12, 13, 14, 15}, row),
                () -> assertEquals(7, flipped.ptsNanos()),
                () -> assertFalse(flipped.opaque()),
                () -> assertEquals(12, pixels.get(12), "the source's picture is untouched"));
    }

    @Test
    @DisplayName("show a picture in another layout as it is")
    void others() {
        var pushed = new PushedPictures();
        var i420 = new VideoPlanes(
                PixelFormat.I420,
                2,
                2,
                List.of(ByteBuffer.allocateDirect(4), ByteBuffer.allocateDirect(1), ByteBuffer.allocateDirect(1)),
                List.of(2, 1, 1),
                VideoFrame.ColorMatrix.BT709,
                false,
                0);
        pushed.push(i420);
        assertSame(i420, new MirroredPictures(pushed).shownPicture().orElseThrow());
    }
}
