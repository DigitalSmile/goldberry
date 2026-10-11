package dev.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.select.Selector.PseudoClass;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widgets.Controls;

/// `::placeholder` and `::selection` select a field's parts, as the part
/// selectors they stand for do.
class PseudoElementPartsTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;

    /// The `text-input` node's three parts, `[selection, value, caret]`, styled
    /// under the toolkit's sheets and `css`.
    private static List<ComputedStyle> parts(TextInput field, String css) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
        var resolver = new StyleResolver(sheets);
        var node = new ElementTree(field).root().children().getFirst();
        return node.children().stream().map(part -> style(resolver, part)).toList();
    }

    private static ComputedStyle style(StyleResolver resolver, Element part) {
        return ComputedStyle.of(resolver.resolve(part), CssLength.Context.DEFAULT);
    }

    @Test
    @DisplayName("::placeholder colours an empty field's text, and not a filled one's")
    void placeholder() {
        var css = "text-input::placeholder { color: #ff0000 }";
        assertEquals(
                RED, parts(new TextInput().placeholder("Jane Doe"), css).get(1).color());
        assertNotEquals(
                RED,
                parts(new TextInput("Jane", _ -> {}).placeholder("Jane Doe"), css)
                        .get(1)
                        .color());
    }

    @Test
    @DisplayName("::placeholder alone reaches every field's")
    void placeholderAnywhere() {
        assertEquals(
                RED,
                parts(new TextInput().placeholder("x"), "::placeholder { color: #ff0000 }")
                        .get(1)
                        .color());
    }

    @Test
    @DisplayName("::selection paints the wash behind selected text")
    void selection() {
        assertEquals(
                GREEN,
                parts(new TextInput("Jane", _ -> {}), "text-input::selection { background: #00ff00 }")
                        .getFirst()
                        .background());
    }

    @Test
    @DisplayName("a state before the :: is the field's, as CSS reads it")
    void stateOfTheField() {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "text-input:hover::placeholder { color: #ff0000 }"));
        var resolver = new StyleResolver(sheets);
        var node = new ElementTree(new TextInput().placeholder("x"))
                .root()
                .children()
                .getFirst();
        var value = node.children().get(1);
        assertNotEquals(RED, style(resolver, value).color());

        node.setPseudoClass(PseudoClass.HOVER, true);
        assertEquals(RED, style(resolver, value).color());
    }
}
