package dev.goldberry.build.book;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GuideLink")
class GuideLinkTest {

    @Test
    @DisplayName("maps a page to the chapter mdBook builds it from")
    void mapsToTheChapter() {
        assertAll(
                () -> assertEquals(
                        Optional.of("components/text.md"),
                        GuideLink.parse("https://goldberry.dev/docs/components/text.html").orElseThrow().page()),
                () -> assertEquals(
                        Optional.of("components/index.md"),
                        GuideLink.parse("https://goldberry.dev/docs/components/").orElseThrow().page()),
                () -> assertEquals(
                        Optional.empty(),
                        GuideLink.parse("https://goldberry.dev/docs/").orElseThrow().page()));
    }

    @Test
    @DisplayName("keeps the fragment, which names a heading the guard checks")
    void keepsTheFragment() {
        var link = GuideLink.parse("https://goldberry.dev/docs/components/text.html#link").orElseThrow();
        assertAll(
                () -> assertEquals(Optional.of("components/text.md"), link.page()),
                () -> assertEquals(Optional.of("link"), link.fragment()));
    }

    @Test
    @DisplayName("finds every guide link in a comment, stopping where the link stops")
    void findsLinksInText() {
        var text = """
                /// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#link).
                /** <a href="https://goldberry.dev/docs/guide/input.html">Input</a> */
                // https://goldberry.dev/docs/layout/index.html and nothing else
                """;
        var found = GuideLink.IN_TEXT.matcher(text).results().map(match -> match.group()).toList();
        assertEquals(
                List.of(
                        "https://goldberry.dev/docs/components/text.html#link",
                        "https://goldberry.dev/docs/guide/input.html",
                        "https://goldberry.dev/docs/layout/index.html"),
                found);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "https://goldberry.dev/",
                "https://goldberry.dev/docs/" + "components/text.md",
                "https://goldberry.dev/docs/" + "components/text",
                "https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0001-record-architecture-decisions.md"
            })
    @DisplayName("is not a guide link when it does not name a page mdBook writes")
    void notAGuideLink(String href) {
        assertTrue(GuideLink.parse(href).isEmpty(), href);
    }
}
