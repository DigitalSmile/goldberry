package dev.goldberry.example.ui.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the gallery's sections")
class GallerySectionsTest {

    @Test
    @DisplayName("hold every screen once, and none is empty")
    void everyScreenIsInASection() {
        var listed = new ArrayList<GalleryTab>();
        for (var section : Section.values()) {
            var tabs = Gallery.in(section);
            assertFalse(tabs.isEmpty(), () -> section + " lists no screen");
            listed.addAll(tabs);
        }
        assertEquals(Gallery.TABS, listed, "the gallery lists its screens section by section, in the sidebar's order");
    }

    @Test
    @DisplayName("are the seven agreed, in the sidebar's order")
    void theSections() {
        assertEquals(
                List.of("Basics", "Controls", "Structure", "Graphics & media", "Content", "Platform", "Demos"),
                Arrays.stream(Section.values()).map(Section::title).toList());
    }

    @Test
    @DisplayName("step through the screens in order, wrapping at either end")
    void step() {
        var first = Gallery.TABS.getFirst().name();
        var last = Gallery.TABS.getLast().name();

        assertEquals(Gallery.TABS.get(1).name(), Gallery.step(first, 1));
        assertEquals(first, Gallery.step(last, 1));
        assertEquals(last, Gallery.step(first, -1));
        assertEquals(first, Gallery.step("home", 1), "from Home, Next is the first screen");
        assertEquals(last, Gallery.step("home", -1), "and Previous the last");
    }

    @Test
    @DisplayName("know which names are screens")
    void has() {
        assertTrue(Gallery.has("buttons"));
        assertFalse(Gallery.has("home"));
    }

    @Test
    @DisplayName("refuse a screen without a name, a title or an icon")
    void aTabNeedsItsParts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GalleryTab("x", "X", Section.BASICS, "", GalleryTab.Fit.SCROLLED, context -> null));
    }
}
