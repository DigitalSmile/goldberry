package io.github.digitalsmile.goldberry.widgets.core;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// `stack` — §1's "z-order layering; children positioned by alignment or
/// absolute insets" ([ADR-0250]).
///
/// Asserted against **Yoga's own output**, because every claim a stack makes is a
/// claim about where boxes ended up. Nothing here reads the widget back.
class StackTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(300, 200, 1.0f);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    /// Every laid-out rectangle from the stack down, in paint order.
    ///
    /// **Wrapped in a row that does not stretch it**, which is not arrangement
    /// for its own sake: the root box is always laid out at the frame's size, so
    /// a stack tested as the root would be 300 wide whatever its children did and
    /// every size assertion here would pass for the wrong reason.
    private List<LogicalRect> layouts(Widget stack, String css) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "#wrap { align-items: flex-start }\n" + css));
        var renderer = new WidgetRenderer(sheets, TestFont.get());
        var out = new ArrayList<LogicalRect>();
        var root = new Row(List.of(stack), id("wrap"));
        BoxPainter.forEachBox(target.frame(), renderer.render(new ElementTree(root)), (box, layout) -> out.add(layout));
        // Drop the wrapper, so index 0 is the stack exactly as the assertions read.
        return out.subList(1, out.size());
    }

    /// A box the stylesheet gives a size to. `Spacer` because it is the one
    /// primitive with nothing of its own to contribute — every dimension in these
    /// tests comes from the CSS above, where it can be read beside the assertion.
    private static Widget sized(String id) {
        return new Spacer(id(id));
    }

    /// Two boxes of different sizes: the stack must take the **first** one's
    /// size, because that is the child left in flow.
    @Test
    @DisplayName("the stack is the size of its first child, whatever is stacked on it")
    void theFirstChildSizesIt() {
        var css = """
                #under { width: 120px; height: 60px }
                #over  { width: 20px;  height: 20px }
                """;
        var placed = layouts(new Stack(sized("under"), sized("over")), css);

        // [0] is the stack itself.
        assertEquals(120, placed.getFirst().width(), 1e-6, "the stack should be its first child's width");
        assertEquals(60, placed.getFirst().height(), 1e-6, "the stack should be its first child's height");
    }

    /// And the point of taking the overlay out of flow: adding one must not move
    /// or resize what it sits on.
    @Test
    @DisplayName("adding an overlay changes nothing about the thing under it")
    void anOverlayIsFree() {
        var css = """
                #under { width: 120px; height: 60px }
                #over  { width: 20px;  height: 20px }
                """;
        var alone = layouts(new Stack(sized("under")), css);
        var covered = layouts(new Stack(sized("under"), sized("over")), css);

        assertEquals(alone.getFirst(), covered.getFirst(), "the stack moved when something was put on it");
        assertEquals(alone.get(1), covered.get(1), "the child under the overlay moved");
    }

    /// The overlay overlaps rather than sitting beside — which is the whole word
    /// "stack", and the thing a `row` would get wrong.
    @Test
    @DisplayName("an overlay is drawn over its sibling, not beside it")
    void itOverlaps() {
        var css = """
                #under { width: 120px; height: 60px }
                #over  { width: 20px;  height: 20px }
                """;
        var placed = layouts(new Stack(sized("under"), sized("over")), css);
        var over = placed.get(2);

        assertTrue(over.left() < 120, () -> "the overlay was laid out beside its sibling, at x=" + over.left());
        assertEquals(0, over.left(), 1e-6);
        assertEquals(0, over.top(), 1e-6);
    }

    /// §1's "positioned by **alignment**", and the half [ADR-0244] unblocked: an
    /// absolute child with no inset is placed by the container's alignment and by
    /// its own `align-self`, which is what `ComputedStyle.INITIAL`'s undefined
    /// insets have been describing all along.
    @Test
    @DisplayName("an overlay with no inset is placed by alignment")
    void alignmentPlacesIt() {
        var css = """
                #stack { align-items: flex-end; justify-content: flex-end }
                #under { width: 120px; height: 60px }
                #over  { width: 20px;  height: 20px }
                """;
        var stack = new Stack(List.of(sized("under"), sized("over")), id("stack"));
        var over = layouts(stack, css).get(2);

        assertEquals(100, over.left(), 1e-6, "flex-end on the main axis should put it at the right");
        assertEquals(40, over.top(), 1e-6, "flex-end on the cross axis should put it at the bottom");
    }

    /// And a per-child override, which is the case a badge actually wants: the
    /// stack aligns one way and the chip on it goes to the other corner.
    @Test
    @DisplayName("and align-self overrides the stack for one of them")
    void alignSelfOverridesIt() {
        var css = """
                #stack { align-items: flex-end; justify-content: flex-end }
                #under { width: 120px; height: 60px }
                #over  { width: 20px;  height: 20px; align-self: flex-start }
                """;
        var stack = new Stack(List.of(sized("under"), sized("over")), id("stack"));
        var over = layouts(stack, css).get(2);

        assertEquals(100, over.left(), 1e-6, "the main axis is still the stack's");
        assertEquals(0, over.top(), 1e-6, "align-self should have taken it back to the top");
    }

    /// §1's other half: "or absolute **insets**".
    @Test
    @DisplayName("an overlay with an inset goes exactly where it says")
    void insetsPlaceIt() {
        var css = """
                #under { width: 120px; height: 60px }
                #over  { width: 20px; height: 20px; top: 8px; left: 90px }
                """;
        var over = layouts(new Stack(sized("under"), sized("over")), css).get(2);

        assertEquals(90, over.left(), 1e-6);
        assertEquals(8, over.top(), 1e-6);
    }

    /// A stack of one is that child in a box, which is what makes wrapping an
    /// existing widget in a `stack` a change that cannot move it.
    @Test
    @DisplayName("a stack of one child is that child")
    void oneChild() {
        var css = "#under { width: 120px; height: 60px }";
        var placed = layouts(new Stack(sized("under")), css);

        assertEquals(120, placed.getFirst().width(), 1e-6);
        assertEquals(60, placed.getFirst().height(), 1e-6);
    }

    @Test
    @DisplayName("and an empty stack is legal and empty")
    void empty() {
        assertEquals(1, layouts(new Stack(), "").size(), "an empty stack should still be one box");
    }

    /// The markup half: `stack` is in §1's `core` list, so a document has to be
    /// able to write one. Inflated through the real catalog rather than
    /// constructed, because what is under test is the registration.
    @Test
    @DisplayName("a document can write one, and it stacks")
    void fromMarkup() {
        var doc = io.github.digitalsmile.goldberry.widgets.Widgets.inflater()
                .inflateAll(io.github.digitalsmile.goldberry.kdl.KdlParser.parse("""
                        row id="wrap" {
                            stack {
                                spacer id="under"
                                spacer id="over"
                            }
                        }
                        """))
                .getFirst();
        var css = """
                #wrap  { align-items: flex-start }
                #under { width: 120px; height: 60px }
                #over  { width: 20px;  height: 20px }
                """;
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
        var out = new ArrayList<LogicalRect>();
        BoxPainter.forEachBox(
                target.frame(),
                new WidgetRenderer(sheets, TestFont.get()).render(new ElementTree(doc)),
                (box, layout) -> out.add(layout));

        // [0] wrap, [1] stack, [2] under, [3] over.
        assertEquals(120, out.get(1).width(), 1e-6, "the inflated stack took its first child's width");
        assertEquals(0, out.get(3).left(), 1e-6, "the second child should be over the first, not beside it");
    }
}
