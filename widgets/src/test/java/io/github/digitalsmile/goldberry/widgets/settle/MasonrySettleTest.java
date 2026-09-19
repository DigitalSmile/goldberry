package io.github.digitalsmile.goldberry.widgets.settle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// **A responsive `masonry` settles, and the arrangement that was supposed to
/// make it oscillate does not** — [ADR-0436].
///
/// [MeasuredFixedPointTest] already holds `masonry` with a fixed `columns`: it
/// moves boxes in answer to a measurement, and it is safe because the columns are
/// equal width, so a card's height does not depend on which column it landed in.
/// `min-column-width` opens the door one step further — the wall reads its **own
/// width** and changes the number of columns — and a second claim under
/// ADR-0117's third rule needs its own evidence rather than the first one's.
///
/// The claim is: *a column count changes the wall's height and not its width.*
/// `core-widgets.md` §1 hedges it — true of a wall whose width comes from its
/// parent, and a wall in a shrink-to-fit box "would oscillate". The second half
/// of that is what [ShrinkToFit] went to check, and it is not what happens.
class MasonrySettleTest {

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

    /// Cards of a height the stylesheet fixes, so that re-columning the wall
    /// cannot change any of them.
    private static List<Widget> fixed(List<Integer> heights, StringBuilder css) {
        var kids = new ArrayList<Widget>(heights.size());
        for (var i = 0; i < heights.size(); i++) {
            kids.add(new Card(List.of(), id("c" + i)));
            css.append("#c")
                    .append(i)
                    .append(" { height: ")
                    .append(heights.get(i))
                    .append("px }\n");
        }
        return kids;
    }

    /// Cards of prose, which is the opposite and is what a real wall is made of:
    /// a card's height is a function of the width it was laid out at, so the
    /// moment the wall re-columns every banked height in it is wrong.
    private static List<Widget> prose(int count) {
        var kids = new ArrayList<Widget>(count);
        for (var i = 0; i < count; i++) {
            kids.add(new Card(List.of(new Text("prose ".repeat(4 + i * 9), Attributes.NONE)), id("c" + i)));
        }
        return kids;
    }

    @Nested
    @DisplayName("a wall whose width comes from its parent")
    class Stretched {

        /// **The case this mode exists for, at its worst**, and the number is the
        /// point of the test. Three distinct layouts, where a fixed `masonry`
        /// takes two, and the third one is not slack:
        ///
        ///   1. nothing has measured the wall yet, so it is one column and every
        ///      card is laid out at the wall's whole width;
        ///   2. the wall has been told how wide it is and re-columns — which puts
        ///      every card at a *different* width, so every height banked in (1)
        ///      goes stale in the same instant;
        ///   3. the heights as measured at the real column width, re-dealt.
        ///
        /// A fourth would mean the deal changed something the deal depends on,
        /// which is the loop. Three is also exactly what `Offscreen` affords — it
        /// runs two measuring passes and paints the third — so a responsive wall
        /// is photographed **settled with nothing to spare**, and a fourth layout
        /// here would be a gallery of walls caught mid-reflow rather than a red
        /// test. That is why this number is asserted rather than bounded.
        @Test
        @DisplayName("a responsive wall of prose reflows twice and then holds still")
        void aWallOfProseSettles() {
            var wall = new Masonry(prose(6), Masonry.UNSET, 320, id("wall"));
            var shell = new Column(List.of(wall), id("shell"));

            try (var settled = Settled.of(shell, sheets("#shell { width: 800px }"), 900, 900)) {
                var layouts = settled.settle();

                assertTrue(settled.consumers() > 0, "no `Measured` widget was placed, so this proves nothing");
                assertEquals(3, layouts, "one layout to be measured, one to re-column, one to re-deal");
            }
        }

        /// The same wall of cards the stylesheet gives a height, which costs the
        /// third layout back: re-columning cannot change a height that is written
        /// down, so the deal after the re-column is the deal before it.
        ///
        /// Worth its own case because the difference between the two numbers says
        /// *why* the one above is three — the extra layout is the cards', not the
        /// column count's.
        @Test
        @DisplayName("a responsive wall of fixed-height cards takes the fixed wall's two")
        void heightsThatCannotMoveCostNothing() {
            var css = new StringBuilder("#shell { width: 800px }\n");
            var wall = new Masonry(fixed(List.of(100, 20, 20, 20, 60, 30), css), Masonry.UNSET, 320, id("wall"));
            var shell = new Column(List.of(wall), id("shell"));

            try (var settled = Settled.of(shell, sheets(css.toString()), 900, 600)) {
                assertEquals(2, settled.settle(), "one layout to be measured, one to re-column");
            }
        }

