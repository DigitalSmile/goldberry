package io.github.digitalsmile.goldberry.offscreen;

import static io.github.digitalsmile.goldberry.offscreen.Scene.BLUE;
import static io.github.digitalsmile.goldberry.offscreen.Scene.RED;
import static io.github.digitalsmile.goldberry.offscreen.Scene.panel;
import static io.github.digitalsmile.goldberry.offscreen.Scene.pixels;
import static io.github.digitalsmile.goldberry.offscreen.Scene.sheet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// Reuse between renders — ADR-0425.
///
/// ADR-0284 recorded "the builder is not a cache: rendering the same document
/// twice does the work twice" as a consequence and left it there. A studio is the
/// decision about it, and the thing to assert is not that it is faster — a
/// stopwatch would be a flaky test about that — but that the work is **not done a
/// second time**. The shaping cache counts its own misses, so that is the evidence
/// (ADR-0299).
@DisplayName("a studio")
class StudioTest {

    private static final String CSS = ".root { width: 16px; height: 16px; background: #ff0000 }";

    /// Text, because the claim being tested is about the shaping cache and a tree
    /// with no text in it shapes nothing either way.
    private record Label(Attributes attributes, String words) implements Widget.Leaf, Styled, Paints {

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.text(context.paragraph(style, words), style.color()).style(style);
        }
    }

    private static Label label(String words) {
        return new Label(Scene.classed("root"), words);
    }

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Nested
    @DisplayName("what it keeps")
    class Keeps {

        @Test
        @DisplayName("the second render of one document shapes nothing")
        void keepsTheShapingCache() {
            // The entry's own claim, turned around: "rendering the same document
            // twice does the work twice" was true of the shaping, which is the most
            // expensive step in the text path by an order of magnitude (ADR-0037).
            try (var studio = Studio.of(sheet(CSS))) {
                studio.picture(64, 32).render(label("the quick brown fox"));
                var cache = studio.renderer().paragraphs();
                assertNotNull(cache);
                var missesAfterFirst = cache.misses();
                assertTrue(missesAfterFirst > 0, "the first render had to shape it");

                studio.picture(64, 32).render(label("the quick brown fox"));

                assertEquals(missesAfterFirst, cache.misses(), "and the second render shaped nothing at all");
                assertTrue(cache.hits() > 0);
            }
        }

        @Test
        @DisplayName("one renderer, and therefore one cascade index, across every render")
        void keepsTheRenderer() {
            try (var studio = Studio.of(sheet(CSS))) {
                var renderer = studio.renderer();
                studio.picture(16, 16).render(panel("root"));
                studio.picture(16, 16).render(panel("root"));

                assertSame(renderer, studio.renderer(), "the renderer is the studio's, not the render's");
                assertSame(renderer.paragraphs(), studio.renderer().paragraphs());
            }
        }

        @Test
        @DisplayName("a book handed in is not closed with the studio")
        void doesNotCloseABorrowedBook() {
            try (var book = Fonts.bundled(List.of())) {
                try (var studio = Studio.over(sheet(CSS), book)) {
                    studio.picture(16, 16).render(panel("root"));
                }
                // Still usable: `over` borrows, `of` owns. A studio that closed a
                // caller's book would take the faces out from under whatever else
                // is drawing with them.
                assertNotNull(book.of(BundledFont.UI, 12));
            }
        }
    }

    @Nested
    @DisplayName("what it does not change")
    class Unchanged {

        @Test
        @DisplayName("a studio render is pixel for pixel a plain render")
        void matchesAPlainRender() {
            // The only interesting thing a cache can get wrong. If keeping the
            // cascade index or the shaping cache changed a single pixel, this is
            // where it would show -- and every golden in the repository would move
            // the day the harness started using one.
            var plain = Offscreen.of(64, 32).stylesheets(sheet(CSS)).render(label("the quick brown fox"));

            int[] kept;
            try (var studio = Studio.of(sheet(CSS))) {
                // Twice, so the compared render is one taken from a *warm* studio
                // rather than a cold one.
                studio.picture(64, 32).render(label("the quick brown fox"));
                kept = pixels(studio.picture(64, 32).render(label("the quick brown fox")));
            }

            assertArrayEquals(pixels(plain), kept, "reuse is invisible in the output, which is the point");
        }

        @Test
        @DisplayName("two documents through one studio do not leak into each other")
        void keepsRendersIndependent() {
            try (var studio = Studio.of(sheet("""
                    .root { width: 16px; height: 16px; background: #ff0000 }
                    .other { width: 16px; height: 16px; background: #0000ff }
                    """))) {
                var red = studio.picture(16, 16).render(panel("root"));
                var blue = studio.picture(16, 16).render(panel("other"));
                var redAgain = studio.picture(16, 16).render(panel("root"));

                assertEquals(RED, red.argb(8, 8));
                assertEquals(BLUE, blue.argb(8, 8));
                assertEquals(RED, redAgain.argb(8, 8), "the second document left nothing behind");
            }
        }

        @Test
        @DisplayName("naming other stylesheets gives the kept renderer up rather than ignoring them")
        void namingSheetsDropsTheKeptRenderer() {
            // The trap in wiring a builder to a cached renderer: a caller who sets
            // stylesheets on a picture from a studio must not silently get the
            // studio's. A renderer *is* its cascade.
            try (var studio = Studio.of(sheet(CSS))) {
                var overridden = studio.picture(16, 16)
                        .stylesheets(sheet(".root { width: 16px; height: 16px; background: #0000ff }"))
                        .render(panel("root"));

                assertEquals(BLUE, overridden.argb(8, 8), "the sheets the caller named, not the studio's");
                assertEquals(
                        RED, studio.picture(16, 16).render(panel("root")).argb(8, 8), "and the studio is unharmed");
            }
        }

        @Test
        @DisplayName("a strip from a studio shares the book and not the clock")
        void stripsGetTheirOwnRenderer() {
            // A renderer holds one clock. A strip that used the studio's would move
            // the clock under every still picture taken beside it.
            try (var studio = Studio.of(sheet(CSS))) {
                try (var strip = studio.picture(16, 16).strip(panel("root"))) {
                    strip.advance(500).frame();
                }
                // The studio's renderer is untouched by the strip's 500 ms, which is
                // only observable as the still picture still being correct.
                assertEquals(RED, studio.picture(16, 16).render(panel("root")).argb(8, 8));
            }
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refusals {

        @Test
        @DisplayName("a picture after it has been closed")
        void refusesAfterClosing() {
            var studio = Studio.of(sheet(CSS));
            studio.picture(16, 16).render(panel("root"));
            studio.close();

            var refused = assertThrows(IllegalStateException.class, () -> studio.picture(16, 16));
            assertTrue(refused.getMessage().contains("closed"), refused.getMessage());
            studio.close();
        }

        @Test
        @DisplayName("a thread that did not open it")
        void refusesAnotherThread() throws InterruptedException {
            // The whole of ADR-0425's second half in one assertion: what makes reuse
            // possible is what must not be shared. A studio holds a font book, and a
            // book holds native faces confined to the thread that opened them.
            try (var studio = Studio.of(sheet(CSS))) {
                var raised = new AtomicReference<Throwable>();
                var thief = new Thread(() -> {
                    try {
                        studio.picture(16, 16).render(panel("root"));
                    } catch (Throwable t) {
                        raised.set(t);
                    }
                });
                thief.start();
                thief.join();

                assertNotNull(raised.get(), "a studio shared between threads is refused, not tolerated");
                assertTrue(raised.get() instanceof IllegalStateException, String.valueOf(raised.get()));
                assertTrue(
                        raised.get().getMessage().contains("one studio per thread"),
                        raised.get().getMessage());
            }
        }

        @Test
        @DisplayName("nothing to render with")
        void refusesNulls() {
            assertThrows(NullPointerException.class, () -> Studio.of(null));
            assertThrows(NullPointerException.class, () -> Studio.of(sheet(CSS), null));
            assertThrows(NullPointerException.class, () -> Studio.over(sheet(CSS), null));
        }
    }
}
