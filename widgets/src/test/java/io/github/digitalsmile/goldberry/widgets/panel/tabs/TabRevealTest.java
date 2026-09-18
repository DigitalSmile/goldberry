package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// A tab the strip has scrolled past is brought back when it is selected —
/// [ADR-0120].
///
/// The ADR describes a pending reveal "set when the selection changes and
/// cleared the moment it has been acted on", and only the second half was ever
/// written: `TabsState` held the field, compared every located header against it
/// and cleared it, and nothing ever put a value in it. So a strip of twelve
/// chapters in a narrow window answered `Ctrl+Tab` — or a bound value the
/// application moved — by selecting a header the reader could not see, which is
/// the whole of what this widget's scroll controller exists to prevent.
///
/// What is asserted is where the header was **painted**, because a reveal is
/// only a reveal on screen: an offset on a controller that never reached the
/// viewport would satisfy any test written against the state.
class TabRevealTest {

    private static final int WIDTH = 300;

    private TestFrames.Target target;
    private RenderTree render;
    private ElementTree tree;
    private WidgetRenderer renderer;
    private Clock.Virtual clock;
    private final PointerRouter router = new PointerRouter();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        if (render != null) {
            render.close();
        }
        if (target != null) {
            target.end();
        }
    }

    /// An application in miniature, as [TabTravelTest] has: it owns the
    /// selection, because the strip asks and never decides (ADR-0063).
    private record Harness() implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new HarnessState();
        }
    }

    private static final class HarnessState extends State<Harness> {

        private String selected = "t0";

        void select(String value) {
            setState(() -> selected = value);
        }

        @Override
        public Widget build(BuildContext context) {
            var children = new ArrayList<Widget>();
            // Twelve of them in three hundred pixels: the far end is a long way
            // outside the viewport, so a test cannot pass by scrolling a little.
            for (var i = 0; i < 12; i++) {
                children.add(new Tab("t" + i, "Chapter " + i));
            }
            return new Tabs(selected, children, null, this::select, null, null, Attributes.NONE);
        }
    }

    private HarnessState application() {
        return (HarnessState) tree.root().state().orElseThrow();
    }

    private void open() {
        target = TestFrames.of(WIDTH, 120, 1.0f, 0);
        clock = Clock.virtual();
        renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                .clock(clock);
        tree = new ElementTree(new Harness());
        render = RenderTree.create();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, WIDTH, 120));
        settle();
    }

    /// A frame, and the router walk that tells a [Tab] where it was painted —
    /// which is the only way a reveal is ever asked for.
    private void frame() {
        tree.flush();
        render.update(target.frame(), renderer.render(tree));
        router.updateRegions(HitTest.capture(render));
        clock.advance(100);
    }

    /// Enough frames for a reveal to be measured, acted on, and for the glide it
    /// starts to land — a programmatic scroll slides rather than jumping
    /// (ADR-0363).
    private void settle() {
        for (var i = 0; i < 8; i++) {
            frame();
        }
    }

    /// Where the header for `value` was painted: its left edge in the window,
    /// and its width — the transform included, since a scrolled viewport is a
    /// translation and the whole question here is whether it moved.
    private float[] headerRect(String value) {
        var found = new float[] {Float.NaN, Float.NaN};
        render.forEachPlacedBox(placed -> {
            if (placed.box().owner() instanceof Element element
                    && "tab".equals(element.type())
                    && element.widget() instanceof Tab tab
                    && tab.value().equals(value)) {
                var m = placed.transform();
                found[0] = (float) (m.a() * placed.layout().left() + m.e());
                found[1] = placed.layout().width();
            }
        });
        return found;
    }

    @Test
    @DisplayName("selecting a tab the strip has scrolled past scrolls it back into view")
    void revealsTheSelection() {
        open();
        var before = headerRect("t11");
        assertTrue(
                before[0] > WIDTH,
                "the last of twelve chapters starts outside a 300px window, or this proves nothing: it was at "
                        + before[0]);

        application().select("t11");
        settle();

        var after = headerRect("t11");
        assertTrue(after[0] >= 0, "the newly selected header is not off the left edge either: it is at " + after[0]);
        assertTrue(
                after[0] + after[1] <= WIDTH + 0.5,
                "the tab that was selected is inside the window, which is what a reveal means: it ends at "
                        + (after[0] + after[1]));
    }

    @Test
    @DisplayName("and the tab it came from goes out, because a reveal moves the strip and not the tab")
    void scrollsTheStripRatherThanTheTab() {
        open();
        var firstBefore = headerRect("t0");

        application().select("t11");
        settle();

        var firstAfter = headerRect("t0");
        assertTrue(
                firstAfter[0] < firstBefore[0], "every header moved left by the same distance, the first one included");
        assertEquals(firstBefore[1], firstAfter[1], 0.5, "and none of them was resized to make room");
    }

    @Test
    @DisplayName("a selection already on screen leaves the strip where the reader put it")
    void doesNotScrollForAVisibleTab() {
        open();
        var before = headerRect("t1");
        assertTrue(before[0] < WIDTH, "the second chapter is visible from the start");

        application().select("t1");
        settle();

        assertEquals(
                before[0],
                headerRect("t1")[0],
                0.5,
                "a reveal scrolls the least it can, and the least for a visible tab is nothing");
    }
}
