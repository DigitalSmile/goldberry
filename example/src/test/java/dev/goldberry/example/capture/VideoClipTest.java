package dev.goldberry.example.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.picture.VideoPicture;

@DisplayName("a recorded picture clip")
class VideoClipTest {

    private static VideoClip.Shot shot(long atMillis) {
        var pixels = ByteBuffer.allocateDirect(4);
        return new VideoClip.Shot(Duration.ofMillis(atMillis).toNanos(), new VideoPicture(1, 1, 4, pixels, atMillis));
    }

    @Test
    @DisplayName("finds the picture showing at a time: the last at or before it")
    void indexAt() {
        var clip = new VideoClip(List.of(shot(0), shot(40), shot(80)), 12);

        assertEquals(0, clip.indexAt(-5));
        assertEquals(0, clip.indexAt(Duration.ofMillis(39).toNanos()));
        assertEquals(1, clip.indexAt(Duration.ofMillis(40).toNanos()));
        assertEquals(2, clip.indexAt(Duration.ofSeconds(9).toNanos()));
    }

    @Test
    @DisplayName("lasts to its last picture and that picture's share of the clip")
    void duration() {
        assertEquals(Duration.ofMillis(120), new VideoClip(List.of(shot(0), shot(40), shot(80)), 12).duration());
        assertEquals(Duration.ZERO, new VideoClip(List.of(shot(0)), 4).duration());
        assertEquals(Duration.ZERO, VideoClip.EMPTY.duration());
    }

    @Test
    @DisplayName("refuses pictures out of order, a picture before the start, and a time in an empty clip")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> new VideoClip(List.of(shot(40), shot(0)), 8));
        assertThrows(IllegalArgumentException.class, () -> shot(-1));
        assertThrows(IllegalStateException.class, () -> VideoClip.EMPTY.indexAt(0));
    }
}
