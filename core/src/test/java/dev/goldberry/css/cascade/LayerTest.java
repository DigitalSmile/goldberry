package dev.goldberry.css.cascade;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.TestElement;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;

/// `@layer` inside the application layer.
class LayerTest {

    private static String colour(String css) {
        return colour(new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css))), element("x.y"));
    }

    private static String colour(StyleResolver resolver, TestElement element) {
        var value = resolver.resolve(element).get("color");
        return value == null ? "(none)" : value.getFirst().cssText();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                // A later layer wins at equal specificity, by the order first named.
                "@layer base, theme; @layer theme { x { color: red } } @layer base { x { color: blue } }      | red",
                // A rule in no layer wins over every layered one.
                "x { color: green } @layer a { x { color: red } }                                           | green",
                // Specificity still decides first, as the four layers' order does.
                "@layer top { x.y { color: red } } x { color: green }                                       | red",
                // !important turns the order round.
                "@layer a { x { color: red !important } } x { color: green !important }                     | red",
                "@layer a, b; @layer a { x { color: red !important } } @layer b { x { color: blue !important } } | red",
                // A layer's own rules rank above its sublayers.
                "@layer a { x { color: red } @layer b { x { color: blue } } }                               | red",
                // An unnamed layer is one of its own, ranked where it stands.
                "@layer { x { color: red } } @layer { x { color: blue } }                                   | blue",
                // revert-layer rolls back to the layer below.
                "@layer a { x { color: red } } @layer b { x { color: blue } x { color: revert-layer } }     | red",
            })
    @DisplayName("orders the application's rules")
    void orders(String css, String expected) {
        assertEquals(expected, colour(css));
    }

    @Test
    @DisplayName("a layer outside the application layer is a rule in none")
    void onlyTheApplicationsLayers() {
        var theme = Stylesheet.parse(CascadeLayer.THEME, "@layer late { x { color: red } } x { color: blue }");
        assertEquals("blue", colour(new StyleResolver(List.of(theme)), element("x")));
    }

    @Test
    @DisplayName("ranks nested layers below the layer that holds them, by the order first met")
    void ranks() {
        assertEquals(Map.of("a.b", 0, "a", 1, "c", 2), StyleResolver.ranks(List.of("a.b", "c", "a")));
        assertEquals(Map.of("a", 0, "b", 1), StyleResolver.ranks(List.of("a", "b", "a")));
    }

    @Test
    @DisplayName("an import into a layer puts its rules there")
    void importIntoLayer() {
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, LayerTest.class, "../layers/app.css");
        assertEquals("vendor", sheet.rules().getFirst().layer());
        assertTrue(sheet.layers().contains("vendor"));
        var resolver = new StyleResolver(List.of(sheet));
        assertEquals("green", colour(resolver, element("x")), "the importer's own rule is in no layer");
        assertEquals(
                "1", resolver.resolve(element("x")).get("padding").getFirst().text());
    }

    @Test
    @DisplayName("refuses a name that is not one, and a block of two layers")
    void malformed() {
        assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet("@layer 1a { }", ParseMode.STRICT, "t"));
        assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet("@layer a, b { }", ParseMode.STRICT, "t"));
        assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet("@layer;", ParseMode.STRICT, "t"));
    }
}
