package io.github.digitalsmile.goldberry.widgets.settle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.form.textarea.TextArea;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.panel.split.SplitPane;
import io.github.digitalsmile.goldberry.widgets.panel.table.Column;
import io.github.digitalsmile.goldberry.widgets.panel.table.Table;

/// **Every `Measured` consumer this module can build settles** — [ADR-0420].
///
/// ADR-0117 opened a door with a rule on it: *read geometry to interpret an input
/// or to draw something that cannot affect layout, never to decide a size.*
/// Nothing enforced it. The scroll view obeyed it by construction, which is the
/// strongest kind of safe and the least transferable — the next widget gets no
/// help from it.
///
/// This is the enforcement, and it is a test rather than a runtime check because
/// the failure has no moment. A widget that breaks rule 3 throws nothing, logs
/// nothing and draws nothing wrong; it asks for one more frame, forever. The only
/// thing that tells it apart from a widget doing honest work is that it never
/// stops — which is a property of a *sequence* of frames and cannot be seen from
/// inside one.
///
/// So each case below runs the real loop until two consecutive frames lay out
/// identically, and asserts a small number. The number matters: one frame to be
/// measured, one to act on it, and at most a third that proves the second changed
/// nothing. A case that started needing seven would be a regression this catches
/// long before it becomes a loop.
///
/// [Settled] documents what this cannot reach.
class MeasuredFixedPointTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static List<Stylesheet> sheets(String css) {
        return List.of(
                Controls.baseStylesheet(), Theme.NORD_DARK.load(), Stylesheet.parse(CascadeLayer.APPLICATION, css));
    }

    private static Attributes id(String name) {
        return new Attributes(name, Set.of(), name);
    }

    /// Drives `root` to a fixed point and returns how many distinct layouts it
    /// went through, **having first checked that a `Measured` consumer was there
    /// to drive.** Without that second half a case whose widget stopped asking for
    /// geometry would settle in one layout and pass for the wrong reason.
    private static int settle(Widget root, String css, int width, int height) {
        try (var settled = Settled.of(root, sheets(css), width, height)) {
            var layouts = settled.settle();
            assertTrue(settled.consumers() > 0, "no `Measured` widget was placed, so this case proves nothing");
            return layouts;
        }
    }

    /// The same without that check, for the two cases that are about the harness
    /// rather than about a consumer.
    private static int layouts(Widget root, String css, int width, int height) {
        try (var settled = Settled.of(root, sheets(css), width, height)) {
            return settled.settle();
        }
    }

    @Nested
    @DisplayName("the harness itself, because a test that only ever passes proves nothing")
    class TheHarness {

        /// **The negative control.** A widget that reads its own width and picks a
        /// different one is exactly what rule 3 forbids, and if the harness cannot
        /// catch this then every green case below is green for no reason.
        ///
        /// It alternates rather than drifts, so it takes the *cycle* branch: a
        /// later frame is an earlier frame again, and no number of extra frames
        /// would help.
        @Test
        @DisplayName("a widget that sizes itself from its own measurement is caught, and named")
        void anOscillatorIsCaught() {
            var failure = assertThrows(AssertionError.class, () -> layouts(new Oscillator(id("swing")), "", 400, 200));

            assertTrue(failure.getMessage().contains("oscillates"), failure.getMessage());
            assertTrue(failure.getMessage().contains("ADR-0117 rule 3"), failure.getMessage());
            assertTrue(failure.getMessage().contains("What moved:"), "it says which box moved");
        }

        /// And it does not cry wolf on a tree with no `Measured` in it at all: a
        /// plain card is right on the first frame and proves it on the second.
        @Test
        @DisplayName("a tree with nothing measuring in it settles immediately")
        void aStillTreeSettlesAtOnce() {
            var still = new Row(List.of(new Card(List.of(), id("a"))), id("still"));

            assertEquals(1, layouts(still, "#still { width: 200px } #a { height: 40px; width: 40px }", 400, 200));
        }
    }

    @Nested
    @DisplayName("the consumers")
    class Consumers {

        /// `masonry` — the hardest case in the catalog, because it genuinely
        /// **moves boxes** in answer to a measurement. It is safe because the
        /// columns are equal-width, so a card's height does not depend on which
        /// column it landed in: the value it reports is invariant under the thing
        /// it changes.
        @Test
        @DisplayName("masonry reflows once and then holds still")
        void masonrySettles() {
            var cards = new ArrayList<Widget>();
            var css = new StringBuilder("#wall { width: 300px }\n");
            var heights = List.of(100, 20, 20, 20, 60, 30);
            for (var i = 0; i < heights.size(); i++) {
                cards.add(new Card(List.of(), id("c" + i)));
                css.append("#c")
                        .append(i)
                        .append(" { height: ")
                        .append(heights.get(i))
                        .append("px }\n");
            }

            assertEquals(
                    2, settle(new Masonry(cards, 3, id("wall")), css.toString(), 320, 400), "one reflow, then still");
        }

        /// `scroll` — ADR-0117's own example, and the one that obeys rule 3 *by
        /// construction*: the bars are absolutely positioned, so nothing the
        /// rebuild draws can change the rectangle that was measured.
        @Test
        @DisplayName("a scroll view with bars settles")
        void scrollSettles() {
            var content = new Row(List.of(new Card(List.of(), id("tall"))), id("inner"));
            var scroll = new Scroll(List.of(content), ScrollAxis.VERTICAL, id("view"));

            var css = "#view { height: 100px; width: 200px } #tall { height: 600px; width: 100px }";
            assertEquals(2, settle(scroll, css, 240, 160), "the bars appear on the second layout and change nothing");
        }

        /// `split-pane` — the one that most plainly **decides a size** from a
        /// measurement: it reads its own length and sets the first pane's. It
        /// terminates because the panes' main sizes sum to the parent's, so a
        /// child cannot change the number the parent reported.
        @Test
        @DisplayName("a split pane settles")
        void splitPaneSettles() {
            var split = new SplitPane(new Card(List.of(), id("l")), new Card(List.of(), id("r")));

            assertEquals(2, settle(new Row(List.of(split), id("shell")), "#shell { width: 400px }", 400, 200));
        }

        /// `table` — the header banks its own width so a resize drag has something
        /// to start from (ADR-0361). That is an **input** reading, not a size: the
        /// header's actual width comes from the application's column model, and
        /// the banked number is only ever a drag anchor.
        @Test
        @DisplayName("a resizable table header settles")
        void tableSettles() {
            var columns = List.of(
                    Column.<Person>of("name", "Name", Person::name).resizable(true),
                    Column.<Person>of("realm", "Realm", Person::realm));
            var table = new Table<>(List.of(new Person("f", "Frodo", "The Shire")), Person::id, columns);

            assertEquals(1, settle(table, "", 400, 160), "the banked width is a drag anchor and reaches no box");
        }

        /// `text-area` — it reads its width to decide where the text **wraps**,
        /// which changes its own content height. Safe on the cross axis only, and
        /// its own doc says so; this is what holds that claim down.
        @Test
        @DisplayName("a wrapping text area settles")
        void textAreaSettles() {
            var area =
                    new TextArea("A long enough line of text that it has to wrap more than once", s -> {}).rows(2, 4);

            assertEquals(1, settle(new Row(List.of(area), id("form")), "#form { width: 160px }", 200, 200));
        }

        /// `text-input` — reads its box to place an absolutely positioned caret
        /// and to scroll the value under it. The purest rule-3 case in the
        /// catalog: it does not `setState` at all.
        @Test
        @DisplayName("a text input settles")
        void textInputSettles() {
            var input = new TextInput("a value long enough to need scrolling inside a narrow field", s -> {});

            assertEquals(1, settle(new Row(List.of(input), id("form")), "#form { width: 120px }", 200, 120));
        }

        // `toast` is deliberately absent, and the attempt is worth recording
        // because of how it failed. A `Toaster` builds its plates into the host's
        // overlay layer rather than into its own subtree (ADR-0177), so a
        // windowless harness lays out a `toaster` with nothing in it: `settle()`
        // returned 1 and `Settled.consumers()` returned 0. The vacuity guard is
        // the only reason that was a red test rather than a seventh green one.
        // ADR-0420 records it as uncovered rather than papering over it, and
        // `ToastTest` feeds `measured(...)` by hand for the same reason.
    }

    private record Person(String id, String name, String realm) {}

    // --- the negative control ------------------------------------------------

    /// A widget that breaks [Measured]'s third rule on purpose.
    private record Oscillator(Attributes attributes) implements Widget.Stateful {

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public State<?> createState() {
            return new Swing();
        }
    }

    private static final class Swing extends State<Oscillator> {

        private double width = 50;

        @Override
        public Widget build(BuildContext context) {
            return new Plank(width, this::measured);
        }

        /// The forbidden move, in one line: **decide a size from a measurement,
        /// where the size changes the measurement.**
        private void measured(double measured) {
            var next = measured > 100 ? 60 : 160;
            if (next != width) {
                setState(() -> width = next);
            }
        }
    }

    private record Plank(double width, DoubleConsumer onMeasured) implements Widget.Leaf, Styled, Paints, Measured {

        @Override
        public String cssType() {
            return "plank";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public void measured(Extent bounds, Extent part) {
            onMeasured.accept(bounds.width());
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().background(0xFF00FF00).shrink(0).size(Length.points((float) width), Length.points(20));
        }
    }
}
