package io.github.digitalsmile.goldberry.widgets.panel.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// §3.1's "column resize: 1:1, like `split-pane`'s drag" ([ADR-0361]).
///
/// The widths are the application's: this test is that application, applying
/// every width the table asks for and rebuilding.
class TableResizeTest {

    private static final int WIDTH = 400;

    private record Person(String id, String name, String realm) {}

    private static final List<Person> PEOPLE =
            List.of(new Person("f", "Frodo", "The Shire"), new Person("a", "Aragorn", "Arnor"));

    private final Map<String, Double> widths = new TreeMap<>();
    private final List<Double> asked = new ArrayList<>();
    private final List<String> sorted = new ArrayList<>();

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

    private Table<Person> table() {
        var name = Column.of("name", "Name", Person::name).sortable(true).resizable(true);
        var named = widths.containsKey("name") ? name.fixed(widths.get("name")) : name;
        return new Table<>(PEOPLE, Person::id, List.of(named, Column.of("realm", "Realm", Person::realm)))
                .sorted(null, sort -> sorted.add(sort.column()))
                .resized((column, width) -> {
                    asked.add(width);
                    widths.put(column, width);
                    tree.update(table());
                });
    }

    private void open() {
        target = TestFrames.of(WIDTH, 160, 1.0f, 0);
        renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        tree = new ElementTree(table());
        render = RenderTree.create();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, WIDTH, 160));
        frame();
        frame();
    }

    private void frame() {
        tree.flush();
        render.update(target.frame(), renderer.render(tree));
        router.updateRegions(HitTest.capture(render));
    }

    /// The painted rectangle of the first node of `type`.
    private float[] rect(String type) {
        var found = new ArrayList<float[]>();
        render.forEachPlacedBox(placed -> {
            if (placed.box().owner() instanceof Element element && type.equals(element.type())) {
                var matrix = placed.transform();
                var layout = placed.layout();
                found.add(new float[] {
                    (float) (matrix.a() * layout.left() + matrix.c() * layout.top() + matrix.e()),
                    (float) (matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f()),
                    layout.width(),
                    layout.height()
                });
            }
        });
        return found.getFirst();
    }

    @Test
    @DisplayName("a resizable header has a grip at its trailing edge, and a plain one does not")
    void grip() {
        open();
        var header = rect("table-header");
        var grip = rect("table-grip");

        assertEquals(header[0] + header[2], grip[0] + grip[2], 0.5f, "the grip ends where the header does");
        assertEquals(6, grip[2], 0.5f);
    }

    @Test
    @DisplayName("dragging the grip asks for the width it started at plus the travel, 1:1")
    void dragIsOneToOne() {
        open();
        var header = rect("table-header");
        var start = header[2];
        var x = header[0] + header[2] - 3;
        var y = header[1] + header[3] / 2;

        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        frame();
        router.pointerMoved(x + 40, y);
        frame();
        router.pointerMoved(x + 25, y);
        frame();
        router.pointerReleased(x + 25, y, PointerEvent.Button.PRIMARY, 1);
        frame();
        frame();

        assertEquals(start + 40, asked.get(0), 0.5);
        assertEquals(start + 25, asked.getLast(), 0.5, "measured from the press, not from the last move");
        assertEquals(start + 25, rect("table-header")[2], 0.5f, "the application's width is what is drawn");
        assertTrue(sorted.isEmpty(), "a drag on the grip sorted the column: " + sorted);
    }

    @Test
    @DisplayName("a column cannot be dragged shut")
    void minimum() {
        open();
        var header = rect("table-header");
        var x = header[0] + header[2] - 3;
        var y = header[1] + header[3] / 2;

        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        router.pointerMoved(x - 1000, y);
        router.pointerReleased(x - 1000, y, PointerEvent.Button.PRIMARY, 1);

        assertEquals(TableHead.MINIMUM_WIDTH, asked.getLast(), 0.01);
    }

    @Test
    @DisplayName("a click on the rest of the header still sorts")
    void theLabelStillSorts() {
        open();
        var header = rect("table-header");
        var x = header[0] + 20;
        var y = header[1] + header[3] / 2;

        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1);

        assertEquals(List.of("name"), sorted);
        assertTrue(asked.isEmpty());
    }
}
