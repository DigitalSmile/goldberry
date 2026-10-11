package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.property.GlobalKeyword;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;

/// `inherit`, `initial`, `unset`, `revert`, `revert-layer` and `all`.
class GlobalKeywordTest {

    /// A `window` with one `button` in it, each layer's sheet given in order:
    /// the toolkit's base, the theme, the application.
    private static ComputedStyle child(String base, String theme, String application) {
        var sheets = new ArrayList<Stylesheet>();
        sheets.add(Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, base));
        sheets.add(Stylesheet.parse(CascadeLayer.THEME, theme));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, application));
        var root = element("window");
        root.with(element("button"));
        var resolver = new StyleResolver(sheets);
        var parent = ComputedStyle.of(resolver.resolve(root), CssLength.Context.DEFAULT, null);
        return ComputedStyle.of(resolver.resolve(root.descend(1)), CssLength.Context.DEFAULT, parent);
    }

    private static ComputedStyle child(String application) {
        return child("", "", application);
    }

    private static final String PARENT = "window { padding: 1px 2px 3px 4px; color: #102030; opacity: 0.5 }\n";

    @Nested
    @DisplayName("inherit, initial and unset")
    class Values {

        @Test
        @DisplayName("inherit copies the parent's value, even of a property that does not inherit")
        void inherit() {
            var style = child(PARENT + "button { padding: inherit; opacity: inherit }");
            assertEquals(
                    new Insets(Length.points(1), Length.points(2), Length.points(3), Length.points(4)),
                    style.padding());
            assertEquals(0.5, style.opacity());
        }

        @Test
        @DisplayName("inherit on one side copies that side and leaves the others")
        void inheritOneSide() {
            var style = child(PARENT + "button { padding: 9px; padding-left: inherit }");
            assertEquals(Length.points(9), style.padding().top());
            assertEquals(Length.points(4), style.padding().left());
        }

        @Test
        @DisplayName("initial is the initial value, even of a property that inherits")
        void initial() {
            var style = child(PARENT + "button { color: initial }");
            assertEquals(ComputedStyle.INITIAL.color(), style.color());
        }

        @Test
        @DisplayName("unset inherits what inherits and resets what does not")
        void unset() {
            var style =
                    child(PARENT + "button { padding: 5px; color: #ffffff } button { padding: unset; color: unset }");
            assertEquals(ComputedStyle.INITIAL.padding(), style.padding());
            assertEquals(0xFF102030, style.color());
        }

        @Test
        @DisplayName("all resets every property, and all: inherit takes the parent whole")
        void all() {
            var reset = child(PARENT + "button { padding: 5px; border: 1px solid red; all: initial }");
            assertEquals(ComputedStyle.INITIAL, reset);
            var parent = child(PARENT + "button { all: inherit }");
            assertEquals(0.5, parent.opacity());
            assertEquals(0xFF102030, parent.color());
        }

        @Test
        @DisplayName("are recognised alone and in any case, and nowhere else")
        void recognised() {
            assertEquals(GlobalKeyword.REVERT_LAYER, GlobalKeyword.of(CssTokenizer.tokenize(" Revert-Layer ")));
            assertNull(GlobalKeyword.of(CssTokenizer.tokenize("inherit inherit")));
            assertNull(GlobalKeyword.of(CssTokenizer.tokenize("inherits")));
        }
    }

    @Nested
    @DisplayName("revert and revert-layer")
    class Reverts {

        private static final String BASE = "button { color: #aa0000 }";
        private static final String THEME = "button { color: #00aa00 }";

        @Test
        @DisplayName("revert-layer rolls back to the layer below")
        void revertLayer() {
            assertEquals(
                    0xFF00AA00,
                    child(BASE, THEME, "button { color: revert-layer }").color());
        }

        @Test
        @DisplayName("revert-layer through another revert-layer reaches further down")
        void chain() {
            assertEquals(
                    0xFFAA0000,
                    child(BASE, "button { color: revert-layer }", "button { color: revert-layer }")
                            .color());
        }

        @Test
        @DisplayName("revert rolls back to the toolkit's own sheets, past the theme")
        void revert() {
            assertEquals(
                    0xFFAA0000, child(BASE, THEME, "button { color: revert }").color());
        }

        @Test
        @DisplayName("with nothing to roll back to, the property is unset")
        void nothingBelow() {
            var style = child("", "", PARENT + "button { color: revert; padding: revert-layer }");
            assertEquals(0xFF102030, style.color(), "colour inherits");
            assertEquals(ComputedStyle.INITIAL.padding(), style.padding());
        }

        @Test
        @DisplayName("in the toolkit's own sheet, revert unsets")
        void revertInBase() {
            assertEquals(
                    ComputedStyle.INITIAL.color(),
                    child("button { color: #aa0000 } button { color: revert }", "", "")
                            .color());
        }
    }

    @Test
    @DisplayName("a resolver knows whether a sheet inherits beyond the inherited half")
    void inheritsExplicitly() {
        assertFalse(resolver("button { color: inherit; padding: unset }").inheritsExplicitly());
        assertTrue(resolver("button { padding-left: inherit }").inheritsExplicitly());
        assertTrue(resolver("button { all: inherit }").inheritsExplicitly());
    }

    @Test
    @DisplayName("applies() answers for a keyword as for any value the property takes")
    void applies() {
        assertTrue(ComputedStyle.applies("padding", CssTokenizer.tokenize("inherit"), CssLength.Context.DEFAULT));
        assertTrue(ComputedStyle.applies("all", CssTokenizer.tokenize("unset"), CssLength.Context.DEFAULT));
        assertFalse(ComputedStyle.applies("all", CssTokenizer.tokenize("red"), CssLength.Context.DEFAULT));
        assertFalse(ComputedStyle.applies("float", CssTokenizer.tokenize("inherit"), CssLength.Context.DEFAULT));
    }

    private static StyleResolver resolver(String css) {
        return new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)));
    }
}
