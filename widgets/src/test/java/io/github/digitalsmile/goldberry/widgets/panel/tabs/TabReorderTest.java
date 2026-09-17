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
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
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

/// Dragging a tab to a new place asks the application to move it ([ADR-0372]).
class TabReorderTest {

    private TestFrames.Target target;
    private RenderTree render;
    private ElementTree tree;
    private WidgetRenderer renderer;
    private final PointerRouter router = new PointerRouter();
    private final List<String> reordered = new ArrayList<>();
    private final List<String> selected = new ArrayList<>();

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

    private Tabs strip() {
        var children = new ArrayList<Widget>();
        for (var i = 0; i < 4; i++) {
            children.add(new Tab("t" + i, "Chapter " + i));
        }
        return new Tabs("t0", children, null, selected::add, null, null, Attributes.NONE)
                .onReorder((value, index) -> reordered.add(value + "->" + index));
    }

    private void open() {
        target = TestFrames.of(800, 120, 1.0f, 0);
        renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        tree = new ElementTree(strip());
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

    /// The painted rectangle of the tab with `value`: left, top, width, height.
    private float[] tab(String value) {
        var found = new float[4];
        render.forEachPlacedBox(placed -> {
            if (placed.box().owner() instanceof Element element
                    && "tab".equals(element.type())
                    && ((Tab) element.widget()).value().equals(value)) {
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
    @DisplayName("a tab dragged past two others asks to move to index 2, and follows the pointer on the way")
    void dragAsks() {
        open();
        var first = tab("t0");
        var third = tab("t2");
        var x = first[0] + first[2] / 2;
        var y = first[1] + first[3] / 2;
        var target = third[0] + third[2] * 0.75f;

        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        router.pointerMoved(x + 20, y);
        frame();
        var dragged = tab("t0")[0];
        router.pointerMoved(target, y);
        frame();
        router.pointerReleased(target, y, PointerEvent.Button.PRIMARY, 1);
        frame();

        assertEquals(first[0] + 20, dragged, 1.0f, "the tab is drawn where the pointer took it");
        assertEquals(List.of("t0->2"), reordered);
        assertEquals(first[0], tab("t0")[0], 1.0f, "and back in its slot once dropped, until the application moves it");
    }

    @Test
    @DisplayName("a click that does not travel selects and does not reorder")
    void clickSelects() {
        open();
        var second = tab("t1");
        var x = second[0] + second[2] / 2;
        var y = second[1] + second[3] / 2;

        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        router.pointerMoved(x + 2, y);
        router.pointerReleased(x + 2, y, PointerEvent.Button.PRIMARY, 1);

        assertTrue(reordered.isEmpty());
        assertEquals(List.of("t1"), selected);
    }

    @Test
    @DisplayName("dropped where it started, nothing is asked")
    void noMoveNoAsk() {
        open();
        var first = tab("t0");
        var x = first[0] + first[2] / 2;
        var y = first[1] + first[3] / 2;

        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        router.pointerMoved(x + 10, y);
        frame();
        router.pointerReleased(x + 10, y, PointerEvent.Button.PRIMARY, 1);

        assertTrue(reordered.isEmpty());
    }
}
