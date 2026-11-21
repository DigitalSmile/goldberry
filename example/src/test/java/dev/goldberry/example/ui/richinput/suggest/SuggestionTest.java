package dev.goldberry.example.ui.richinput.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.mark.MarkKind;

/// What a suggestion matches, what each trigger's list holds, and the kind
/// each puts in.
class SuggestionTest {

    @Test
    @DisplayName("a query matches the start of any key, ignoring case")
    void matches() {
        var ann = Contacts.ALL.getFirst().suggestion();
        assertTrue(ann.matches(""));
        assertTrue(ann.matches("AN"));
        assertTrue(ann.matches("le"));
        assertFalse(ann.matches("ee"), "not the middle of a word");
    }

    @Test
    @DisplayName("a contact suggests its name, its handle, and a mention atom")
    void contact() {
        var ann = Contacts.ALL.getFirst().suggestion();
        assertEquals("Ann Lee", ann.title());
        assertEquals("@ann", ann.detail());
        assertEquals("@Ann Lee", ann.text());
        assertEquals(new MarkKind.Mention("user:ann"), ann.kind());
    }

    @Test
    @DisplayName("each trigger puts its choice in under its own kind")
    void kinds() {
        assertEquals(List.of("@", "#", ":", "/"), List.copyOf(Directories.ALL.keySet()));
        var topic = Directories.ALL.get("#").suggest("rel").getFirst();
        assertEquals("#release", topic.text());
        assertEquals(Directories.topicKind("release"), topic.kind());
        assertFalse(topic.kind().isAtomic(), "a topic is edited like a link");
        var emoji = Directories.ALL.get(":").suggest("ro").getFirst();
        assertEquals("🚀", emoji.text());
        assertNull(emoji.kind(), "an emoji is plain text");
        var command = Directories.ALL.get("/").suggest("po").getFirst();
        assertEquals("/poll", command.text());
        assertTrue(command.kind().isAtomic(), "a command goes in whole");
    }

    @Test
    @DisplayName("a suggester lists at most five, in order")
    void capped() {
        assertEquals(
                Suggester.SHOWN, Suggester.of(Directories.PEOPLE).suggest("").size());
        assertEquals(
                List.of("Ann Lee", "Ana Silva"),
                Suggester.of(Directories.PEOPLE).suggest("a").stream()
                        .map(Suggestion::title)
                        .toList());
    }

    @Test
    @DisplayName("a suggestion needs an id, a title, text and a key; a picker a trigger")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> new Suggestion(" ", "t", "", "x", null, List.of("k")));
        assertThrows(IllegalArgumentException.class, () -> new Suggestion("i", "t", "", "x", null, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SuggestionPicker(Map.of(), () -> {
                    throw new AssertionError("never opened");
                }));
    }
}
