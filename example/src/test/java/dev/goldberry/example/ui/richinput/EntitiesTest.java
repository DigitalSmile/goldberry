package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.richinput.Entities.Entity;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.text.mark.MarkedText;

/// Marks as a messenger's entities.
class EntitiesTest {

    @Test
    @DisplayName("a mark is an entity at the same offset and length, in a messenger's words")
    void entities() {
        var text = MarkedWriter.start()
                .with("Bold", Style.BOLD)
                .plain(" ")
                .with("gone", Style.STRIKE)
                .plain(" ")
                .with("site", new MarkKind.Link(Samples.SITE))
                .plain(" ")
                .with("@Ann", new MarkKind.Mention("user:ann"))
                .text();
        assertEquals(
                List.of(
                        new Entity(0, 4, "bold", ""),
                        new Entity(5, 4, "strikethrough", ""),
                        new Entity(10, 4, "text_link", "https://goldberry.dev"),
                        new Entity(15, 4, "mention_name", "user:ann")),
                Entities.of(text));
    }

    @Test
    @DisplayName("offsets count UTF-16 code units, so an emoji is two")
    void utf16() {
        var text = MarkedWriter.start().plain("🚀 ").with("go", Style.BOLD).text();
        assertEquals(List.of(new Entity(3, 2, "bold", "")), Entities.of(text));
    }

    @Test
    @DisplayName("a custom kind is its own name and value; plain text has none")
    void custom() {
        assertEquals(
                "0+9 highlight",
                Entities.describe(MarkedWriter.start()
                        .with("important", Samples.HIGHLIGHT)
                        .text()));
        assertEquals("(no entities)", Entities.describe(MarkedText.of("plain")));
    }
}
