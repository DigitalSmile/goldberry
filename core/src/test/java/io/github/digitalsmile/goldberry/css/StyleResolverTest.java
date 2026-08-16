package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StyleResolverTest {

    private static StyleResolver resolver(Stylesheet... sheets) {
        return new StyleResolver(List.of(sheets));
    }

    private static Stylesheet sheet(CascadeLayer layer, String css) {
        return Stylesheet.parse(layer, css);
    }

    /// A resolved value flattened back to text, which is what an assertion about
    /// "which declaration won" actually wants to compare.
    private static String value(Map<String, List<Token>> resolved, String property) {
        var tokens = resolved.get(property);
        if (tokens == null) {
            return null;
        }
        var text = new StringBuilder();
        for (var token : tokens) {
            text.append(token.cssText());
        }
        return text.toString();
    }

    @Nested
    @DisplayName("the cascade")
    class Cascade {

        @Test
        @DisplayName("a more specific selector wins")
        void specificity() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    button { color: red }
                    button.primary { color: blue }
                    """);
            var resolved = resolver(css).resolve(element("button.primary"));

            assertEquals("blue", value(resolved, "color"));
        }

        @Test
        @DisplayName("at equal specificity the later rule wins")
        void sourceOrder() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    button { color: red }
                    button { color: blue }
                    """);
            assertEquals("blue", value(resolver(css).resolve(element("button")), "color"));
        }

        @Test
        @DisplayName("at equal specificity the later layer wins")
        void layerOrder() {
            // Order of the arguments is deliberately wrong-way-round: the layer
            // decides, not the order the sheets were handed over.
            var app = sheet(CascadeLayer.APPLICATION, "button { color: blue }");
            var base = sheet(CascadeLayer.TOOLKIT_BASE, "button { color: red }");

            assertEquals("blue", value(resolver(app, base).resolve(element("button")), "color"));
        }

        @Test
        @DisplayName("specificity beats layer, per §8")
        void specificityBeatsLayer() {
            // "Later layer wins AT EQUAL SPECIFICITY" -- so a sharper toolkit
            // rule still beats a vaguer application one, exactly as two rules in
            // one stylesheet would.
            var base = sheet(CascadeLayer.TOOLKIT_BASE, "button.primary { color: red }");
            var app = sheet(CascadeLayer.APPLICATION, "button { color: blue }");

            assertEquals("red", value(resolver(base, app).resolve(element("button.primary")), "color"));
        }

        @Test
        @DisplayName("!important outranks everything")
        void important() {
            var base = sheet(CascadeLayer.TOOLKIT_BASE, "button { color: red !important }");
            var inline = sheet(CascadeLayer.INLINE, "button#x.a.b.c { color: blue }");

            assertEquals("red", value(resolver(base, inline).resolve(element("button#x.a.b.c")), "color"));
        }

        @Test
        @DisplayName("a rule that does not match contributes nothing")
        void nonMatchingRules() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    input { color: red }
                    button { padding: 4px }
                    """);
            var resolved = resolver(css).resolve(element("button"));

            assertFalse(resolved.containsKey("color"));
            assertEquals("4px", value(resolved, "padding"));
        }

        @Test
        @DisplayName("a selector list uses its most specific matching selector")
        void selectorListSpecificity() {
            // ".x, button.primary" matches this element twice; the rule counts
            // at the higher specificity, which beats the plain "button" below it.
            var css = sheet(CascadeLayer.APPLICATION, """
                    .x, button.primary { color: blue }
                    button { color: red }
                    """);
            assertEquals("blue", value(resolver(css).resolve(element("button.primary")), "color"));
        }
    }

    @Nested
    @DisplayName("custom properties")
    class CustomProperties {

        @Test
        @DisplayName("a var() is replaced by the property's value")
        void substitution() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    :root { --gb-accent: #88c0d0 }
                    button { color: var(--gb-accent) }
                    """);
            var root = element("window");
            root.with(element("button"));

            assertEquals("#88c0d0", value(resolver(css).resolve(root.descend(1)), "color"));
        }

        @Test
        @DisplayName("custom properties inherit down the tree")
        void inheritance() {
            // The whole point of :root as a theming hook -- a button three levels
            // down has to see it.
            var css = sheet(CascadeLayer.APPLICATION, """
                    :root { --gb-accent: #88c0d0 }
                    button { color: var(--gb-accent) }
                    """);
            var root = element("window").with(element("row").with(element("form").with(element("button"))));

            assertEquals("#88c0d0", value(resolver(css).resolve(root.descend(3)), "color"));
        }

        @Test
        @DisplayName("a nearer definition overrides an inherited one")
        void shadowing() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    :root { --gb-accent: #88c0d0 }
                    .danger { --gb-accent: #bf616a }
                    button { color: var(--gb-accent) }
                    """);
            var root = element("window").with(element("row.danger").with(element("button")));

            assertEquals("#bf616a", value(resolver(css).resolve(root.descend(2)), "color"));
        }

        @Test
        @DisplayName("a theme layer overrides the base's custom properties")
        void themeLayer() {
            // §10: swapping a theme is swapping a custom-property layer.
            var base = sheet(CascadeLayer.TOOLKIT_BASE, """
                    :root { --gb-bg: #ffffff }
                    button { background: var(--gb-bg) }
                    """);
            var dark = sheet(CascadeLayer.THEME, ":root { --gb-bg: #2e3440 }");
            var root = element("window").with(element("button"));

            assertEquals("#2e3440", value(resolver(base, dark).resolve(root.descend(1)), "background"));
        }

        @Test
        @DisplayName("a var() can expand to another var()")
        void chained() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    :root { --a: #88c0d0; --b: var(--a) }
                    button { color: var(--b) }
                    """);
            var root = element("window").with(element("button"));

            assertEquals("#88c0d0", value(resolver(css).resolve(root.descend(1)), "color"));
        }

        @Test
        @DisplayName("a fallback is used when the property is undefined")
        void fallback() {
            var css = sheet(CascadeLayer.APPLICATION, "button { color: var(--missing, #ff0000) }");
            assertEquals("#ff0000", value(resolver(css).resolve(element("button")), "color"));
        }

        @Test
        @DisplayName("a var() inside a fallback resolves too")
        void nestedFallback() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    :root { --there: #88c0d0 }
                    button { color: var(--missing, var(--there)) }
                    """);
            var root = element("window").with(element("button"));

            assertEquals("#88c0d0", value(resolver(css).resolve(root.descend(1)), "color"));
        }

        @Test
        @DisplayName("an unresolvable var() with no fallback drops the declaration")
        void invalidAtComputedValueTime() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    button { color: var(--missing); padding: 4px }
                    """);
            var resolved = resolver(css).resolve(element("button"));

            // CSS calls this "invalid at computed-value time" and drops it. The
            // sibling declaration is unaffected.
            assertFalse(resolved.containsKey("color"));
            assertEquals("4px", value(resolved, "padding"));
        }

        @Test
        @DisplayName("a cyclic var() is dropped rather than overflowing the stack")
        void cycles() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    :root { --a: var(--b); --b: var(--a) }
                    button { color: var(--a) }
                    """);
            var root = element("window").with(element("button"));

            assertFalse(resolver(css).resolve(root.descend(1)).containsKey("color"));
        }

        @Test
        @DisplayName("a var() among other tokens keeps them")
        void partialSubstitution() {
            var css = sheet(CascadeLayer.APPLICATION, """
                    :root { --pad: 8px }
                    button { padding: 4px var(--pad) }
                    """);
            var root = element("window").with(element("button"));

            assertEquals("4px 8px", value(resolver(css).resolve(root.descend(1)), "padding"));
        }

        @Test
        @DisplayName("custom properties are not themselves returned as style")
        void customPropertiesAreNotOutput() {
            var css = sheet(CascadeLayer.APPLICATION, ":root { --gb-accent: red }");
            var resolved = resolver(css).resolve(element("window"));

            // They are the mechanism, not the result: nothing paints "--gb-accent".
            assertFalse(resolved.containsKey("--gb-accent"));
            assertTrue(resolver(css).customPropertiesFor(element("window")).containsKey("--gb-accent"));
        }
    }

    @Nested
    @DisplayName("a themed button")
    class Realistic {

        @Test
        @DisplayName("base, theme and application compose the way §8 and §10 say")
        void wholeCascade() {
            var base = sheet(CascadeLayer.TOOLKIT_BASE, """
                    :root { --gb-bg: #eceff4; --gb-fg: #2e3440 }
                    button { background: var(--gb-bg); color: var(--gb-fg); padding: 4px 8px }
                    button:hover { background: var(--gb-accent, #d8dee9) }
                    """);
            var theme = sheet(CascadeLayer.THEME, """
                    :root { --gb-bg: #3b4252; --gb-fg: #eceff4; --gb-accent: #88c0d0 }
                    """);
            var app = sheet(CascadeLayer.APPLICATION, """
                    button.primary { padding: 6px 12px }
                    """);

            var root = element("window");
            root.with(element("button.primary:hover"));
            var resolved = resolver(base, theme, app).resolve(root.descend(1));

            // Theme's custom property reaches a base rule that never mentions it.
            assertEquals("#eceff4", value(resolved, "color"));
            // :hover is more specific than the bare type rule, and the accent
            // now exists so the fallback is not used.
            assertEquals("#88c0d0", value(resolved, "background"));
            // Application beats base at higher specificity.
            assertEquals("6px 12px", value(resolved, "padding"));
        }
    }
}
