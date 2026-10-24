package dev.goldberry.media.container.mp4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The two edits that are a shift, and the ones that are left alone.
@DisplayName("EditList")
class EditListTest {

    @Test
    @DisplayName("no edits, or one from the start, shift nothing")
    void none() {
        assertEquals(0, EditList.NONE.shift(48_000, 1000));
        assertEquals(0, new EditList(List.of(new EditList.Entry(1000, 0))).shift(48_000, 1000));
        assertFalse(EditList.NONE.hasIgnoredEdits());
    }

    @Test
    @DisplayName("a first edit into the media shifts the track earlier by its media_time: Opus's pre-skip")
    void mediaTime() {
        var edits = new EditList(List.of(new EditList.Entry(1000, 312)));
        assertEquals(-312, edits.shift(48_000, 1000));
        assertFalse(edits.hasIgnoredEdits());
    }

    @Test
    @DisplayName("a leading empty edit delays the track by its duration, rescaled to the track's timescale")
    void emptyEdit() {
        var edits = new EditList(List.of(new EditList.Entry(500, -1), new EditList.Entry(1000, 0)));
        assertEquals(24_000, edits.shift(48_000, 1000));
        assertEquals(6400, edits.shift(12_800, 1000));
        assertFalse(edits.hasIgnoredEdits());
    }

    @Test
    @DisplayName("an empty edit and a media_time add up; the rescale rounds to the nearest tick")
    void both() {
        var edits = new EditList(List.of(new EditList.Entry(333, -1), new EditList.Entry(1000, 100)));
        assertEquals(Math.round(333 * 12_800 / 1000d) - 100, edits.shift(12_800, 1000));
    }

    @Test
    @DisplayName("a second real edit is ignored, and said to be")
    void later() {
        var edits = new EditList(List.of(new EditList.Entry(500, 0), new EditList.Entry(500, 24_000)));
        assertEquals(0, edits.shift(48_000, 1000));
        assertTrue(edits.hasIgnoredEdits());
    }
}
