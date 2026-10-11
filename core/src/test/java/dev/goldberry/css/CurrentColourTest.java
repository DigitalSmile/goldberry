package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.colour.CurrentColour;

/// `currentcolor` is the element's own colour, wherever a colour is written.
class CurrentColourTest {

    private static ComputedStyle child(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var root = element("window");
        root.with(element("button"));
        var resolver = new StyleResolver(List.of(sheet));
        var parent = ComputedStyle.of(resolver.resolve(root), CssLength.Context.DEFAULT, null);
        return ComputedStyle.of(resolver.resolve(root.descend(1)), CssLength.Context.DEFAULT, parent);
    }

    @Test
    @DisplayName("a border, an outline and a shadow take the element's own colour")
    void ownColour() {
        var style = child("button { border: 1px solid currentcolor; outline-color: currentColor;"
                + " box-shadow: 0 1px 2px currentcolor; color: #102030 }");
        var border = style.decoration().border();
        assertEquals(0xFF102030, border.top().argb());
        assertEquals(0xFF102030, border.left().argb());
        assertEquals(0xFF102030, style.decoration().outlineColor());
        assertEquals(0xFF102030, style.decoration().shadows().getFirst().argb());
    }

    @Test
    @DisplayName("a colour the element inherits is its own colour too")
    void inheritedColour() {
        var style = child("window { color: #405060 } button { border-color: currentcolor }");
        assertEquals(0xFF405060, style.decoration().border().top().argb());
    }

    @Test
    @DisplayName("in color itself it is the inherited colour")
    void inColour() {
        assertEquals(
                0xFF405060,
                child("window { color: #405060 } button { color: currentcolor }")
                        .color());
    }

    @Test
    @DisplayName("works inside a gradient")
    void inAGradient() {
        var style = child("button { color: #ff0000; background: linear-gradient(currentcolor, blue) }");
        assertTrue(style.fill().hasInk());
    }

    @Test
    @DisplayName("a keyframe reads the colour the element has")
    void keyframe() {
        var base = child("button { color: #00ff00 }");
        var tokens = CssTokenizer.tokenize("currentcolor").stream()
                .filter(t -> !t.is(TokenType.EOF))
                .toList();
        var framed = base.applied(Map.of("background-color", tokens), CssLength.Context.DEFAULT);
        assertEquals(0xFF00FF00, framed.fill().colour());
    }

    @Test
    @DisplayName("a value without it is handed back as it is")
    void untouched() {
        var tokens = CssTokenizer.tokenize("1px solid red");
        assertSame(tokens, CurrentColour.substitute(tokens, 0xFF000000));
    }
}
