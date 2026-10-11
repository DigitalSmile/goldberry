package dev.goldberry.example.ui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.gallery.Gallery;

@DisplayName("the search index's file")
class SearchIndexFormatTest {

    private static final SearchIndex SAMPLE = new SearchIndex(List.of(
            new IndexedCard("buttons", "", "Buttons", "Three widgets."),
            new IndexedCard("buttons", "button-card", "Buttons, every variant", "A tab\there, a line\nthere, a \\"),
            new IndexedCard("forms", "", "Forms", "Fields."),
            new IndexedCard("forms", "field-card", "Field", "A label and a control.")));

    @Test
    @DisplayName("reads back what it wrote, tabs, breaks and backslashes included")
    void roundTrip() {
        assertEquals(SAMPLE.entries(), SearchIndex.parse(SAMPLE.write()).entries());
    }

    @Test
    @DisplayName("counts the cards on each screen, leaving the headers out")
    void counts() {
        assertEquals(Map.of("buttons", 1, "forms", 1), SAMPLE.counts());
        assertEquals(2, SAMPLE.cards().size());
        assertEquals(Optional.of("Fields."), SAMPLE.summaryOf("forms"));
        assertEquals(Optional.empty(), SAMPLE.summaryOf("charts"));
    }

    @Test
    @DisplayName("refuses a line that is not four fields, naming it")
    void malformed() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> SearchIndex.parse("# header\nbuttons\tonly\n"));
        assertTrue(thrown.getMessage().contains("line 2"), thrown.getMessage());
    }

    @Test
    @DisplayName("the shipped list has a card on most screens, and only screens the gallery has")
    void shipped() {
        var shipped = SearchIndex.shipped();

        assertFalse(shipped.cards().isEmpty(), "the jar carries no search index");
        assertTrue(shipped.entries().stream().allMatch(entry -> Gallery.has(entry.screen())));
        assertTrue(shipped.counts().size() > Gallery.TABS.size() / 2);
    }

    @Test
    @DisplayName("the catalog lists every screen, then the cards, then the commands")
    void catalog() {
        var command = new SearchEntry.Command("Quit", "Ctrl+Q", () -> {});
        var entries = SearchCatalog.entries(SAMPLE, List.of(command));

        assertEquals(Gallery.TABS.size() + 2 + 1, entries.size());
        assertEquals(SearchEntry.Kind.SCREEN, entries.getFirst().kind());
        assertEquals(command, entries.getLast());
        var buttons = entries.stream()
                .filter(entry -> entry instanceof SearchEntry.Screen screen
                        && screen.tab().name().equals("buttons"))
                .findFirst()
                .orElseThrow();
        assertEquals("Three widgets.", buttons.detail(), "a screen carries the summary its header opens with");
    }
}
