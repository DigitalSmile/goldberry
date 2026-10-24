package dev.goldberry.media.codec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;

@DisplayName("PictureLimit")
class PictureLimitTest {

    @Test
    @DisplayName("8K UHD, portrait 8K, the widest 8K panorama and an unstated size are within it")
    void within() {
        assertTrue(PictureLimit.allows(7680, 4320));
        assertTrue(PictureLimit.allows(4320, 7680));
        assertTrue(PictureLimit.allows(8192, 4352));
        assertTrue(PictureLimit.allows(8192, 16));
        assertTrue(PictureLimit.allows(0, 0));
    }

    @Test
    @DisplayName("a side past 8192, more pixels than 8192 × 4352, or a negative size is past it")
    void past() {
        assertFalse(PictureLimit.allows(8193, 16), "a side");
        assertFalse(PictureLimit.allows(16, 8193), "the other side");
        assertFalse(PictureLimit.allows(6000, 6000), "36 megapixels, each side within");
        assertFalse(PictureLimit.allows(8192, 4353), "one row over");
        assertFalse(PictureLimit.allows(65_536, 65_536));
        assertFalse(PictureLimit.allows(-1, 16));
        assertEquals(35_651_584L, PictureLimit.MAX_PIXELS);
    }

    @Test
    @DisplayName("require refuses with InvalidData naming the size, and lets a picture within it pass")
    void require() {
        assertDoesNotThrow(() -> PictureLimit.require(7680, 4320, "a picture"));
        var refused = assertThrows(MediaException.class, () -> PictureLimit.require(6000, 6000, "track 0"));
        var error = assertInstanceOf(MediaError.InvalidData.class, refused.error());
        assertTrue(error.message().contains("track 0 of 6000×6000"), error.message());
    }
}
