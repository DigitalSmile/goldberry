package io.github.digitalsmile.goldberry.widgets.shell.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.web.WebCallback;

/// A page that can be called back into — [ADR-0448] — and one the application
/// can point somewhere else — [ADR-0449].
///
/// What is checked here is the **value**: that handlers are carried, ordered and
/// immutable, that they survive the other `with`-style methods, and that
/// [WebPage#showsSameAs] answers the question `web-view` actually asks. The
/// round trip through the engine is not testable — it needs the library, WebKit
/// and a display, which is the same line `WebviewLibraryTest` draws — and is
/// checked by running the showcase's web tab, where the demo document calls back
/// on load.
@DisplayName("a page's callbacks")
class WebPageCallbackTest {

    private static final WebCallback NOTHING = arguments -> "";

    @Nested
    @DisplayName("are carried on the value")
    class Carried {

        @Test
        @DisplayName("and reach the spec the backend is handed")
        void reachTheSpec() {
            var page = WebPage.of("https://example.org").on("save", NOTHING);

            assertEquals(List.of("save"), List.copyOf(page.spec().callbacks().keySet()));
        }

        /// A page is a value, so adding a handler makes a new one. The original
        /// is what somebody else may still be holding.
        @Test
        @DisplayName("without changing the page they were added to")
        void doNotMutate() {
            var bare = WebPage.of("https://example.org");

            var bound = bare.on("save", NOTHING);

            assertTrue(bare.callbacks().isEmpty(), "on() mutated the page it was called on");
            assertFalse(bound.callbacks().isEmpty());
        }

        /// Ordered, because the order names are bound in is the order a
        /// duplicate is reported in — and because "whichever the map felt like"
        /// is not an answer to "which handler won".
        @Test
        @DisplayName("in the order they were declared")
        void keepTheirOrder() {
            var page = WebPage.of("https://example.org")
                    .on("first", NOTHING)
                    .on("second", NOTHING)
                    .on("third", NOTHING);

            assertEquals(
                    List.of("first", "second", "third"),
                    List.copyOf(page.callbacks().keySet()));
        }

        @Test
        @DisplayName("and cannot be added to from outside")
        void areUnmodifiable() {
            var page = WebPage.of("https://example.org").on("save", NOTHING);

            assertThrows(
                    UnsupportedOperationException.class, () -> page.callbacks().put("other", NOTHING));
        }

        /// The `with`-style methods rebuild the record, and every one of them
        /// has to carry the handlers over. Forgetting one is a page that loses
        /// its callbacks because somebody set a title.
        @Test
        @DisplayName("surviving every other thing that can be set on a page")
        void surviveTheOtherSetters() {
            var page = WebPage.of("https://example.org")
                    .on("save", NOTHING)
                    .title("Notes")
                    .sized(800, 600)
                    .debug(true);

            assertEquals(List.of("save"), List.copyOf(page.callbacks().keySet()));
            assertEquals(List.of("save"), List.copyOf(page.spec().callbacks().keySet()));
        }
    }

    @Nested
    @DisplayName("are why a page is compared by what it shows")
    class Showing {

        /// The trap this exists for. A handler is a lambda, lambdas have no
        /// value equality, and `equals` on a record compares every component —
        /// so two rebuilds of the same `on(...)` expression are unequal pages,
        /// and a widget navigating on `equals` would reload for ever.
        @Test
        @DisplayName("because two pages with the same handler expression are not equal")
        void handlersDefeatEquals() {
            var first = WebPage.of("https://example.org").on("save", arguments -> "");
            var second = WebPage.of("https://example.org").on("save", arguments -> "");

            assertFalse(first.equals(second), "if this ever passes, showsSameAs may no longer be needed");
        }

        @Test
        @DisplayName("and showsSameAs sees past them")
        void showsSameAsIgnoresHandlers() {
            var first = WebPage.of("https://example.org").on("save", arguments -> "");
            var second = WebPage.of("https://example.org").on("save", arguments -> "");

            assertTrue(first.showsSameAs(second));
        }

        /// A title, a size and the inspector are things about a page that do not
        /// change what is on it — navigating for any of them would reload the
        /// document to put a different word in a titlebar.
        @Test
        @DisplayName("past a title, a size and the inspector too")
        void showsSameAsIgnoresTheRest() {
            var page = WebPage.of("https://example.org");

            assertTrue(page.showsSameAs(page.title("Elsewhere").sized(320, 240).debug(true)));
        }

        @Test
        @DisplayName("but not past the url or the document")
        void showsSameAsSeesContent() {
            var page = WebPage.of("https://example.org");

            assertFalse(page.showsSameAs(WebPage.of("https://example.com")));
            assertFalse(page.showsSameAs(WebPage.ofHtml("<p>hello</p>")));
            assertFalse(page.showsSameAs(WebPage.blank()));
            assertFalse(page.showsSameAs(null), "nothing is the same as no page at all");
        }

        @Test
        @DisplayName("and two documents differ when their text does")
        void documentsCompareByText() {
            assertTrue(WebPage.ofHtml("<p>a</p>").showsSameAs(WebPage.ofHtml("<p>a</p>")));
            assertFalse(WebPage.ofHtml("<p>a</p>").showsSameAs(WebPage.ofHtml("<p>b</p>")));
        }
    }
}
