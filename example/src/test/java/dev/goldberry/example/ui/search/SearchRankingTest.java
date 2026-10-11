package dev.goldberry.example.ui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.search.FuzzyMatcher.Span;
import dev.goldberry.example.ui.search.SearchRanking.Ranked;

@DisplayName("ranking the palette's results")
class SearchRankingTest {

    private static SearchEntry card(String screen, String id, String title, String summary) {
        return new SearchEntry.Card(new IndexedCard(screen, id, title, summary), Gallery.tab(screen));
    }

    private static final List<SearchEntry> ENTRIES = List.of(
            new SearchEntry.Screen(Gallery.tab("panels"), "The containers a window is made of."),
            card("panels", "tabs-card", "Tabs that come and go", "A strip of headers over one panel."),
            card("choices", "radio-card", "A group is one Tab stop", "A radio group holds one option."),
            card("collections", "table-card", "The Company, in columns", "A table is a list with columns."),
            card("buttons", "button-card", "Buttons, every variant", "A button runs one action."),
            new SearchEntry.Command("Switch the light", "Ctrl+T", () -> {}));

    private static List<String> titles(List<Ranked> ranked) {
        return ranked.stream().map(each -> each.entry().title()).toList();
    }

    @Test
    @DisplayName("puts a title that starts with the query above one that merely contains it")
    void titleFirst() {
        var ranked = SearchRanking.rank("tab", ENTRIES, null, 10);

        assertEquals("Tabs that come and go", ranked.getFirst().entry().title());
        assertTrue(titles(ranked).contains("The Company, in columns"), "a summary match is still a match");
        assertTrue(
                titles(ranked).indexOf("A group is one Tab stop")
                        < titles(ranked).indexOf("The Company, in columns"),
                "a word in the title outranks the same word in the summary");
    }

    @Test
    @DisplayName("needs every word of the query to match somewhere")
    void everyWord() {
        assertEquals(List.of("Tabs that come and go"), titles(SearchRanking.rank("tabs strip", ENTRIES, null, 10)));
        assertEquals(List.of(), SearchRanking.rank("tabs zebra", ENTRIES, null, 10));
    }

    @Test
    @DisplayName("finds a card by the screen it is on")
    void byScreen() {
        assertTrue(titles(SearchRanking.rank("collections table", ENTRIES, null, 10))
                .contains("The Company, in columns"));
    }

    @Test
    @DisplayName("keeps to one kind when asked")
    void kind() {
        assertEquals(
                List.of("Switch the light"),
                titles(SearchRanking.rank("light", ENTRIES, SearchEntry.Kind.COMMAND, 10)));
        assertEquals(List.of("Panels"), titles(SearchRanking.rank("panel", ENTRIES, SearchEntry.Kind.SCREEN, 10)));
    }

    @Test
    @DisplayName("lists a screen above a card that matched as well")
    void screensFirst() {
        var ranked = SearchRanking.rank("panel", ENTRIES, null, 10);

        assertEquals("Panels", ranked.getFirst().entry().title());
    }

    @Test
    @DisplayName("stops at the limit, and lists nothing for a blank query")
    void limits() {
        assertEquals(1, SearchRanking.rank("a", ENTRIES, null, 1).size());
        assertEquals(List.of(), SearchRanking.rank("   ", ENTRIES, null, 10));
    }

    @Test
    @DisplayName("marks each matched run of the title once")
    void spans() {
        var ranked = SearchRanking.rank("tab tabs", ENTRIES, null, 1).getFirst();

        assertEquals(List.of(new Span(0, 4)), ranked.title());
    }

    @Test
    @DisplayName("joins spans that overlap or touch")
    void merge() {
        assertEquals(
                List.of(new Span(0, 6), new Span(8, 9)),
                SearchRanking.merge(List.of(new Span(3, 6), new Span(0, 4), new Span(8, 9))));
    }
}
