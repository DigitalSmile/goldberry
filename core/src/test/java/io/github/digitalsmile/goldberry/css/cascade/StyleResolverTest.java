package io.github.digitalsmile.goldberry.css.cascade;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.parse.Token;

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

        /// Two sheets in one layer, which is what `TOOLKIT_BASE` actually holds:
        /// `controls.css`, `MarkdownStyles` and `HtmlStyles` are all in it.
        ///
        /// A `Match` carried the rule's index *within its own sheet* as its whole
        /// source order, so the two sheets' indices were compared against each
        /// other and an earlier sheet's twelfth rule beat a later sheet's first.
        /// The padding below is the shape of it: whichever of the two rules sits
        /// lower in its own file used to win, whatever order the sheets were
        /// loaded in.
        @Test
        @DisplayName("at equal specificity the later sheet in a layer wins, whatever its rules are numbered")
        void sheetOrderWithinALayer() {
            var first = sheet(CascadeLayer.TOOLKIT_BASE, """
                    input { padding: 1px }
                    input { padding: 2px }
                    button { color: red }
                    """);
            var second = sheet(CascadeLayer.TOOLKIT_BASE, "button { color: blue }");

            assertEquals("blue", value(resolver(first, second).resolve(element("button")), "color"));
        }

        @Test
        @DisplayName("and the other way round, so it is the sheets that are ordered and not the rules")
        void sheetOrderReversed() {
            var first = sheet(CascadeLayer.TOOLKIT_BASE, "button { color: blue }");
            var second = sheet(CascadeLayer.TOOLKIT_BASE, """
                    input { padding: 1px }
                    input { padding: 2px }
                    button { color: red }
                    """);

            assertEquals("red", value(resolver(first, second).resolve(element("button")), "color"));
        }

        @Test
        @DisplayName("a sheet's position never outranks the layer above it")
        void layerBeatsSheetOrder() {
            // The ordering the sheet index must not disturb: it separates two
            // sheets *inside* a layer, and a sheet handed over first is still in
            // whatever layer it declared.
            var app = sheet(CascadeLayer.APPLICATION, "button { color: blue }");
            var base = sheet(CascadeLayer.TOOLKIT_BASE, "button { color: red }");

            assertEquals("blue", value(resolver(app, base).resolve(element("button")), "color"));
            assertEquals("blue", value(resolver(base, app).resolve(element("button")), "color"));
        }

        @Test
        @DisplayName("nor the specificity above that")
        void specificityBeatsSheetOrder() {
            var first = sheet(CascadeLayer.TOOLKIT_BASE, "button.primary { color: red }");
            var second = sheet(CascadeLayer.TOOLKIT_BASE, "button { color: blue }");

            assertEquals("red", value(resolver(first, second).resolve(element("button.primary")), "color"));
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

        /// Which order the winners come out in, which nothing had needed to be
        /// true until a property arrived with four longhands over it.
        ///
        /// Nothing between the resolver and `ComputedStyle.apply` re-orders, so
        /// the order properties come out in *is* the order they are applied in —
        /// and a `padding` applied after a `padding-left` overwrites the edge the
        /// longhand set. This was a `HashMap`, so the answer was whichever way
        /// the two property names' buckets fell: `padding` and `padding-left`
        /// came out the right way round and `inset` and `left` came out the wrong
        /// way, so `inset: 8px; left: 20px` quietly lost its `left`
        /// ([ADR-0311]).
        @Nested
        @DisplayName("the order the winners come out in")
        class DeclarationOrder {

            /// The property names in the order the resolver returns them.
            private List<String> order(String css, String selector) {
                return List.copyOf(resolver(sheet(CascadeLayer.APPLICATION, css))
                        .resolve(element(selector))
                        .keySet());
            }

            @Test
            @DisplayName("is the order the declarations were written in")
            void sourceOrder() {
                assertEquals(
                        List.of("inset", "left", "padding", "padding-left"),
                        order("button { inset: 8px; left: 20px; padding: 4px; padding-left: 9px }", "button"));
            }

            @Test
            @DisplayName("and the other way round, which is the case a hash could not tell apart")
            void reversed() {
                assertEquals(List.of("left", "inset"), order("button { left: 20px; inset: 8px }", "button"));
            }

            @Test
            @DisplayName("across rules, weakest first")
            void acrossRules() {
                var css = """
                        button { left: 20px }
                        button.primary { inset: 8px }
                        """;
                assertEquals(List.of("left", "inset"), order(css, "button.primary"));
            }

            @Test
            @DisplayName("a property that wins twice sits where its winning declaration is")
            void rewonPropertyMovesToTheEnd() {
                // `LinkedHashMap` keeps a re-put key at its **first** position,
                // which would leave `padding-left` where the losing first
                // declaration was — behind the `padding` that must not overwrite
                // it. The resolver removes before it puts for exactly this.
                var css = """
                        button { padding-left: 1px }
                        button { padding: 4px }
                        button { padding-left: 9px }
                        """;
                assertEquals(List.of("padding", "padding-left"), order(css, "button"));
            }
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
            var root =
                    element("window").with(element("row").with(element("form").with(element("button"))));

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

    /// One missing token is a **message**, not a stream ([ADR-0243]).
    ///
    /// A stylesheet is static, so a `var()` that resolves to nothing cannot
    /// resolve on the next frame either — but a style is resolved per element per
    /// invalidation, so before this an unresolvable token reported itself sixty
    /// times a second for as long as the screen it was on kept moving. Two of
    /// them survived long enough to reach a user, which is what a log nobody can
    /// read costs.
    ///
    /// Asserted through [StyleResolver#reportedDrops] rather than through the log
    /// itself: only `slf4j-api` is on the classpath, so there is no appender to
    /// read back, and a logging backend bought for one assertion would be a
    /// dependency this does not need.
    @Nested
    @DisplayName("reporting a var() that resolves to nothing")
    class Reporting {

        /// `--missing` is defined nowhere and the declaration has no fallback,
        /// which is CSS's "invalid at computed-value time".
        private static final String CSS = "button { color: var(--missing) } text { color: var(--missing) }";

        /// A fresh `window > type` tree each call, because what varies between
        /// these cases is the child's **type** and `descend` walks depth rather
        /// than siblings.
        private static io.github.digitalsmile.goldberry.css.TestElement childOf(String type) {
            var root = element("window");
            root.with(element(type));
            return root.descend(1);
        }

        @Test
        @DisplayName("the same element resolved many times reports once")
        void oncePerProperty() {
            var resolver = resolver(sheet(CascadeLayer.APPLICATION, CSS));
            var root = element("window");
            root.with(element("button"));

            for (var frame = 0; frame < 5; frame++) {
                var resolved = resolver.resolve(root.descend(1));
                assertFalse(resolved.containsKey("color"), "the declaration must be dropped every time");
            }

            assertEquals(1, resolver.reportedDrops(), "five resolves of one element reported more than once");
        }

        /// Keyed by element type as well as property, because the same token
        /// failing on `button` and on `text` is two facts — and which types it
        /// reaches is the blast radius somebody debugging it wants.
        @Test
        @DisplayName("but a second element type is a second fact")
        void oncePerType() {
            var resolver = resolver(sheet(CascadeLayer.APPLICATION, CSS));

            resolver.resolve(childOf("button"));
            resolver.resolve(childOf("text"));
            resolver.resolve(childOf("button"));

            assertEquals(2, resolver.reportedDrops(), "two types sharing one bad token should be two reports");
        }

        /// The deduplication is **per resolver**, which is what makes it "per
        /// stylesheet": a theme swap builds a new renderer and therefore a new
        /// resolver, and what the new theme is missing is news.
        @Test
        @DisplayName("and a new resolver reports again, because a new theme is news")
        void perResolver() {
            var root = element("window");
            root.with(element("button"));

            var first = resolver(sheet(CascadeLayer.APPLICATION, CSS));
            first.resolve(root.descend(1));

            var second = resolver(sheet(CascadeLayer.APPLICATION, CSS));
            assertEquals(0, second.reportedDrops(), "a fresh resolver started with something already reported");
            second.resolve(root.descend(1));
            assertEquals(1, second.reportedDrops());
        }

        /// A cycle is a fact about the property rather than about the element, so
        /// it is keyed by name alone and reported once however many nodes hit it.
        @Test
        @DisplayName("a self-referring custom property is reported once too")
        void cycleReportedOnce() {
            var resolver = resolver(sheet(
                    CascadeLayer.APPLICATION,
                    ":root { --a: var(--b); --b: var(--a) } button { color: var(--a) } text { color: var(--a) }"));
            resolver.resolve(childOf("button"));
            resolver.resolve(childOf("text"));

            // The cycle key is the property name; the two drops it causes are
            // keyed by type. What matters is that neither grows per frame.
            var after = resolver.reportedDrops();
            resolver.resolve(childOf("button"));
            resolver.resolve(childOf("text"));
            assertEquals(after, resolver.reportedDrops(), "resolving again added a report");
        }
    }
}
