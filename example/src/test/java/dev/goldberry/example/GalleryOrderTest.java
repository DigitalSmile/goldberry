package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.Screen;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.GalleryTab;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Shortcut;

/// That the gallery has **one** order in it.
///
/// The strip and the accelerators are two ways to set one property,
/// and they each used to hold their own list of screens. The lists disagreed:
/// `Charts` was inserted after `Choosers` in the strip and *instead* of it in
/// the accelerators, so `Ctrl+8` selected the ninth tab, and the tenth screen
/// had no key at all — the loop asked a nine-element list for its tenth digit
/// and the window died on the way up.
///
/// Neither is a thing a golden image can show: both keys select *a* screen and
/// the picture of it is correct. So it is asserted here.
class GalleryOrderTest {

    /// What [Showcase#screenShortcuts] bound, in the order it bound it.
    private record Bound(Shortcut accelerator, String screen) {}

    private static List<Bound> shortcuts() {
        var bound = new ArrayList<Bound>();
        var picked = new String[1];
        Showcase.screenShortcuts(
                (accelerator, action) -> {
                    // Run it to find out what it does, rather than trusting the
                    // order it arrived in: the defect being guarded against is a
                    // key bound to the wrong screen, which is invisible until
                    // somebody presses it.
                    action.run();
                    bound.add(new Bound(accelerator, picked[0]));
                },
                name -> picked[0] = name);
        return bound;
    }

    @Test
    @DisplayName("every digit selects the screen at its own position in the strip")
    void theDigitAndTheTabAgree() {
        var bound = shortcuts();

        var digits = List.of(
                Key.DIGIT_1,
                Key.DIGIT_2,
                Key.DIGIT_3,
                Key.DIGIT_4,
                Key.DIGIT_5,
                Key.DIGIT_6,
                Key.DIGIT_7,
                Key.DIGIT_8,
                Key.DIGIT_9,
                Key.DIGIT_0);
        var expected = new ArrayList<Bound>();
        for (var index = 0; index < Math.min(Screen.GALLERY.size(), digits.size()); index++) {
            expected.add(new Bound(Mod.CTRL.and(digits.get(index)), Screen.GALLERY.get(index)));
        }

        assertEquals(expected, bound, "Ctrl+<n> selects the nth screen of the strip, and Ctrl+0 the tenth");
    }

    @Test
    @DisplayName("a gallery longer than the digits binds what it can and stops")
    void theKeysRunOutRatherThanThrowing() {
        var bound = shortcuts();

        // The bug this replaces: `digits.get(index)` on a list shorter than the
        // screens, which is an IndexOutOfBoundsException during `start` — a
        // window that never opens, from adding a screen.
        assertEquals(Math.min(Screen.GALLERY.size(), 10), bound.size(), "ten digits, however many screens there are");
        assertTrue(Screen.GALLERY.size() >= bound.size(), "and no key for a screen that is not in the gallery");
    }

    @Test
    @DisplayName("the strip, the digits and the menus read one list")
    void oneList() {
        assertEquals(Gallery.names(), Screen.GALLERY, "Screen.GALLERY is the gallery's tab list, not a copy of it");
        Gallery.TABS.forEach(tab -> assertEquals(tab.title(), Screen.title(tab.name())));
    }

    @Test
    @DisplayName("a screen nobody added is a failure, not a menu row that opens nothing")
    void anUnknownScreenIsRefused() {
        var failure = assertThrows(IllegalArgumentException.class, () -> Screen.title("histograms"));
        assertTrue(failure.getMessage().contains("histograms"), failure.getMessage());
    }

    @Test
    @DisplayName("no two screens share a label")
    void theTitlesAreDistinct() {
        var titles = Gallery.TABS.stream().map(GalleryTab::title).toList();
        assertEquals(titles.size(), Set.copyOf(titles).size(), "two tabs read the same: " + titles);
    }

    @Test
    @DisplayName("no screen is named twice")
    void theGalleryHasNoDuplicates() {
        var seen = new LinkedHashMap<String, Integer>();
        for (var name : Screen.GALLERY) {
            seen.merge(name, 1, Integer::sum);
        }
        assertEquals(
                Screen.GALLERY.size(),
                seen.size(),
                "a duplicate would give two digits to one screen and none to another: " + seen);
    }
}
