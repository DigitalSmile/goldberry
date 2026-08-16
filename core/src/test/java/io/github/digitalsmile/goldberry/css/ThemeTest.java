package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ThemeTest {

    /// The semantic tokens §10 says widgets consume. A theme missing one of
    /// these is a theme that leaves some widget unpainted.
    private static final List<String> SEMANTIC_TOKENS = List.of(
            "--gb-bg", "--gb-surface", "--gb-surface-2", "--gb-text", "--gb-text-muted",
            "--gb-border", "--gb-accent", "--gb-focus", "--gb-danger", "--gb-warning",
            "--gb-success", "--gb-info", "--gb-selection");

    /// A theme is only ever seen through the cascade, so resolve it the way a
    /// widget would rather than reading the file.
    private static ComputedStyle styleWith(Theme theme, String widgetCss) {
        var base = Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, widgetCss);
        var root = element("window");
        root.with(element("button"));
        var declarations = new StyleResolver(List.of(base, theme.load())).resolve(root.descend(1));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT);
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    @DisplayName("both themes ship and parse")
    void themesParse(Theme theme) {
        var sheet = theme.load();
        assertNotNull(sheet);
        assertEquals(CascadeLayer.THEME, sheet.layer());
        assertTrue(sheet.rules().size() >= 1);
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    @DisplayName("every semantic token is defined, and resolves to a real colour")
    void everySemanticTokenResolves(Theme theme) {
        var variables = new StyleResolver(List.of(theme.load()))
                .customPropertiesFor(element("window"));

        for (var token : SEMANTIC_TOKENS) {
            assertTrue(variables.containsKey(token), () -> theme + " is missing " + token);

            // Defined is not enough: the value has to survive var() expansion
            // and be a colour, or a widget reading it paints nothing.
            var style = styleWith(theme, "button { background: var(" + token + ") }");
            assertNotEquals(
                    ComputedStyle.INITIAL.background(),
                    style.background(),
                    () -> theme + "'s " + token + " did not resolve to a usable colour");
        }
    }

    @Nested
    @DisplayName("the palette is theme-invariant")
    class Palette {

        @ParameterizedTest
        @ValueSource(strings = {"--nord0", "--nord6", "--nord8", "--nord10", "--nord11"})
        @DisplayName("a raw palette entry is the same colour in both themes")
        void paletteIsShared(String token) {
            // nord8 is a fact about Nord, not about a theme. If these ever
            // diverge, one of the two files has had a semantic edit applied to
            // the wrong tier.
            var css = "button { background: var(" + token + ") }";
            assertEquals(
                    styleWith(Theme.NORD_LIGHT, css).background(),
                    styleWith(Theme.NORD_DARK, css).background());
        }
    }

    @Nested
    @DisplayName("switching")
    class Switching {

        @Test
        @DisplayName("swapping the theme repaints a widget rule that never mentions a colour")
        void oneSwapChangesEverything() {
            // The claim §10 makes: switching is one stylesheet swap. The widget
            // rule below is identical in both cases.
            var widget = "button { background: var(--gb-bg); color: var(--gb-text) }";

            var light = styleWith(Theme.NORD_LIGHT, widget);
            var dark = styleWith(Theme.NORD_DARK, widget);

            assertEquals(0xFFECEFF4, light.background());
            assertEquals(0xFF2E3440, light.color());
            assertEquals(0xFF2E3440, dark.background());
            assertEquals(0xFFECEFF4, dark.color());
        }

        @Test
        @DisplayName("background and text swap ends of the palette between themes")
        void lightAndDarkAreInverses() {
            var bg = "button { background: var(--gb-bg) }";
            var text = "button { background: var(--gb-text) }";

            assertEquals(styleWith(Theme.NORD_LIGHT, bg).background(),
                    styleWith(Theme.NORD_DARK, text).background());
            assertEquals(styleWith(Theme.NORD_DARK, bg).background(),
                    styleWith(Theme.NORD_LIGHT, text).background());
        }

        @Test
        @DisplayName("an application rule still overrides the theme")
        void applicationBeatsTheme() {
            var base = Stylesheet.parse(CascadeLayer.TOOLKIT_BASE,
                    "button { background: var(--gb-bg) }");
            var app = Stylesheet.parse(CascadeLayer.APPLICATION,
                    "button { background: #ff0000 }");
            var root = element("window");
            root.with(element("button"));

            var declarations = new StyleResolver(List.of(base, Theme.NORD_DARK.load(), app))
                    .resolve(root.descend(1));

            assertEquals(0xFFFF0000,
                    ComputedStyle.of(declarations, CssLength.Context.DEFAULT).background());
        }
    }

    @Nested
    @DisplayName("selection")
    class Selection {

        @ParameterizedTest
        @EnumSource(Theme.class)
        @DisplayName("the selection colour is translucent, as §10 specifies")
        void selectionIsTranslucent(Theme theme) {
            var style = styleWith(theme, "button { background: var(--gb-selection) }");
            var alpha = (style.background() >>> 24) & 0xFF;

            // 30% light, 40% dark -- both well short of opaque, and not zero.
            assertTrue(alpha > 0 && alpha < 255,
                    () -> theme + "'s selection alpha was " + alpha);
        }
    }
}
