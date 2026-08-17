package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.ParagraphCache;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The retained render tree, and the reconciliation that keeps it correct —
/// ADR-0069.
///
/// The risk in retention is not that it is slow; it is that a node reused when it
/// should have been rebuilt lays out against a style nobody set, or measures with
/// a callback closed over the wrong paragraph. Every test here is one of those.
class RenderTreeTest {

    private TestFrames.Target target;
    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(200, 200, 1.0f);
        font = Font.bundled(BundledFont.UI, 14);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
        if (font != null) {
            font.close();
        }
    }

    /// Every box's rectangle, in walk order.
    private static List<ComputedLayout> layouts(RenderTree tree) {
        var out = new ArrayList<ComputedLayout>();
        tree.forEachPlacedBox(placed -> out.add(placed.layout()));
        return out;
    }

    private static Box sized(float width, float height) {
        return Box.filled(0xFF00FF00)
                .size(StyleLength.points(width), StyleLength.points(height));
    }

    @Nested
    @DisplayName("the same answer as the throwaway path")
    class Equivalence {

        /// The invariant the whole change rests on. Everything else here is
        /// about *how* the tree is kept; this is about it still being right.
        @Test
        @DisplayName("a retained tree lays out identically to a thrown-away one")
        void matchesBoxPainter() {
            var box = Box.filled(0xFF000000)
                    .size(StyleLength.points(200), StyleLength.points(200))
                    .direction(FlexDirection.COLUMN)
                    .padding(StyleLength.points(8))
                    .gap(StyleLength.points(4))
                    .children(sized(40, 20), sized(60, 30), Box.of().grow(1));

            var thrownAway = new ArrayList<ComputedLayout>();
            BoxPainter.forEachBox(target.frame(), box, (b, layout) -> thrownAway.add(layout));

            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), box);
                assertEquals(thrownAway, layouts(tree));
            }
        }

        @Test
        @DisplayName("and still does on the tenth frame")
        void staysIdenticalAcrossFrames() {
            // The failure this catches: a guard that skips a setter Yoga needed,
            // which produces a correct first frame and a wrong second one.
            var box = Box.filled(0xFF000000)
                    .size(StyleLength.points(200), StyleLength.points(200))
                    .direction(FlexDirection.COLUMN)
                    .children(sized(40, 20), sized(60, 30));

            var expected = new ArrayList<ComputedLayout>();
            BoxPainter.forEachBox(target.frame(), box, (b, layout) -> expected.add(layout));

            try (var tree = RenderTree.create()) {
                for (var frame = 0; frame < 10; frame++) {
                    tree.update(target.frame(), box);
                    assertEquals(expected, layouts(tree), "frame " + frame);
                }
            }
        }
    }

    @Nested
    @DisplayName("reconciliation")
    class Reconciliation {

        @Test
        @DisplayName("a changed style re-lays out")
        void styleChange() {
            // The guards in `RenderObject.apply` skip a setter when the value is
            // unchanged. If one of them compared the wrong field, or compared by
            // identity where the box is rebuilt every frame, this is what would
            // fail: the node would keep the old width forever.
            try (var tree = RenderTree.create()) {
                var root = Box.of().size(StyleLength.points(200), StyleLength.points(200));

                tree.update(target.frame(), root.children(sized(40, 20)));
                assertEquals(40, layouts(tree).get(1).width());

                tree.update(target.frame(), root.children(sized(90, 20)));
                assertEquals(90, layouts(tree).get(1).width(),
                        "the second frame's width never reached Yoga");
            }
        }

        @Test
        @DisplayName("an added child appears and a removed one goes")
        void structureChange() {
            try (var tree = RenderTree.create()) {
                var root = Box.of()
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .direction(FlexDirection.COLUMN);

                tree.update(target.frame(), root.children(sized(40, 20)));
                assertEquals(2, tree.size());

                tree.update(target.frame(), root.children(sized(40, 20), sized(40, 30)));
                assertEquals(3, tree.size());
                assertEquals(20, layouts(tree).get(2).top(), "the new child is below the first");

                tree.update(target.frame(), root.children(sized(40, 20)));
                assertEquals(2, tree.size(), "the removed child's node is gone, not orphaned");
            }
        }

        @Test
        @DisplayName("a text box that becomes a container is rebuilt, not reused")
        void kindChange() {
            // Yoga refuses children on a node with a measure function, so these
            // two are not interchangeable. Reusing the node would throw from
            // inside `insertChild` -- which is the good outcome; the bad one is a
            // wrapper that cleared the measure function and left Yoga's cached
            // measurement in place.
            try (var tree = RenderTree.create()) {
                var root = Box.of().size(StyleLength.points(200), StyleLength.points(200));

                tree.update(target.frame(), root.children(
                        Box.text(Paragraph.of(font, "hello"), 0xFFFFFFFF)));
                assertEquals(2, tree.size());

                tree.update(target.frame(), root.children(
                        Box.of().children(sized(30, 30))));
                assertEquals(3, tree.size(), "the container and its child");
                assertEquals(30, layouts(tree).get(2).width());
            }
        }

        @Test
        @DisplayName("a box tree of a different shape entirely is taken")
        void wholesaleReplacement() {
            // An application that swaps screens hands over something with nothing
            // in common with what was there. Nothing should be reused and nothing
            // should leak.
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), Box.of()
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .children(sized(10, 10), sized(20, 20), sized(30, 30)));
                assertEquals(4, tree.size());

                tree.update(target.frame(), Box.text(Paragraph.of(font, "a screen away"), 0xFF000000));
                assertEquals(1, tree.size());
            }
        }
    }

    @Nested
    @DisplayName("measure callbacks")
    class Measuring {

        @Test
        @DisplayName("the same paragraph across frames keeps its callback")
        void keepsTheCallback() {
            // The 11 us this whole exercise is about. There is no way to observe
            // "the callback was not rebuilt" directly, so this asserts the thing
            // it depends on and the thing it produces: the cache hands back one
            // instance, and the measured height is stable.
            var cache = ParagraphCache.create();
            var first = cache.paragraph(font, "wrap me around the box");
            var second = cache.paragraph(font, "wrap me around the box");
            assertTrue(first == second, "the cache is what makes identity stable");

            try (var tree = RenderTree.create()) {
                var root = Box.of()
                        .size(StyleLength.points(120), StyleLength.points(200))
                        .direction(FlexDirection.COLUMN);

                tree.update(target.frame(), root.children(Box.text(first, 0xFF000000)));
                var height = layouts(tree).get(1).height();

                tree.update(target.frame(), root.children(Box.text(second, 0xFF000000)));
                assertEquals(height, layouts(tree).get(1).height());
                assertTrue(height > 0, "the paragraph measured something");
            }
        }

        @Test
        @DisplayName("different text re-measures rather than reporting the old size")
        void remeasures() {
            // The failure a naive "keep the callback" would produce: the node
            // keeps measuring the first paragraph and the new text is laid out
            // at the old one's height, with nothing to report.
            try (var tree = RenderTree.create()) {
                var root = Box.of()
                        .size(StyleLength.points(90), StyleLength.points(200))
                        .direction(FlexDirection.COLUMN);

                tree.update(target.frame(), root.children(
                        Box.text(Paragraph.of(font, "one"), 0xFF000000)));
                var shortHeight = layouts(tree).get(1).height();

                tree.update(target.frame(), root.children(
                        Box.text(Paragraph.of(font,
                                "a much longer run of words that has to wrap several times over"),
                                0xFF000000)));
                var tallHeight = layouts(tree).get(1).height();

                assertTrue(tallHeight > shortHeight,
                        "the wrapped paragraph measured " + tallHeight
                                + ", the same as the one word it replaced");
            }
        }
    }

    @Nested
    @DisplayName("lifetime")
    class Lifetime {

        @Test
        @DisplayName("a scale change rebuilds, because the pixel grid moved")
        void scaleChange() {
            // Yoga rounds every computed edge onto the config's grid and a config
            // change does not dirty a node, so there is no way to ask for the
            // rounding to be redone. The tree goes instead.
            var half = TestFrames.of(300, 300, 1.5f);
            try (var tree = RenderTree.create()) {
                var box = Box.of()
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .padding(StyleLength.points(5))
                        .children(sized(33, 33));

                tree.update(target.frame(), box);
                var atOne = layouts(tree);

                tree.update(half.frame(), box);
                var atOneAndAHalf = layouts(tree);

                assertEquals(atOne.size(), atOneAndAHalf.size());
                assertTrue(tree.size() > 0, "the tree was rebuilt, not emptied");
            } finally {
                half.end();
            }
        }

        @Test
        @DisplayName("a closed tree refuses to be used again")
        void closed() {
            var tree = RenderTree.create();
            tree.update(target.frame(), sized(10, 10));
            tree.close();

            assertThrows(IllegalStateException.class,
                    () -> tree.update(target.frame(), sized(10, 10)));
            assertThrows(IllegalStateException.class,
                    () -> tree.forEachPlacedBox(placed -> { }));
        }

        @Test
        @DisplayName("closing twice is harmless")
        void closedTwice() {
            var tree = RenderTree.create();
            tree.update(target.frame(), sized(10, 10));
            tree.close();
            tree.close();
        }

        @Test
        @DisplayName("walking before the first update says so")
        void neverUpdated() {
            try (var tree = RenderTree.create()) {
                assertEquals(0, tree.size());
                assertThrows(IllegalStateException.class,
                        () -> tree.forEachPlacedBox(placed -> { }));
            }
        }
    }

    @Nested
    @DisplayName("with the rest of the pipeline")
    class Pipeline {

        @Test
        @DisplayName("transforms accumulate through a retained tree too")
        void transforms() {
            // The walk moved from BoxPainter into RenderTree, so the transform
            // accumulation moved with it. This is the assertion that says it
            // arrived intact.
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), Box.of()
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .children(Box.filled(0xFF00FF00)
                                .size(StyleLength.points(40), StyleLength.points(40))
                                .transform(Transform.of(new Transform.Function.Translate(
                                        Transform.Length.px(100), Transform.Length.ZERO)))
                                .children(sized(20, 20))));

                var transforms = new ArrayList<io.github.digitalsmile.goldberry.css.Affine>();
                tree.forEachPlacedBox(placed -> transforms.add(placed.transform()));

                assertTrue(transforms.get(0).isIdentity(), "the root is where it was laid out");
                assertEquals(100, transforms.get(1).e(), 1e-9);
                assertEquals(100, transforms.get(2).e(), 1e-9, "the child inherits it");
            }
        }

        @Test
        @DisplayName("one layout pass answers both the painter and hit testing")
        void oneePassTwoReaders() {
            // What the showcase does. `HitTest.capture(tree)` reads the pass that
            // `update` already ran, rather than running a second one -- which is
            // what the `(Frame, Box)` overload does and what a window was paying
            // for twice per frame.
            try (var tree = RenderTree.create()) {
                var box = Box.filled(0xFF000000)
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .children(sized(40, 40).owner("target"));

                tree.update(target.frame(), box);
                tree.paint(target.frame());

                var regions = io.github.digitalsmile.goldberry.input.HitTest.capture(tree);
                assertEquals("target",
                        io.github.digitalsmile.goldberry.input.HitTest.at(regions, 20, 20)
                                .orElseThrow());
                assertNotEquals(0, regions.size());
            }
        }
    }
}
