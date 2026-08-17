package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.Token;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §1.3's density preference, and the promise attached to it: "token-conformant
/// apps adapt with zero code" ([ADR-0074]).
///
/// The assertions that matter are about *every* control at once rather than any
/// one of them. A density that moved `button` and not `checkbox` would pass a
/// per-control test and be exactly the divergence §3's shared metrics row exists
/// to prevent — so the heights are asserted over the whole catalog, and a
/// control added with a literal height fails here on the day it is added.
class DensityTest {

    /// §1.3's density row: control heights 32 / 28.
    private static final StyleLength REGULAR_HEIGHT = StyleLength.points(32);
    private static final StyleLength COMPACT_HEIGHT = StyleLength.points(28);

    /// The controls §3 gives a height to. `radio-group` is not among them: it is
    /// a container whose height is the sum of its options, and a density that
    /// pinned it would clip the group the moment an option moved.
    private static final List<String> SIZED = List.of("button", "checkbox", "radio");

    @Nested
    @DisplayName("the tokens")
    class Tokens {

        /// The claim that an application which never mentions density still gets
        /// one — because regular is not applied, it is what the toolkit is.
        @Test
        @DisplayName("regular is the base stylesheet's own value and ships no sheet")
        void regularIsTheDefault() {
            assertTrue(Density.REGULAR.stylesheets().isEmpty());
            assertTrue(Density.REGULAR.resourceName().isEmpty());
            assertEquals("", Density.REGULAR.source());

            var base = List.of(Controls.baseStylesheet());
            assertEquals("32px", customProperty(base, "--gb-control-height"));
            assertEquals("32px", customProperty(base, "--gb-list-row-height"));
            assertEquals("regular", customProperty(base, "--gb-density"));
        }

        @Test
        @DisplayName("compact overrides all three tokens")
        void compactOverrides() {
            var sheets = Controls.stylesheets(Theme.NORD_DARK, Density.COMPACT);

            assertEquals("28px", customProperty(sheets, "--gb-control-height"));
            assertEquals("26px", customProperty(sheets, "--gb-list-row-height"));
            assertEquals("compact", customProperty(sheets, "--gb-density"));
        }

        /// The **layer** is what makes the override work — not list order and not
        /// a more specific selector. Both blocks are `:root`, so a compact sheet
        /// parsed into TOOLKIT_BASE would leave the cascade nothing to separate
        /// them by and the winner would be an accident of sort stability.
        @Test
        @DisplayName("the compact sheet sits in the theme layer")
        void compactIsAThemeLayer() {
            var sheets = Density.COMPACT.stylesheets();

            assertEquals(1, sheets.size());
            assertEquals(CascadeLayer.THEME, sheets.getFirst().layer());
        }

        /// A density is a set of numbers and never a rule. A stylesheet here that
        /// matched a selector would be styling controls behind the theme's back,
        /// and switching density would restyle rather than resize.
        @Test
        @DisplayName("compact declares custom properties and nothing else")
        void compactDeclaresOnlyTokens() {
            for (var rule : Density.COMPACT.stylesheets().getFirst().rules()) {
                for (var declaration : rule.declarations()) {
                    assertTrue(declaration.isCustomProperty(),
                            "a density may only carry tokens, and this one declares "
                                    + declaration.property());
                }
            }
        }

        @Test
        @DisplayName("a density's source is readable, the way a theme's is")
        void sourceIsReadable() {
            assertEquals("density-compact.css", Density.COMPACT.resourceName().orElseThrow());
            assertTrue(Density.COMPACT.source().contains("--gb-control-height"));
        }

        /// §1.3's "zero code" cuts both ways: a density carries no class and no
        /// type, so an application cannot opt one control out of it and markup
        /// cannot ask for it. It is a preference, not a variant.
        @Test
        @DisplayName("a density is not a class a document could name")
        void densityIsNotAVariant() {
            assertTrue(Density.COMPACT.stylesheets().getFirst().rules().stream()
                    .flatMap(rule -> rule.selectors().stream())
                    .allMatch(selector -> selector.specificity() == rootSpecificity()),
                    "every selector in a density must be :root");
        }
    }

