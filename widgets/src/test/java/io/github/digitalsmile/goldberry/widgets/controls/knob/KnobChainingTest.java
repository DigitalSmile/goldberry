package io.github.digitalsmile.goldberry.widgets.controls.knob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// A `knob` inside a `scroll`, which is the bubble path for a wheel and the one
/// arrangement neither widget's own tests can build ([ADR-0236]).
///
/// `TODO.md` had been holding this open since [ADR-0089] under the heading
/// "`Kind.WHEEL` had exactly one consumer, and it showed": the wheel *route* has
/// been covered since ADR-0061 — a fabricated SDL event through the real
/// translate and the real sink — but until `scroll` shipped there was nothing
/// above a knob for an unconsumed wheel to reach, so the half of the contract
/// that is about **not** consuming had never been run. Both cases the entry
/// named are here, and the second is the one it predicted would fail.
///
/// Everything is asserted through the **real router against painted regions**,
/// for [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollTest]'s
/// reason: a scroll view is arithmetic on two rectangles that do not exist until
/// Yoga has run, and the knob has to be found where it was actually drawn rather
/// than where the markup suggests.
///
/// The list is **scrolled off its top before every case**, which is not
/// arrangement for its own sake. A wheel that chains has to reach a scroll view
/// with somewhere to go, and the direction a knob at its *maximum* rejects is the
/// one that scrolls a list *up* — so a list left at the top would have refused
/// the wheel for its own reason and the test would have passed before the fix.
class KnobChainingTest {

    /// Short enough that the column of rows overflows it several times over.
    private static final int VIEWPORT_HEIGHT = 120;

    /// Rows above the knob, so it is still fully in view after [#PRE_SCROLL].
    private static final int LEAD_ROWS = 4;

    /// Plenty to travel through, so "the list scrolled" is never a clamp away
    /// from "the list is already at the end".
    private static final int ROWS = 20;

    /// How far the list is taken off its top first, in wheel lines. Two, because
    /// the knob has to survive it and one line is `ScrollViewport.LINE` = 20px.
    private static final float PRE_SCROLL = 2;

    /// A grid coarse enough that one wheel line is unmistakable in an assertion.
    private static final double STEP = 5;

