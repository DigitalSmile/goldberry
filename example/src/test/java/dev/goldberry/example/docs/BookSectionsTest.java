package dev.goldberry.example.docs;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the book's sections")
class BookSectionsTest {

    @Test
    @DisplayName("are every chapter the summary lists, in its order")
    void chaptersAreTheSummarys() {
        var chapters = BookSections.chapters();

        assertAll(
                () -> assertEquals("introduction", chapters.getFirst()),
                () -> assertTrue(chapters.indexOf("layout/index") < chapters.indexOf("components/index")),
                () -> assertFalse(
                        chapters.stream().anyMatch(chapter -> chapter.startsWith("adr/")),
                        "the decision log is not built into the book"));
    }

    @Test
    @DisplayName("name headings by the anchors the book's own links use")
    void anchorsAreMdBooks() {
        assertAll(
                () -> assertEquals("button", BookSections.anchorOf("`button`")),
                () -> assertEquals("the-desktops-theme", BookSections.anchorOf("The desktop's theme")),
                () -> assertEquals("custom-properties-and-var", BookSections.anchorOf("Custom properties and `var()`")),
                () -> assertTrue(BookSections.resolves(DocLink.to("layout/spacer", "spacer"))),
                () -> assertFalse(BookSections.resolves(DocLink.to("layout/spacer", "nothing-here"))),
                () -> assertFalse(BookSections.resolves(DocLink.page("adr/0001-record-architecture-decisions"))));
    }

    @Test
    @DisplayName("ask for a card per widget, per feature, and a link per other chapter")
    void requiredFollowsTheRules() {
        var required = Set.copyOf(BookSections.required());

        assertAll(
                () -> assertTrue(required.contains(DocLink.to("components/buttons", "button")), "a widget heading"),
                () -> assertTrue(required.contains(DocLink.to("guide/windows", "the-badge")), "a feature subheading"),
                () -> assertTrue(required.contains(DocLink.page("weaving")), "a chapter with nothing to click"),
                () -> assertFalse(
                        required.contains(DocLink.to("components/choices", "option")),
                        "a child node is shown on its parent's card"),
                () -> assertFalse(
                        required.stream().anyMatch(link -> link.anchor().equals("read-more"))));
    }

    @Test
    @DisplayName("count a chapter that needs a link as shown by any link into it")
    void aPageLinkCoversItsChapter() {
        var links = Set.of(DocLink.to("weaving", "the-two-halves"));

        assertAll(
                () -> assertTrue(BookSections.covers(links, DocLink.page("weaving"))),
                () -> assertFalse(BookSections.covers(links, DocLink.to("weaving", "known-limits"))));
    }
}