    @Nested
    @DisplayName("what a control resolves to")
    class Heights {

        @Test
        @DisplayName("every control is 32 high at regular")
        void regularHeights() {
            for (var type : SIZED) {
                assertEquals(REGULAR_HEIGHT, heightOf(type, Density.REGULAR),
                        type + " should be 32 high at regular");
            }
        }

        @Test
        @DisplayName("every control is 28 high at compact")
        void compactHeights() {
            for (var type : SIZED) {
                assertEquals(COMPACT_HEIGHT, heightOf(type, Density.COMPACT),
                        type + " should be 28 high at compact");
            }
        }

        /// The half the two tests above cannot cover between them: if the token
        /// were dropped and both densities fell back to one literal, one of those
        /// two would still pass entirely. This is what says they differ at all.
        @Test
        @DisplayName("a control's height actually changes between the two")
        void theTwoDiffer() {
            for (var type : SIZED) {
                assertNotEquals(heightOf(type, Density.REGULAR), heightOf(type, Density.COMPACT),
                        type + " does not respond to density at all");
            }
        }

        /// §3.1's glyph is 16 at either density: compact shrinks the row, not the
        /// thing the user aims at. That is the trade ADR-0074 makes against
        /// §1.3's own 32×32 hit-target floor, and it is defensible only while the
        /// glyph holds still — so this is the assertion that holds it there.
        @Test
        @DisplayName("the glyph does not shrink with the row")
        void theGlyphHoldsStill() {
            for (var part : List.of("check-indicator", "radio-indicator")) {
                assertEquals(StyleLength.points(16), styleOf(part, Density.COMPACT).height(),
                        part + " should stay 16px at compact");
                assertEquals(styleOf(part, Density.REGULAR).height(),
                        styleOf(part, Density.COMPACT).height());
            }
        }

        /// The other §3 metrics stay literal, and this keeps them there. Turning
        /// padding and gap into tokens "for symmetry" would invent a density
        /// scale §1.3 does not define (Principle 3).
        @Test
        @DisplayName("padding, gap and radius do not move with a density")
        void onlyHeightMoves() {
            var regular = styleOf("button", Density.REGULAR);
            var compact = styleOf("button", Density.COMPACT);

            assertEquals(regular.padding(), compact.padding());
            assertEquals(regular.gap(), compact.gap());
            assertEquals(regular.decoration(), compact.decoration());
        }
    }

    // ---------------------------------------------------------------- helpers

    private static StyleLength heightOf(String type, Density density) {
        return styleOf(type, density).height();
    }

    /// The style the cascade resolves for a bare node of `type`, against the
    /// toolkit's own stylesheets and nothing else.
    private static ComputedStyle styleOf(String type, Density density) {
        var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK, density));
        return ComputedStyle.of(resolver.resolve(new Probe(type)), CssLength.Context.DEFAULT);
    }

    private static String customProperty(List<Stylesheet> sheets, String name) {
        var tokens = new StyleResolver(sheets).customPropertiesFor(new Probe("button")).get(name);
        return tokens == null ? null : tokens.stream().map(Token::cssText).reduce("", String::concat);
    }

    private static int rootSpecificity() {
        return Stylesheet.parse(CascadeLayer.THEME, ":root { --probe: 1 }")
                .rules().getFirst().selectors().getFirst().specificity();
    }

    /// A node that exists only to be styled.
    ///
    /// [StyleElement] rather than a real widget, because what is under test is
    /// the stylesheet: a real `Button` would bring its label and its icon along,
    /// and the height being asserted would then be one of four boxes. It is its
    /// own root, so `:root`'s tokens reach it directly.
    private record Probe(String type) implements StyleElement {

        @Override
        public String id() {
            return null;
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public StyleElement parent() {
            return null;
        }

        @Override
        public boolean hasState(Selector.PseudoClass state) {
            return false;
        }
    }
}
