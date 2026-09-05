package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §1.4's text scale, end to end: **the text grows and the boxes with fixed
/// metrics do not**, which is the condition §1.4 asks every component to survive
/// and the one an image of the gallery at 150% would be looking for
/// ([ADR-0267]).
///
/// The unit half is `TextScaleTest`. This is the half that says the factor
/// reaches a laid-out frame at all — a scale applied to a `Typography` nothing
/// shapes with would pass every assertion there and draw the same picture.
class TextScaleLayoutTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// Every text box's laid-out size, at `scale`.
    private static List<float[]> textBoxes(Widget root, double scale) {
        var target = TestFrames.of(400, 300, 1.0f, 0);
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get()).textScale(scale);
        var tree = new ElementTree(root);
        var found = new ArrayList<float[]>();
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            render.forEachPlacedBox(placed -> {
                if (placed.box().text() != null) {
                    found.add(new float[] {
                        placed.layout().width(), placed.layout().height()
                    });
                }
            });
        }
        return List.copyOf(found);
    }

    /// In a **row**, so the text box is the width of its content. In a `column`
    /// it would be stretched to the window and both scales would measure 400 —
    /// which is what the first version of this test asserted and why it says so
    /// here.
    @Test
    @DisplayName("a paragraph is shaped larger, so its box comes out wider and taller")
    void textGrows() {
        var root = new Row(new Text("Export the current selection"));

        var normal = textBoxes(root, 1.0).getFirst();
        var large = textBoxes(root, 1.5).getFirst();

        assertTrue(large[0] > normal[0] * 1.3, () -> "at 150% the text is " + large[0] + " wide against " + normal[0]);
    }

    /// The **height** is asserted through the font rather than through the box,
    /// and the reason is the same one that put the text in a row: a flex child is
    /// stretched on its cross axis, so a text box in a row is as tall as the row
    /// whatever its paragraph says. The width is the honest signal for a shaped
    /// paragraph; the line box is the honest signal for the type.
    @Test
    @DisplayName("and the line box grows with it, so lines do not overlap")
    void theLineBoxGrows() {
        var normal = TestFont.get()
                .of(new io.github.digitalsmile.goldberry.css.Typography(
                        "Inter", 13, io.github.digitalsmile.goldberry.assets.BundledFont.Weight.REGULAR, 18));
        var large = TestFont.get()
                .of(new io.github.digitalsmile.goldberry.css.Typography(
                                "Inter", 13, io.github.digitalsmile.goldberry.assets.BundledFont.Weight.REGULAR, 18)
                        .scaled(1.5));

        assertTrue(
                large.lineHeight() > normal.lineHeight() * 1.3,
                () -> "line height " + large.lineHeight() + " against " + normal.lineHeight());
    }

    @Test
    @DisplayName("and the default changes nothing at all, which is why no golden moved")
    void oneIsUnchanged() {
        var root = new Row(new Text("Export the current selection"));

        var implicitly = textBoxes(root, 1.0);
        var explicitly = new ArrayList<float[]>();
        var target = TestFrames.of(400, 300, 1.0f, 0);
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get());
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(new ElementTree(root)));
            render.forEachPlacedBox(placed -> {
                if (placed.box().text() != null) {
                    explicitly.add(new float[] {
                        placed.layout().width(), placed.layout().height()
                    });
                }
            });
        }

        assertEquals(implicitly.size(), explicitly.size());
        assertEquals(implicitly.getFirst()[0], explicitly.getFirst()[0], 1e-6);
        assertEquals(implicitly.getFirst()[1], explicitly.getFirst()[1], 1e-6);
    }

    /// **The box does not grow with it**, which is the whole of what §1.4 is
    /// asking components to survive: a `button` is `height: 32px` whatever its
    /// label says, so at 150% the text has to fit in a box that did not move.
    ///
    /// Asserted rather than assumed, because the *other* design — scaling inside
    /// the cascade — would have grown the padding too and hidden exactly this.
    @Test
    @DisplayName("a control's fixed height does not scale, which is the condition being tested for")
    void fixedMetricsDoNotScale() {
        var root = new Column(new io.github.digitalsmile.goldberry.widgets.controls.button.Button("Save"));

        var heights = new ArrayList<Float>();
        for (var scale : List.of(1.0, 1.5)) {
            var target = TestFrames.of(400, 300, 1.0f, 0);
            var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get()).textScale(scale);
            try (var render = RenderTree.create()) {
                render.update(target.frame(), renderer.render(new ElementTree(root)));
                render.forEachPlacedBox(placed -> {
                    var type = placed.box().owner() instanceof io.github.digitalsmile.goldberry.widget.Element element
                            ? element.type()
                            : null;
                    if ("button".equals(type)) {
                        heights.add(placed.layout().height());
                    }
                });
            }
        }

        assertEquals(2, heights.size(), "one button per scale");
        assertEquals(heights.get(0), heights.get(1), 0.01f, "§3 pins a button at 32 and a text scale is not a zoom");
    }
}
