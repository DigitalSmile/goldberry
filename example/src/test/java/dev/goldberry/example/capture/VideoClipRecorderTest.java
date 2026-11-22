package dev.goldberry.example.capture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.camera.synthetic.SyntheticCamera;
import dev.goldberry.media.picture.VideoPlanes;

@DisplayName("a picture clip's recorder")
class VideoClipRecorderTest {

    /// One NV12 picture of 64×36: a luma plane and a half-height chroma plane.
    private static final long PICTURE_BYTES = 64 * 36 + 64 * 18;

    @Test
    @DisplayName("copies each picture the camera shows, timed from the first")
    void copies() {
        try (var camera = SyntheticCamera.of(64, 36);
                var recorder = new VideoClipRecorder(camera, Duration.ofSeconds(1), 1 << 20)) {
            camera.advance(Duration.ofMillis(100));
            var clip = recorder.clip();

            assertEquals(3, clip.pictures().size());
            assertEquals(3 * PICTURE_BYTES, clip.bytes());
            var first = assertInstanceOf(
                    VideoPlanes.class, clip.pictures().getFirst().picture());
            var shown =
                    assertInstanceOf(VideoPlanes.class, camera.shownPicture().orElseThrow());
            assertAll(
                    () -> assertEquals(0, clip.pictures().getFirst().atNanos()),
                    () -> assertEquals(2e9 / 30, clip.pictures().get(2).atNanos(), 1e3, "the third, two frames on"),
                    () -> assertEquals(64, first.stride(0), "rows packed"),
                    () -> assertNotSame(shown.plane(0), first.plane(0)));

            // The camera's pool lends its slots again; the copy keeps its bytes.
            var before = first.sample(0, 10, 10);
            camera.advance(Duration.ofMillis(300));
            assertEquals(before, first.sample(0, 10, 10));
        }
    }

    @Test
    @DisplayName("stops at its budget and says it is full")
    void budget() {
        try (var camera = SyntheticCamera.of(64, 36);
                var recorder = new VideoClipRecorder(camera, Duration.ofSeconds(10), 2 * PICTURE_BYTES)) {
            camera.advance(Duration.ofMillis(34));
            assertFalse(recorder.full());
            camera.advance(Duration.ofMillis(100));

            assertTrue(recorder.full());
            assertEquals(2, recorder.pictures());
        }
    }

    @Test
    @DisplayName("stops at its longest clip, and takes nothing once closed")
    void longest() {
        try (var camera = SyntheticCamera.of(64, 36)) {
            var recorder = new VideoClipRecorder(camera, Duration.ofMillis(100), 1 << 20);
            camera.advance(Duration.ofMillis(300));
            assertTrue(recorder.full());
            assertEquals(4, recorder.pictures(), "at 0, 33, 67 and 100 ms");

            var other = new VideoClipRecorder(camera, Duration.ofSeconds(1), 1 << 20);
            other.close();
            var kept = other.pictures();
            camera.advance(Duration.ofMillis(100));
            assertEquals(kept, other.pictures());
        }
    }
}
