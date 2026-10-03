package dev.goldberry.example.docs;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("a link into the guide")
class DocLinkTest {

    @Test
    @DisplayName("opens the page mdBook builds, at the heading's anchor")
    void urlIsTheBuiltPage() {
        assertAll(
                () -> assertEquals(
                        "https://goldberry.dev/docs/components/buttons.html#button",
                        DocLink.to("components/buttons", "button").url()),
                () -> assertEquals(
                        "https://goldberry.dev/docs/weaving.html",
                        DocLink.page("weaving").url()),
                () -> assertEquals(
                        "components/buttons.md",
                        DocLink.page("components/buttons").source()));
    }

    @Test
    @DisplayName("reads back from the address a card carries")
    void parsesItsOwnUrl() {
        var link = DocLink.to("guide/windows", "the-desktops-theme");

        assertAll(
                () -> assertEquals(Optional.of(link), DocLink.parse(link.url())),
                () -> assertEquals(
                        Optional.of(DocLink.page("TODO")),
                        DocLink.parse(DocLink.page("TODO").url())),
                () -> assertEquals(Optional.empty(), DocLink.parse("https://example.org/docs/a.html")),
                () -> assertEquals(Optional.empty(), DocLink.parse(DocLink.SITE + "components/buttons.md")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"components/buttons.md", "/components/buttons", "components/buttons#button", ""})
    @DisplayName("refuses a chapter written any way but the book's")
    void refusesABadPage(String page) {
        assertThrows(IllegalArgumentException.class, () -> DocLink.page(page));
    }

    @Test
    @DisplayName("refuses an anchor mdBook would not write")
    void refusesABadAnchor() {
        var refused = assertThrows(IllegalArgumentException.class, () -> DocLink.to("components/buttons", "Button"));
        assertTrue(refused.getMessage().contains("Button"), refused.getMessage());
    }
}
