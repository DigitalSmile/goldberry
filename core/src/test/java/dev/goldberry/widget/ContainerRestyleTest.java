package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The cascade's second run for `@container`: a frame lays out, the renderer
/// records each container's size and restyles under the ones that changed,
/// and the frame is laid out once more.
class ContainerRestyleTest {

    private record Node(String name, List<Widget> children) implements Widget.Leaf, Styled, Paints {

        Node(String name, Widget... children) {
            this(name, List.of(children));
        }

        @Override
        public String cssType() {
            return "node";
        }

        /// The space-separated classes `name` lists.
        @Override
        public Set<String> classes() {
            return Set.of(name.split(" "));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    private static final String SHEET = """
            .page { width: 400px; height: 100px; align-items: flex-start }
            .card { container-type: inline-size; padding: 0 10px; border: 1px solid red }
            .narrow { width: 200px }
            .wide { width: 360px }
            .inner { width: 20px; height: 20px; opacity: 1 }
            @container (min-width: 300px) { .inner { opacity: 0.5 } }
            .sized { width: 10cqw; height: 10px }
            """;

    private Font font;
    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
        target = TestFrames.of(400, 100, 1f);
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

    /// One frame as `FrameSequence` lays it out: render, lay out, and once more
    /// when the containers settle something.
    private Box frame(ElementTree tree, WidgetRenderer renderer, RenderTree render) {
        var boxes = renderer.render(tree);
        render.update(target.frame(), boxes);
        if (renderer.settleContainers(render)) {
            boxes = renderer.render(tree);
            render.update(target.frame(), boxes);
        }
        return boxes;
    }

    private static Box inner(Box root) {
        return root.children().getFirst().children().getFirst();
    }

    @Test
    @DisplayName("a container wider than the query restyles what is in it within the first frame")
    void wideContainer() {
        var tree = new ElementTree(new Node("page", new Node("card wide", new Node("inner"))));
        var renderer = new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET)), font);
        try (var render = RenderTree.create()) {
            assertEquals(0.5, inner(frame(tree, renderer, render)).opacity(), 1e-9);
            // Settled: the next frame lays out once.
            renderer.render(tree);
            assertFalse(renderer.settleContainers(render), "nothing changed, so nothing is restyled");
        }
    }

    @Test
    @DisplayName("a narrower one does not, and the query reads the content box, inside the border and padding")
    void narrowContainer() {
        var tree = new ElementTree(new Node("page", new Node("card narrow", new Node("inner"))));
        var renderer = new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET)), font);
        try (var render = RenderTree.create()) {
            assertEquals(1, inner(frame(tree, renderer, render)).opacity(), 1e-9);
            var card = tree.root().children().getFirst();
            assertTrue(card.isQueryContainer());
            assertEquals(200 - 2 - 20, card.queryContainer().width(), 1e-9);
        }
    }

    @Test
    @DisplayName("a container unit is a share of the container's content box")
    void containerUnits() {
        var tree = new ElementTree(new Node("page", new Node("card wide", new Node("sized"))));
        var renderer = new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET)), font);
        try (var render = RenderTree.create()) {
            frame(tree, renderer, render);
            var widths = new ArrayList<Float>();
            render.forEachPlacedBox(placed -> widths.add(placed.layout().width()));
            // Yoga rounds to whole pixels at 1x.
            assertEquals((360 - 2 - 20) * 0.1, widths.get(2), 0.5);
        }
    }
}
