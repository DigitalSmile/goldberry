package io.github.digitalsmile.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The walk **up** from a target to the viewport that holds it
/// ([ADR-0439]).
///
/// [ScrollControllerTest] covers the other direction — a handle created above a
/// viewport and handed into it. What is asserted here is the half a `tour` needs
/// and could not previously ask for: given nothing but the widget, which
/// `scroll` moves it.
class ScrollScopeTest {

    private static final int VIEWPORT_HEIGHT = ScrollHarness.VIEWPORT_HEIGHT;

    private final List<ScrollHarness> harnesses = new ArrayList<>();

    private ScrollHarness harness(Widget root) {
        var made = new ScrollHarness(root);
        harnesses.add(made);
        return made;
    }

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        harnesses.forEach(ScrollHarness::close);
        harnesses.clear();
    }

    /// Thirty rows in a viewport a hundred pixels tall — the same document
    /// [ScrollControllerTest] uses, and with **no controller wired**, which is
    /// the whole point.
    private static Widget document() {
        var rows = new ArrayList<Widget>();
        for (var i = 0; i < 30; i++) {
            rows.add(new Text("row " + i, Attributes.NONE.id("row" + i)));
        }
        return new Scroll(
                List.of(new Column(rows.toArray(Widget[]::new))), ScrollAxis.VERTICAL, Attributes.NONE.id("outer"));
    }

    @Nested
    @DisplayName("finding the viewport")
    class Finding {

        @Test
        @DisplayName("a row inside a scroll view finds the viewport that holds it")
        void findsTheEnclosingViewport() {
            var harness = harness(document());

            var scope = ScrollScope.enclosing(harness.element("row20"));

            assertTrue(scope.isPresent(), "row20 is inside a `scroll` and nothing found it");
            assertEquals(ScrollAxis.VERTICAL, scope.get().axis());
        }

        @Test
        @DisplayName("a widget in no scroll view answers empty rather than failing")
        void nothingEncloses() {
            var harness = harness(new Column(new Text("alone", Attributes.NONE.id("alone"))));

            assertFalse(ScrollScope.enclosing(harness.element("alone")).isPresent());
        }

        @Test
        @DisplayName("a null target is empty, which is what a region nobody claimed answers")
        void nullTarget() {
            // `HitTest.Region.owner()` is documented as "an `Element` in the
            // widget stack, or null for a box nobody claimed", so every caller
            // that starts from a region can hand this a null.
            assertFalse(ScrollScope.enclosing(null).isPresent());
        }

        @Test
        @DisplayName("a scroll named by its own id finds itself, and revealing it moves nothing")
        void theViewportsOwnId() {
            var harness = harness(document());
            var before = harness.rowRect("row0").top();

            // The walk starts at the target's *parent*, and this still answers
            // with the viewport itself: an `id` written on a `scroll` lands on
            // the `ScrollViewport` it builds, so the first parent of the only
            // element anybody can name is the state that holds the offset.
            var scope = ScrollScope.enclosing(harness.element("outer"));
            assertTrue(scope.isPresent(), "a `scroll`'s own id resolves to its inner node, whose parent holds it");

            // Which is harmless where a tour would hit it: a viewport asked to
            // bring itself into its own view is already there.
            var self = harness.rowRect("outer");
            scope.get().reveal(self, self);
            harness.settle();

            assertEquals(before, harness.rowRect("row0").top(), 0.01);
        }
    }

    @Nested
    @DisplayName("revealing through it")
    class Revealing {

        @Test
        @DisplayName("a row below the fold is brought into view with no controller wired anywhere")
        void revealsWithoutAController() {
            var harness = harness(document());
            var viewport = LogicalRect.of(0, 0, ScrollHarness.WIDTH, VIEWPORT_HEIGHT);
            var scope = ScrollScope.enclosing(harness.element("row20")).orElseThrow();

            scope.reveal(harness.rowRect("row20"), viewport);
            harness.settle();

            var after = harness.rowRect("row20");
            assertTrue(
                    after.top() >= -1 && after.top() + after.size().height() <= VIEWPORT_HEIGHT + 1,
                    "row20 is at " + after.top() + ", still outside the viewport");
        }

        @Test
        @DisplayName("it moves the least it can, exactly as the controller does")
        void minimal() {
            var harness = harness(document());
            var scope = ScrollScope.enclosing(harness.element("row20")).orElseThrow();

            scope.reveal(harness.rowRect("row20"), LogicalRect.of(0, 0, ScrollHarness.WIDTH, VIEWPORT_HEIGHT));
            harness.settle();

            // Up to the bottom edge and no further -- the same arithmetic, because
            // it is now literally the same method ([ScrollState#reveal]).
            var after = harness.rowRect("row20");
            assertTrue(
                    after.top() > VIEWPORT_HEIGHT / 2.0,
                    "the row was pulled further than it needed to be; it is at " + after.top());
        }

        @Test
        @DisplayName("a row already in view moves nothing")
        void alreadyVisible() {
            var harness = harness(document());
            var scope = ScrollScope.enclosing(harness.element("row1")).orElseThrow();
            var before = harness.rowRect("row0").top();

            scope.reveal(harness.rowRect("row1"), LogicalRect.of(0, 0, ScrollHarness.WIDTH, VIEWPORT_HEIGHT));
            harness.settle();

            assertEquals(before, harness.rowRect("row0").top(), 0.01);
        }
    }

    @Nested
    @DisplayName("nested viewports")
    class Nested_ {

        /// A short `scroll` inside a taller one, both vertical and both with
        /// something to scroll.
        ///
        /// The inner one warns — §2.4 discourages this and [ScrollState] says so
        /// — and that is exactly why it is worth pinning: the shape is
        /// discouraged rather than refused, so it happens. **Both must overflow**
        /// or the assertion below is vacuous: an outer viewport with nothing to
        /// scroll stays still whichever one the walk picked.
        private Widget nested() {
            var inner = new ArrayList<Widget>();
            for (var i = 0; i < 30; i++) {
                inner.add(new Text("inner " + i, Attributes.NONE.id("inner" + i)));
            }
            var list = new Scroll(
                            List.of(new Column(inner.toArray(Widget[]::new))),
                            ScrollAxis.VERTICAL,
                            Attributes.NONE.id("inner-scroll"))
                    .height(50);

            var outer = new ArrayList<Widget>();
            outer.add(list);
            for (var i = 0; i < 30; i++) {
                outer.add(new Text("after " + i, Attributes.NONE.id("after" + i)));
            }
            return new Scroll(
                    List.of(new Column(outer.toArray(Widget[]::new))),
                    ScrollAxis.VERTICAL,
                    Attributes.NONE.id("outer-scroll"));
        }

        @Test
        @DisplayName("the innermost one moves, and the outer one is left where the reader put it")
        void innermostWins() {
            var harness = harness(nested());
            var outerRowBefore = harness.rowRect("after0").top();
            var scope = ScrollScope.enclosing(harness.element("inner20")).orElseThrow();

            scope.reveal(harness.rowRect("inner20"), harness.rowRect("inner-scroll"));
            harness.settle();

            // If the walk had found the *outer* viewport it would have scrolled
            // the page to chase a row that was never going to leave the inner
            // list, and every row after the list would have moved with it.
            assertEquals(
                    outerRowBefore,
                    harness.rowRect("after0").top(),
                    0.5,
                    "the outer viewport scrolled, so the walk did not stop at the nearest one");
            var revealed = harness.rowRect("inner20");
            var clip = harness.rowRect("inner-scroll");
            assertTrue(
                    revealed.top() >= clip.top() - 1
                            && revealed.top() + revealed.size().height()
                                    <= clip.top() + clip.size().height() + 1,
                    "inner20 is at " + revealed.top() + ", not inside the inner viewport at " + clip.top());
        }
    }
}
