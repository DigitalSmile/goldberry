package dev.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;

/// A field's room is its width less **each** padding edge, not twice the left
/// one.
///
/// `text-area` wrapped wider than its room for the same arithmetic.
/// A field does not wrap, so there the error was a caret
/// scrolled into view a few pixels late: the end of a long value sat under the
/// right padding, where the clip cut it off.
class AsymmetricPaddingTest {

    private static final String LONG = "the road goes ever on and on, down from the door where it began";

    private final TestHost host = new TestHost();

    private final WidgetRenderer renderer = new WidgetRenderer(
            List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                            text-input.even { padding: 0 4px }
                            text-input.uneven { padding: 0 16px 0 4px }
                            """)),
            TestFont.get());

    private double scrolledWith(String padding) {
        var tree = new ElementTree(new TextInput(LONG, null).withAttributes(Attributes.NONE.classes(padding)), host);
        render(tree);
        var field = (TextField) tree.root().children().getFirst().widget();
        field.measured(new Extent(200, 32), new Extent(200, 32));
        // Focused, because an untouched field no longer chases its caret at all
        // — and what this measures is the chase: how much room the
        // caret is given at the trailing edge, which is where the right padding is.
        field.onFocusChanged(true, false);
        render(tree);
        return ((TextInputState) tree.root().state().orElseThrow()).scrolledBy();
    }

    private void render(ElementTree tree) {
        tree.flush();
        renderer.render(tree);
    }

    @Test
    @DisplayName("twelve more pixels of right padding scroll the end of the value twelve pixels further")
    void theRightPaddingComesOff() {
        assertEquals(12, scrolledWith("uneven") - scrolledWith("even"), 1e-6);
    }
}
