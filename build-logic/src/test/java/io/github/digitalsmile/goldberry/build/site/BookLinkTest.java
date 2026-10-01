package io.github.digitalsmile.goldberry.build.site;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BookLink")
class BookLinkTest {

    @Test
    @DisplayName("maps each of the three shapes to the chapter mdBook builds it from")
    void mapsToTheChapter() {
        assertAll(
                () -> assertEquals(Optional.of(new BookLink.Front()), BookLink.parse("docs/")),
                () -> assertEquals(Optional.empty(), BookLink.parse("docs/").orElseThrow().source()),
                () -> assertEquals(Optional.of("adr/index.md"), BookLink.parse("docs/adr/").orElseThrow().source()),
                () -> assertEquals(Optional.of("status.md"), BookLink.parse("docs/status.html").orElseThrow().source()),
                () -> assertEquals(
                        Optional.of("adr/0460-media.md"),
                        BookLink.parse("docs/adr/0460-media.html").orElseThrow().source()));
    }

    @Test
    @DisplayName("drops a fragment, which names a heading and not a file")
    void dropsTheFragment() {
        assertEquals(Optional.of("native.md"), BookLink.parse("docs/native.html#linux").orElseThrow().source());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"https://github.com/DigitalSmile/goldberry", "assets/og-card.jpg", "docs/native", "#faq"})
    @DisplayName("is not a book link when it does not point at a page of the book")
    void notABookLink(String href) {
        assertTrue(BookLink.parse(href).isEmpty(), href);
    }
}
