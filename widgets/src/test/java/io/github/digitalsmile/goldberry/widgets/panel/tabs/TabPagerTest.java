package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// A tab strip wider than its window has a chevron at each end ([ADR-0365]).
class TabPagerTest {

    private TestFrames.Target target;
    private RenderTree render;
    private ElementTree tree;
    private WidgetRenderer renderer;
    private final PointerRouter router = new PointerRouter();
    private final Clock.Virtual clock = Clock.virtual();

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

    private void open(Widget root, int width) {
        target = TestFrames.of(width, 120, 1.0f, 0);
        renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                .clock(clock);
        tree = new ElementTree(root);
        render = RenderTree.create();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, width, 120));
        for (var i = 0; i < 4; i++) {
            frame();
        }
    }

    private void frame() {
        tree.flush();
        render.update(target.frame(), renderer.render(tree));
        router.updateRegions(HitTest.capture(render));
        clock.advance(100);
    }

    private static Tabs tabs(int count) {
        var children = new ArrayList<Widget>();
        for (var i = 0; i < count; i++) {
            children.add(new Tab("t" + i, "Chapter " + i));
        }
        return new Tabs("t0", children, null, null, null, null, Attributes.NONE);
    }

    private List<Element> pagers() {
        var found = new ArrayList<Element>();
        collect(tree.root(), found);
        return found;
    }

    private static void collect(Element from, List<Element> into) {
        if ("tab-pager".equals(from.type())) {
            into.add(from);
        }
        for (var child : from.children()) {
            collect(child, into);
        }
    }

    private float[] rect(Element element) {
        var found = new float[4];
        render.forEachPlacedBox(placed -> {
            if (placed.box().owner() == element) {
                var m = placed.transform();
                var l = placed.layout();
                found[0] = (float) (m.a() * l.left() + m.c() * l.top() + m.e());
                found[1] = (float) (m.b() * l.left() + m.d() * l.top() + m.f());
                found[2] = l.width();
                found[3] = l.height();
            }
        });
        return found;
    }

    @Test
    @DisplayName("a strip whose tabs fit has no pagers")
    void fits() {
        open(tabs(2), 600);

        assertTrue(pagers().isEmpty());
    }

    @Test
    @DisplayName("an overflowing strip has one at each end, and the start is disabled at the start")
    void overflowing() {
        open(tabs(12), 300);
        var pagers = pagers();

        assertEquals(2, pagers.size());
        assertTrue(((TabPager) pagers.get(0).widget()).isDisabled(), "nothing before the first tab");
        assertFalse(((TabPager) pagers.get(1).widget()).isDisabled());
    }

    @Test
    @DisplayName("pressing the end pager pages the strip, and the start pager comes on")
    void pages() {
        open(tabs(12), 300);
        var end = rect(pagers().get(1));
        var x = end[0] + end[2] / 2;
        var y = end[1] + end[3] / 2;

        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1);
        for (var i = 0; i < 5; i++) {
            frame();
        }

        assertFalse(((TabPager) pagers().get(0).widget()).isDisabled(), "the strip moved away from its start");
    }
}