        /// Too narrow to divide, which is the branch that costs nothing at all:
        /// the count it settles on is the count it started at, so there was never
        /// a second answer to converge on.
        @Test
        @DisplayName("a wall too narrow for two columns never reflows")
        void aNarrowWallCostsNoLayoutAtAll() {
            var css = new StringBuilder("#shell { width: 400px }\n");
            var wall = new Masonry(fixed(List.of(100, 20, 20, 20), css), Masonry.UNSET, 320, id("wall"));
            var shell = new Column(List.of(wall), id("shell"));

            try (var settled = Settled.of(shell, sheets(css.toString()), 500, 600)) {
                assertEquals(1, settled.settle(), "one column before and after, so nothing moved");
            }
        }

        /// A fixed wall is untouched by any of this — the same six cards and the
        /// same two layouts as [MeasuredFixedPointTest]'s case. Asserted here so
        /// that a change to the responsive path which quietly costs the fixed path
        /// a frame goes red in the file that made the change.
        @Test
        @DisplayName("a fixed masonry still takes two, and the width it is told changes nothing")
        void aFixedMasonryIsUnchanged() {
            var css = new StringBuilder("#wall { width: 300px }\n");
            var wall = new Masonry(fixed(List.of(100, 20, 20, 20, 60, 30), css), 3, id("wall"));

            try (var settled = Settled.of(wall, sheets(css.toString()), 320, 400)) {
                assertEquals(2, settled.settle());
            }
        }
    }

    @Nested
    @DisplayName("a wall in a shrink-to-fit box, which is the case the specification hedges")
    class ShrinkToFit {

        /// **`core-widgets.md` §1 says this would oscillate. It does not, and the
        /// reason it does not is one line of `controls.css`.**
        ///
        /// The feared loop is real arithmetic: a masonry sized to its own content
        /// would be `n` columns each as wide as the widest card in it, so a bigger
        /// `n` makes a wider wall, which asks for a bigger `n`. It needs the
        /// columns to be as wide as their cards — and since ADR-0373 they are not.
        /// A `masonry-column` is `flex-basis: 0`, so it contributes **nothing** to
        /// its parent's content width, and a masonry with no definite width comes
        /// out at zero however many columns it has.
        ///
        /// So the wall's width is independent of its column count by
        /// construction, which is precisely what ADR-0117's third rule asks for —
        /// and it is a property of the *stylesheet* rather than of the parent's
        /// good behaviour. The hedge in the specification describes a layout this
        /// toolkit cannot produce.
        ///
        /// What it produces instead is a wall zero pixels wide whose cards hang
        /// out of it: degenerate, stable, and the same picture a fixed `columns`
        /// gives in the same box. Which is
        /// [#andTheSameIsTrueOfAFixedCount]'s job, because *"it is degenerate
        /// either way"* and *"`min-column-width` broke it"* are very different
        /// claims and only one of them is true.
        @Test
        @DisplayName("it does not oscillate: the wall has no content width to feed back")
        void itDoesNotLoop() {
            var css = new StringBuilder("card { width: 240px }\n");
            var wall = new Masonry(fixed(List.of(40, 40, 40, 40), css), Masonry.UNSET, 100, id("wall"));
            // A `row` sizes its children on the main axis, so this wall's width is
            // its own content's -- the one arrangement `min-column-width` was
            // warned off.
            var shell = new Row(List.of(wall), id("shell"));

            try (var settled = Settled.of(shell, sheets(css.toString()), 2000, 600)) {
                assertEquals(1, settled.settle(), "no second answer, so no loop and nothing to refuse");
            }
        }

        /// The same box and the same cards with a fixed `columns=2`: also one
        /// layout, because the degeneracy belongs to the box and predates the mode
        /// that was blamed for it.
        @Test
        @DisplayName("and the same is true of a fixed count, so it is the box and not the mode")
        void andTheSameIsTrueOfAFixedCount() {
            var css = new StringBuilder("card { width: 240px }\n");
            var wall = new Masonry(fixed(List.of(40, 40, 40, 40), css), 2, id("wall"));
            var shell = new Row(List.of(wall), id("shell"));

            try (var settled = Settled.of(shell, sheets(css.toString()), 2000, 600)) {
                assertEquals(1, settled.settle());
            }
        }
    }
}
