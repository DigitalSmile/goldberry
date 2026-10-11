package dev.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;

/// A `text-area` with lines in both directions: each hard line is a paragraph,
/// and under `unicode-bidi: plaintext` each finds its own direction, which the
/// arrow keys follow.
class TextAreaBidiTest {

    /// Hebrew, "shalom".
    private static final String SHALOM = "שלום";

    private static final String PLAINTEXT = "text-area { unicode-bidi: plaintext }";

    private final TestHost host = new TestHost();

    private ElementTree mounted(TextArea area, @Nullable String css) {
        var tree = new ElementTree(area, host);
        render(tree, css);
        box(tree).measured(new Extent(300, 200), new Extent(300, 200));
        render(tree, css);
        return tree;
    }

    private void render(ElementTree tree, @Nullable String css) {
        tree.flush();
        var sheets = new ArrayList<Stylesheet>(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()));
        if (css != null) {
            sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
        }
        new WidgetRenderer(sheets, TestFont.get()).render(tree);
    }

    private TextAreaBox box(ElementTree tree) {
        return (TextAreaBox) tree.root().children().getFirst().widget();
    }

    private void key(ElementTree tree, Key key, @Nullable String css) {
        box(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null));
        render(tree, css);
    }

    @Test
    @DisplayName("under plaintext, Left reads on in the Hebrew line and goes back in the Latin one")
    void perLine() {
        var tree = mounted(new TextArea("abc\n" + SHALOM, null), PLAINTEXT);
        box(tree).onFocusChanged(true, true);
        render(tree, PLAINTEXT);

        key(tree, Key.DOWN, PLAINTEXT);
        key(tree, Key.HOME, PLAINTEXT);
        assertEquals(4, box(tree).edit().caret(), "the start of the Hebrew line");
        key(tree, Key.LEFT, PLAINTEXT);
        assertEquals(5, box(tree).edit().caret());

        key(tree, Key.UP, PLAINTEXT);
        key(tree, Key.END, PLAINTEXT);
        key(tree, Key.LEFT, PLAINTEXT);
        assertEquals(2, box(tree).edit().caret(), "the Latin line goes back");
    }

    @Test
    @DisplayName("without it the box's direction is every line's")
    void boxDirection() {
        var css = "text-area { direction: rtl }";
        var tree = mounted(new TextArea("abc", null), css);
        box(tree).onFocusChanged(true, true);
        render(tree, css);

        key(tree, Key.HOME, css);
        key(tree, Key.LEFT, css);
        assertEquals(1, box(tree).edit().caret(), "Latin in a right-to-left area still reads on with Left");
    }
}
