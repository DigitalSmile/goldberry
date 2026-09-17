package io.github.digitalsmile.goldberry.widgets.panel.table;

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
import io.github.digitalsmile.goldberry.input.key.Modifiers;
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
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// A table on a page that scrolls keeps its column names in view while it is
/// there, and lets them go with it ([ADR-0360]).
class TableStickyHeaderTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 160;

    private record Row(String id, String name) {}

    private TestFrames.Target target;
    private RenderTree render;
    private ElementTree tree;
    private WidgetRenderer renderer;
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

    private void open(Widget root) {
        target = TestFrames.of(WIDTH, HEIGHT, 1.0f, 0);
        renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        tree = new ElementTree(root);
        render = RenderTree.create();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, WIDTH, HEIGHT));
        frame();
        frame();
    }

    private void frame() {
        tree.flush();
        render.update(target.frame(), renderer.render(tree));
        router.updateRegions(HitTest.capture(render));
    }

    private void wheel(float lines) {
        router.pointerWheel(WIDTH / 2f, HEIGHT / 2f, 0, lines, Modifiers.NONE);
        frame();
        frame();
    }

    /// Where the header row is painted, on screen.
    private double headTop() {
        var found = new ArrayList<Double>();
        render.forEachPlacedBox(placed -> {
            if (placed.box().owner() instanceof Element element && "table-head".equals(element.type())) {
                var matrix = placed.transform();
                var layout = placed.layout();
                found.add(matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f());
            }
        });
        assertEquals(1, found.size());
        return found.getFirst();
    }

    /// A line of text, a forty-row table, and forty more lines under it.
    private static Widget page() {
        var rows = new ArrayList<Row>();
        for (var i = 0; i < 40; i++) {
            rows.add(new Row("r" + i, "Row " + i));
        }
        var table = new Table<>(rows, Row::id, List.of(Column.of("name", "Name", Row::name)));
        var content = new ArrayList<Widget>();
        content.add(new Text("Above the table"));
        content.add(table);
        for (var i = 0; i < 40; i++) {
            content.add(new Text("After " + i));
        }
        return new Scroll(
                // Qualified: this package has its own `Column`, a table column.
                List.of(new io.github.digitalsmile.goldberry.widgets.core.Column(content.toArray(Widget[]::new))),
                ScrollAxis.VERTICAL,
                Attributes.NONE);
    }

    @Test
    @DisplayName("the header rests under the line above it, and pins at the top once the rows scroll under it")
    void pins() {
        open(page());
        var resting = headTop();
        assertTrue(resting > 0, "the header starts below the first line: " + resting);

        wheel(6);

        assertEquals(0, headTop(), 1.0, "the header stayed in view while its rows scrolled");
    }

    @Test
    @DisplayName("once the table has scrolled away, its header goes with it")
    void leavesWithTheTable() {
        open(page());

        wheel(40);

        assertTrue(headTop() < -1, "the header outstayed its table: " + headTop());
    }
}