    private TestFrames.Target target;
    private RenderTree render;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        if (render != null) {
            render.close();
            render = null;
        }
        if (target != null) {
            target.end();
            target = null;
        }
    }

    @Test
    @DisplayName("a knob turns without scrolling the list it is in")
    void theKnobTakesItsOwnWheel() {
        var harness = new Harness(50);
        var where = harness.contentMiddle();

        harness.wheelOverTheKnob(1);

        assertEquals(45.0, harness.gain.get(), 1e-9, "the wheel over the knob did not turn it");
        assertEquals(where, harness.contentMiddle(), 0.5, "turning the knob scrolled the list behind it");
    }

    /// The case `TODO.md` predicted would fail. `Knob.wheel` consumed
    /// unconditionally, so a knob pinned at its maximum swallowed every upward
    /// scroll and the list it sat in would not move — the pointer had to be taken
    /// off the control to scroll past it, which is not how any of this works
    /// anywhere else.
    @Test
    @DisplayName("a knob at its maximum lets the scroll through")
    void theEndOfTheTravelChains() {
        var harness = new Harness(100);
        var where = harness.contentMiddle();

        // Towards the user is *more*, which is the direction a knob already at
        // its maximum has nothing to do with -- and the direction the list,
        // taken off its top by the harness, still has room in.
        harness.wheelOverTheKnob(-3);

        assertEquals(100.0, harness.gain.get(), 1e-9, "a knob at its maximum turned past it");
        assertTrue(
                harness.contentMiddle() > where + 1,
                "the scroll view never saw the wheel; the content is still at " + harness.contentMiddle());
    }

    /// Which is the half that keeps the fix honest: chaining at the end must not
    /// cost the knob the direction it *can* still move, or a knob at its maximum
    /// would be one nothing could turn back down.
    @Test
    @DisplayName("and the same knob still turns the other way")
    void theOtherDirectionIsStillTheKnobs() {
        var harness = new Harness(100);
        var where = harness.contentMiddle();

        harness.wheelOverTheKnob(1);

        assertEquals(95.0, harness.gain.get(), 1e-9, "a knob at its maximum refused to come down");
        assertEquals(where, harness.contentMiddle(), 0.5, "coming down off the maximum scrolled the list as well");
    }

    /// The other end of the travel, and the reason it is worth a second case: at
    /// the minimum the direction that chains is the one that scrolls a list
    /// *down*, so this is the pair of signs the maximum case cannot check.
    @Test
    @DisplayName("a knob at its minimum lets the scroll through the other way")
    void theOtherEndChainsToo() {
        var harness = new Harness(0);
        var where = harness.contentMiddle();

        harness.wheelOverTheKnob(2);

        assertEquals(0.0, harness.gain.get(), 1e-9, "a knob at its minimum turned below it");
        assertTrue(
                harness.contentMiddle() < where - 1,
                "the scroll view never saw the wheel; the content is still at " + harness.contentMiddle());
    }

    /// The case that opened [ADR-0238] while this file was being written, and
    /// the one a knob cannot fix for itself: a **disabled** knob never reaches
    /// [Knob#onPointer] at all, because the router refuses input to a disabled
    /// subtree — and used to refuse it by returning before the chain was built,
    /// so the `scroll` above never got a turn either.
    @Test
    @DisplayName("a disabled knob is not a place a scroll stops")
    void aDisabledKnobChains() {
        var harness = new Harness(50, true);
        var where = harness.contentMiddle();

        harness.wheelOverTheKnob(2);

        assertEquals(50.0, harness.gain.get(), 1e-9, "a disabled knob turned");
        assertTrue(
                harness.contentMiddle() < where - 1,
                "the disabled knob swallowed the wheel; the content is still at " + harness.contentMiddle());
    }

    /// A live tree: rendered, laid out, and with a router holding the regions the
    /// paint produced.
    private final class Harness {

        /// The knob's value, held where a rebuild can read it back — an unbound
        /// knob would report every turn and then redraw at the value the markup
        /// gave it, so the second wheel of a test would start from the first
        /// one's beginning.
        private final Property<Double> gain;

        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final PointerRouter router = new PointerRouter();

        Harness(double value) {
            this(value, false);
        }

        Harness(double value, boolean disabled) {
            gain = Property.of(value);
            var rows = new ArrayList<Widget>();
            for (var i = 0; i < LEAD_ROWS; i++) {
                rows.add(new Text("lead " + i));
            }
            rows.add(new Knob(0, 100, value, STEP, 0, gain, gain::set, disabled, Attributes.NONE));
            for (var i = 0; i < ROWS; i++) {
                rows.add(new Text("row " + i));
            }
            target = TestFrames.of(200, VIEWPORT_HEIGHT, 1.0f, 0);
            renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
            tree = new ElementTree(
                    new Scroll(List.of(new Column(rows.toArray(Widget[]::new))), ScrollAxis.VERTICAL, Attributes.NONE));
            render = RenderTree.create();
            router.focusRoot(tree.root());
            frame();
            // Off the top, so the list has room in both directions. Aimed at the
            // bottom edge, which is rows rather than the knob -- a pre-scroll
            // that went through the control under test would be begging the
            // question.
            router.pointerWheel(100, VIEWPORT_HEIGHT - 4, 0, PRE_SCROLL, Modifiers.NONE);
            frame();
        }

        /// One frame: rebuild whatever went dirty, render, lay out, and hand the
        /// router the rectangles. Exactly what a window does.
        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        /// Turns the wheel over the **middle of the knob**, then paints.
        ///
        /// The position is read off the paint rather than assumed, because the
        /// whole test turns on the event reaching the knob first: a coordinate
        /// that missed it by two pixels would land on the viewport, scroll the
        /// list, and pass [KnobChainingTest#theEndOfTheTravelChains] for the
        /// wrong reason. The bounds check is the same worry stated once — the
        /// knob is inside the content, so a pre-scroll that grew would carry it
        /// off the top and the router would hand every event to the rows.
        void wheelOverTheKnob(float lines) {
            var knob = centreOf("knob");
            assertTrue(
                    knob[1] > 0 && knob[1] < VIEWPORT_HEIGHT,
                    "the knob was scrolled out of the viewport before the wheel, to y=" + knob[1]);
            router.pointerWheel(knob[0], knob[1], 0, lines, Modifiers.NONE);
            frame();
        }

        /// Where the middle of the content sits, read off the paint: an offset is
        /// only real if it reached the screen.
        double contentMiddle() {
            return centreOf("scroll-content")[1];
        }

        /// The centre of the one box of `type`, in window coordinates.
        private float[] centreOf(String type) {
            var found = new ArrayList<float[]>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element && type.equals(element.type())) {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    found.add(new float[] {
                        (float) (matrix.a() * layout.left()
                                + matrix.c() * layout.top()
                                + matrix.e()
                                + matrix.a() * layout.width() / 2),
                        (float) (matrix.b() * layout.left()
                                + matrix.d() * layout.top()
                                + matrix.f()
                                + matrix.d() * layout.height() / 2)
                    });
                }
            });
            assertEquals(1, found.size(), "expected exactly one " + type);
            return found.getFirst();
        }
    }
}
