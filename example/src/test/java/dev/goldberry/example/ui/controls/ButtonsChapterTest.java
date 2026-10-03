package dev.goldberry.example.ui.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.key.Key;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.chip.Chip;

/// The Buttons screen, pressed: the counter and its disabled buttons, the badge
/// that counts, a filter chip, the tags a × takes off, and a pressable row.
@DisplayName("the Buttons screen")
class ButtonsChapterTest {

    private ChapterSession chapter;

    @BeforeEach
    void open() {
        chapter = ChapterSession.open("buttons");
    }

    @AfterEach
    void close() {
        if (chapter != null) {
            chapter.close();
        }
    }

    @Test
    @DisplayName("has a card per section, in its own ids")
    void cards() {
        assertEquals(
                Set.of(
                        "button-card",
                        "shapes-card",
                        "road-card",
                        "badge-card",
                        "chip-card",
                        "tag-card",
                        "buttons-pressable"),
                chapter.cardIds("buttons"));
    }

    @Test
    @DisplayName("enables Turn back once a league is walked, and the badge counts it")
    void theRoad() {
        assertTrue(chapter.widget("undo", Button.class).disabled(), "nothing to undo yet");
        assertEquals("Nobody has left Bag End yet.", chapter.text("leagues"));

        chapter.session().click("click");
        chapter.session().click("b-primary");

        assertEquals("2 leagues walked.", chapter.text("leagues"));
        assertFalse(chapter.widget("undo", Button.class).disabled(), "a league to undo");
        var badge = chapter.find(chapter.element("badges"), Badge.class, b -> b.source() != null)
                .orElseThrow();
        assertEquals("2", ((Badge) badge.widget()).resolved());

        chapter.session().click("undo");
        chapter.session().click("reset");

        assertEquals("Nobody has left Bag End yet.", chapter.text("leagues"));
        assertTrue(chapter.widget("reset", Button.class).disabled(), "nothing to begin again from");
    }

    @Test
    @DisplayName("flips a filter chip through its action")
    void filterChip() {
        assertFalse(chapter.widget("chip-starred", Chip.class).isChecked());

        chapter.session().click("chip-starred");

        assertEquals(true, chapter.value("app.chip-starred"));
        assertTrue(chapter.widget("chip-starred", Chip.class).isChecked());
    }

    @Test
    @DisplayName("takes a tag off with Delete, and puts them back")
    void tags() {
        assertTrue(chapter.session().focus("tag-java"));
        chapter.session().key(Key.DELETE);

        assertTrue(chapter.session().byId("tag-java").isEmpty(), "the java chip is gone");
        assertEquals(
                List.of("kdl", "blend2d", "harfbuzz", "yoga"), chapter.model().tags());

        chapter.session().click("reset-tags");

        assertTrue(chapter.session().byId("tag-java").isPresent(), "the java chip is back");
    }

    @Test
    @DisplayName("opens a release from a pressable row, and not from a disabled one")
    void pressable() {
        assertEquals("No release opened yet.", chapter.text("release-opened"));

        chapter.session().focus("release-sealed");
        chapter.session().key(Key.ENTER);
        assertEquals("No release opened yet.", chapter.text("release-opened"));

        chapter.session().click("release-current");
        assertEquals("Opened release 2.4.0.", chapter.text("release-opened"));
    }
}
