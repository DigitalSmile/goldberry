package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/// The underline travels from the tab that was selected to the one that is —
/// [ADR-0377].
///
/// §3.1 gives `tabs` and `segmented` the same effect, and `tabs` did not have
/// it: the underline faded out under one header and in under another, which is
/// two things happening in place rather than one thing moving. ADR-0097
/// deferred it for want of geometry; the geometry is the headers' own painted
/// rectangles, which the strip has kept since it learned to take a drag.
///
/// What is asserted is the displacement rather than a picture, because the
/// picture at rest is unchanged and that is the point: a strip painted with no
/// router behind it — which is what half the golden images are — still draws
/// exactly what it drew before.
class TabTravelTest {

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

    /// An application in miniature: it owns which tab is selected, because the
    /// strip asks and never decides (ADR-0063).
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
            // Deliberately of very different widths, so that the scale half of
            // the displacement is not 1 and a test cannot pass by ignoring it.
            children.add(new Tab("t0", "One"));
            children.add(new Tab("t1", "A considerably longer chapter"));
            children.add(new Tab("t2", "Three"));
            return new Tabs(selected, children, null, this::select, null, null, Attributes.NONE);
        }
    }

    private HarnessState application() {
        return (HarnessState) tree.root().state().orElseThrow();
    }

    private void open() {
        target = TestFrames.of(800, 120, 1.0f, 0);
        clock = Clock.virtual();
        renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                .clock(clock);
        tree = new ElementTree(new Harness());
        render = RenderTree.create();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, 800, 120));
        for (var i = 0; i < 3; i++) {
            frame();
        }
    }

    private void frame() {
        tree.flush();
        render.update(target.frame(), renderer.render(tree));
        router.updateRegions(HitTest.capture(render));
    }

    /// The journey the tab with `value` is carrying, or null when it is on none.
    private Tab.Travel travelOf(String value) {
        var found = new Tab.Travel[1];
        walk(tree.root(), element -> {
            if (element.widget() instanceof Tab tab && tab.value().equals(value)) {
                found[0] = tab.travel();
            }
        });
        return found[0];
    }

    /// Where the underline under the tab with `value` was actually painted —
    /// its left edge in the window, transform and transition included.
    ///
    /// The transform is applied about the box's own place, so the left edge is
    /// the laid-out one put through the matrix, which is what
    /// [TabReorderTest] does for a dragged header.
    private double underlineLeftOf(String value) {
        var found = new double[1];
        render.forEachPlacedBox(placed -> {
            if (placed.box().owner() instanceof Element element
                    && "tab-indicator".equals(element.type())
                    && element.parent() instanceof Element parent
                    && parent.widget() instanceof Tab tab
                    && tab.value().equals(value)) {
                var m = placed.transform();
                found[0] =
                        m.a() * placed.layout().left() + m.c() * placed.layout().top() + m.e();
            }
        });
        return found[0];
    }

    private static void walk(Element element, java.util.function.Consumer<Element> visitor) {
        visitor.accept(element);
        element.children().forEach(child -> walk(child, visitor));
    }

    private float[] tabRect(String value) {
        var found = new float[2];
        render.forEachPlacedBox(placed -> {
            if (placed.box().owner() instanceof Element element
                    && "tab".equals(element.type())
                    && ((Tab) element.widget()).value().equals(value)) {
                var m = placed.transform();
                found[0] = (float) (m.a() * placed.layout().left() + m.e());
                found[1] = placed.layout().width();
            }
        });
        return found;
    }

    @Test
    @DisplayName("a strip that has never changed its selection is on no journey")
    void atRest() {
        open();
        assertNull(travelOf("t0"), "the tab a strip opened on has nowhere to have come from");
        assertNull(travelOf("t1"));
    }

    @Test
    @DisplayName("selecting another tab displaces its underline back onto the old one")
    void displaced() {
        open();
        var from = tabRect("t0");
        var to = tabRect("t1");

        application().select("t1");
        frame();

        var travel = travelOf("t1");
        assertNotNull(travel, "the strip has both headers' rectangles, so it knows the distance");
        assertEquals(from[0] - to[0], travel.dx(), 0.01, "as far back as the old header was");
        assertEquals(from[1] / to[1], travel.scale(), 0.01, "and as much narrower as it was narrow");
        assertTrue(travel.isDisplaced());
        // The displacement is where the box is actually drawn, not merely a
        // number on a record: the underline under the newly selected header is
        // painted on top of the **old** one.
        assertEquals(from[0], underlineLeftOf("t1"), 0.5, "and that is where it is painted: over the old header");
    }

    @Test
    @DisplayName("and the next frame lets go, so it slides home rather than jumping")
    void relaxes() {
        open();
        application().select("t1");
        frame();
        var journey = travelOf("t1").id();

        // The frame after the displacement was drawn: the strip takes it back,
        // and because the id has not changed this is the *same* element changing
        // — which is what a transition needs (ADR-0065).
        frame();
        var relaxed = travelOf("t1");
        assertEquals(journey, relaxed.id(), "still the same journey, or the element would be rebuilt and snap");
        assertEquals(0, relaxed.dx(), "with nothing left to travel");
        assertEquals(1, relaxed.scale());

        // Mid-transition the underline is between the two headers, which is the
        // whole claim: it is neither where it was nor where it is going.
        var was = tabRect("t0")[0];
        var going = tabRect("t1")[0];
        clock.advance(80);
        frame();
        var half = underlineLeftOf("t1");
        assertTrue(half > was && half < going, "between the two headers: " + half);

        clock.advance(120);
        frame();
        assertEquals(going, underlineLeftOf("t1"), 0.5, "and then it is home, under the tab it belongs to");
    }

    @Test
    @DisplayName("a second change is a second journey, and therefore a new element")
    void journeysAreCounted() {
        open();
        application().select("t1");
        frame();
        var first = travelOf("t1").id();

        application().select("t2");
        frame();
        var second = travelOf("t2").id();

        assertTrue(second > first, "a displaced underline must be a newly built element, or it would glide backwards");
    }
}
