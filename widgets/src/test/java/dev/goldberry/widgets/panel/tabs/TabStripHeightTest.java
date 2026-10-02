package dev.goldberry.widgets.panel.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;

/// A tab strip is one control height, whatever is in it.
///
/// The header row used to take its height from the tallest thing in it, which is
/// a `tab` while there are tabs and the `+` button when the last one is closed.
/// So closing every tab shrank the strip and dropped the `+` to the top of a row
/// that was no longer as tall as it — the one state where a header row has
/// nothing to take its height from.
///
/// It was also wrong with tabs in it, by two pixels, which is why the gallery's
/// goldens moved: the row was 30 where its tabs are 32.
class TabStripHeightTest {

    private static final float CONTROL_HEIGHT = 32;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// The laid-out height of the first node of `type`.
    private static Optional<Float> heightOf(Widget root, String type) {
        var target = TestFrames.of(400, 160, 1.0f, 0);
        var tree = new ElementTree(root);
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            var found = new java.util.ArrayList<Float>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element && type.equals(element.type())) {
                    found.add(placed.layout().height());
                }
            });
            return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
        }
    }

    private static Tabs tabs(Widget... children) {
        return new Tabs("a", List.of(children), null, null, null, () -> {}, Attributes.NONE);
    }

    @Test
    @DisplayName("a strip with tabs is exactly one control tall")
    void withTabs() {
        assertEquals(
                CONTROL_HEIGHT,
                heightOf(tabs(new Tab("a", "Alpha"), new Tab("b", "Beta")), "tab-list")
                        .orElseThrow(),
                0.5f);
    }

    /// **The reported defect.** Close the last tab and the row still has to be a
    /// row: the `+` is 24 square by design, and a strip that shrank to it would
    /// move every pixel below it and leave the button sitting high.
    @Test
    @DisplayName("a strip with no tabs left is still one control tall")
    void withNoTabs() {
        assertEquals(CONTROL_HEIGHT, heightOf(tabs(), "tab-list").orElseThrow(), 0.5f);
    }

    @Test
    @DisplayName("and the `+` is centred in it rather than parked at the top")
    void plusIsCentred() {
        var target = TestFrames.of(400, 160, 1.0f, 0);
        var tree = new ElementTree(tabs());
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            var top = new java.util.ArrayList<Float>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element && "tab-new".equals(element.type())) {
                    top.add(placed.layout().top());
                }
            });
            // 24 square in a 32 row, centred: 4px of slack at each end.
            assertEquals(4.0f, top.getFirst(), 0.5f);
        }
    }
}
