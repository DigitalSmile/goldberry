package io.github.digitalsmile.goldberry.widgets.core.scroll;

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
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.Scrollbars;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §2.4's "always show scroll bars": a reserved gutter, layout rather than
/// overlay ([ADR-0364]).
class ScrollGutterTest {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 120;

    private TestFrames.Target target;
    private RenderTree render;

    /// The viewport's box as the renderer described it on the last frame: its
    /// content, then its bar. Read here rather than off the render tree, which
    /// lifts an opacity into a layer.
    private Box viewportBox;

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

    /// Paints a tall document under `scrollbars` for a few frames and hands back
    /// the placed boxes of the last one, by CSS type.
    private List<Placed> paint(Scrollbars scrollbars) {
        target = TestFrames.of(WIDTH, HEIGHT, 1.0f, 0);
        var renderer =
                new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR, scrollbars), TestFont.get());
        var rows = new ArrayList<Widget>();
        for (var i = 0; i < 30; i++) {
            rows.add(new Text("Row " + i, Attributes.NONE.id("row" + i)));
        }
        var tree = new ElementTree(
                new Scroll(List.of(new Column(rows.toArray(Widget[]::new))), ScrollAxis.VERTICAL, Attributes.NONE));
        render = RenderTree.create();
        var router = new PointerRouter();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, WIDTH, HEIGHT));
        for (var i = 0; i < 4; i++) {
            tree.flush();
            viewportBox = renderer.render(tree);
            render.update(target.frame(), viewportBox);
            router.updateRegions(HitTest.capture(render));
        }
        var placed = new ArrayList<Placed>();
        render.forEachPlacedBox(box -> {
            if (box.box().owner() instanceof Element element) {
                placed.add(new Placed(element.type(), box.box(), box.layout().width()));
            }
        });
        return placed;
    }

    private record Placed(String type, Box box, float width) {}

    private static Placed first(List<Placed> placed, String type) {
        return placed.stream().filter(p -> type.equals(p.type())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("overlay bars take no width from the content and are invisible at rest")
    void overlay() {
        var placed = paint(Scrollbars.OVERLAY);
        var column = placed.stream()
                .filter(p -> "column".equals(p.type()))
                .findFirst()
                .orElseThrow();

        assertEquals(WIDTH, column.width(), 0.5f);
        assertEquals(0, viewportBox.children().getLast().opacity(), 0.001);
    }

    @Test
    @DisplayName("always-shown bars reserve a 12px gutter beside the content and are drawn at rest")
    void always() {
        var placed = paint(Scrollbars.ALWAYS);
        var column = placed.stream()
                .filter(p -> "column".equals(p.type()))
                .findFirst()
                .orElseThrow();
        var bar = first(placed, "scrollbar");

        assertEquals(WIDTH - 12, column.width(), 0.5f, "the content gave the bar its gutter");
        assertEquals(12, bar.width(), 0.5f);
        assertEquals(1, viewportBox.children().getLast().opacity(), 0.001, "a reserved bar does not fade");
    }

    @Test
    @DisplayName("the setting is a token stylesheet after the density, and overlay adds none")
    void stylesheets() {
        var overlay = Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR, Scrollbars.OVERLAY);
        var always = Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR, Scrollbars.ALWAYS);

        assertEquals(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR).size(), overlay.size());
        assertEquals(overlay.size() + 1, always.size());
        assertTrue(Scrollbars.ALWAYS.source().contains("--gb-scrollbar-gutter: 12px"));
    }
}
