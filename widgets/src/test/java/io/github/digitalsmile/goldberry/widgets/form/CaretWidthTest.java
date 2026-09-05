package io.github.digitalsmile.goldberry.widgets.form;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.form.textarea.TextArea;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;

/// `--gb-caret-width` — the same number for both controls that draw a caret
/// ([ADR-0253]).
///
/// A caret's box is set by the field **after** the cascade, so a
/// `caret { width: 3px }` would be overwritten rather than honoured. Read as a
/// custom property it resizes the caret, which is what an author writing one
/// meant — and a thicker caret is a real low-vision aid rather than a
/// preference, which is why §13 lists that kind of switch.
class CaretWidthTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// Every painted box's width, so the caret can be found by its size rather
    /// than by counting children — which changes with the anatomy.
    private static List<Double> widthsOf(Widget widget, String css) {
        var sheets = new ArrayList<>(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()));
        if (!css.isEmpty()) {
            sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
        }
        var tree = new ElementTree(widget);
        tree.flush();
        var root = new WidgetRenderer(sheets, TestFont.get()).render(tree);
        var out = new ArrayList<Double>();
        collect(root, out);
        return out;
    }

    private static void collect(Box box, List<Double> out) {
        if (box.width() instanceof StyleLength.Points points) {
            out.add((double) points.value());
        }
        box.children().forEach(child -> collect(child, out));
    }

    @Test
    @DisplayName("a field's caret is one pixel when nothing says otherwise")
    void theDefault() {
        assertEquals(
                1,
                widthsOf(new TextInput("hi", value -> {}), "").stream()
                        .filter(w -> w == 1)
                        .count(),
                "exactly one box should be the caret's default width");
    }

    @Test
    @DisplayName("and an application that asks for a fat one gets it")
    void overridden() {
        var widths = widthsOf(new TextInput("hi", value -> {}), "text-input { --gb-caret-width: 3px }");

        assertEquals(1, widths.stream().filter(w -> w == 3).count(), () -> "no 3px box among " + widths);
        assertEquals(0, widths.stream().filter(w -> w == 1).count(), () -> "the 1px caret is still there: " + widths);
    }

    /// The two controls are in different packages and each had its own copy of
    /// the number, the second carrying a comment saying it was the first's. They
    /// read one constant now, so this is the assertion that says so.
    @Test
    @DisplayName("a text-area's caret is the same number, from the same token")
    void theAreaAgrees() {
        var widths = widthsOf(new TextArea("hi", value -> {}), "text-area { --gb-caret-width: 3px }");

        assertEquals(1, widths.stream().filter(w -> w == 3).count(), () -> "no 3px box among " + widths);
    }

    @Test
    @DisplayName("and both default to the one number")
    void bothDefault() {
        assertEquals(Carets.WIDTH, 1);
        assertEquals("--gb-caret-width", Carets.WIDTH_TOKEN);
    }
}
