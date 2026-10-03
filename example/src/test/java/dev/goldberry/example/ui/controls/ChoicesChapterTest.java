package dev.goldberry.example.ui.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.key.Key;
import dev.goldberry.widgets.controls.checkbox.Checkbox;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.radio.Radio;
import dev.goldberry.widgets.controls.toggle.Toggle;

/// The Choices screen, driven: a mixed box asking for all, a checkbox and a
/// switch on one value, a group that roves, and three controls on the theme.
@DisplayName("the Choices screen")
class ChoicesChapterTest {

    private ChapterSession chapter;

    @BeforeEach
    void open() {
        chapter = ChapterSession.open("choices");
    }

    @AfterEach
    void close() {
        if (chapter != null) {
            chapter.close();
        }
    }

    @Test
    @DisplayName("has a card per section, and four selects")
    void cards() {
        assertEquals(
                Set.of(
                        "switch-card",
                        "choices-toggle",
                        "choices-radio",
                        "choices-radio-group",
                        "theme-card",
                        "choices-select",
                        "tongues-card",
                        "places-card",
                        "realms-card"),
                chapter.cardIds("choices"));
    }

    @Test
    @DisplayName("asks for all when a mixed box is clicked")
    void mixed() {
        assertEquals(Checkbox.Value.MIXED, chapter.value("app.partly"));

        chapter.session().click("partly");

        assertEquals(Checkbox.Value.CHECKED, chapter.value("app.partly"));
        assertTrue(chapter.widget("partly", Checkbox.class).isChecked());
    }

    @Test
    @DisplayName("moves the switch when the checkbox on its value is clicked")
    void oneValueTwoControls() {
        var before = chapter.widget("prose-switch", Toggle.class).isChecked();

        chapter.session().click("prose-toggle");

        assertEquals(!before, chapter.widget("prose-switch", Toggle.class).isChecked());
    }

    @Test
    @DisplayName("roves and selects with the arrows inside a group, and the caption follows")
    void radioGroup() {
        assertEquals("Wintering in Rivendell.", chapter.text("winter-caption"));
        var bree = chapter.find(
                        chapter.element("winter"),
                        Radio.class,
                        radio -> radio.value().equals("bree"))
                .orElseThrow();

        chapter.session().click(bree);
        assertEquals("Wintering in Bree.", chapter.text("winter-caption"));

        chapter.session().key(Key.DOWN);
        assertEquals("Wintering in the Shire.", chapter.text("winter-caption"));
    }

    @Test
    @DisplayName("moves the radios and the select when the bar picks a light")
    void theme() {
        assertEquals("dark", chapter.value("app.theme"));
        var light = chapter.find(
                        chapter.element("theme-bar"),
                        Option.class,
                        option -> option.value().equals("light"))
                .orElseThrow();

        chapter.session().click(light);

        assertEquals("light", chapter.value("app.theme"));
        var radio = chapter.find(
                        chapter.element("themes"), Radio.class, r -> r.value().equals("light"))
                .orElseThrow();
        assertTrue(((Radio) radio.widget()).isChecked(), "the radios follow the bar");
    }
}
