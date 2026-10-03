package dev.goldberry.example.ui.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.controls.ChapterSession;
import dev.goldberry.widgets.form.textinput.TextInput;

/// The Text screen: every card on it, a label that follows what is typed, and a
/// line copied out of a read-only field and pasted into another.
@DisplayName("the Text screen")
class TextChapterTest {

    private ChapterSession chapter;

    @BeforeEach
    void open() {
        chapter = ChapterSession.open("text");
    }

    @AfterEach
    void close() {
        if (chapter != null) {
            chapter.close();
        }
    }

    @Test
    @DisplayName("has a card per section of both chapters it shows")
    void cards() {
        assertEquals(
                Set.of(
                        "text-card",
                        "text-wrapping",
                        "text-bound",
                        "links-card",
                        "text-faces",
                        "text-bundled-faces",
                        "text-shipping-a-face",
                        "prose-card",
                        "text-scale",
                        "text-selection"),
                chapter.cardIds("text"));
    }

    @Test
    @DisplayName("shows what is typed in the field in the text bound beside it")
    void bound() {
        assertEquals(BoundTextCard.NAME, chapter.text("text-bound-echo"));

        // The field is wider than the name, so a click at its centre puts the
        // caret after the last letter.
        chapter.session().click("text-bound-field");
        chapter.session().type(" of the Old Forest");

        assertEquals("Tom Bombadil of the Old Forest", chapter.text("text-bound-echo"));
    }

    @Test
    @DisplayName("refuses typing in the read-only field, and undoes typing in the editable one")
    void editing() {
        chapter.session().click("text-copy-from");
        chapter.session().type("Gollum");
        assertEquals(SelectionCard.QUOTE, field("text-copy-from").resolved(), "a read-only field keeps its text");
        assertTrue(field("text-copy-from").readOnly());

        chapter.session().click("text-paste-into");
        chapter.session().type("Bree");
        assertEquals("Bree", field("text-paste-into").resolved());

        chapter.session().key("Ctrl+Z");
        assertEquals("", field("text-paste-into").resolved(), "a typing run is one step to undo");
    }

    /// The field with this id. The id is on the field's record and on the node it
    /// builds, so it is found by type under the card.
    private TextInput field(String id) {
        return (TextInput) chapter.find(
                        chapter.element("text-selection"),
                        TextInput.class,
                        input -> id.equals(input.attributes().id()))
                .orElseThrow()
                .widget();
    }
}
