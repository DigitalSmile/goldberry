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

import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssLength;

class ThemeTest {

    /// The semantic tokens §10 says widgets consume. A theme missing one of
    /// these is a theme that leaves some widget unpainted.
    private static final List<String> SEMANTIC_TOKENS = List.of(
            "--gb-bg",
            "--gb-surface",
            "--gb-surface-2",
            "--gb-text",
            "--gb-text-muted",
            "--gb-border",
            "--gb-accent",
            "--gb-focus",
            "--gb-danger",
            "--gb-warning",
            "--gb-success",
            "--gb-info",
            "--gb-selection");

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
        var variables = new StyleResolver(List.of(theme.load())).customPropertiesFor(element("window"));

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

    /// Which way a surface token *goes*, which is the fact three widgets got
    /// wrong ([ADR-0245]).
    ///
    /// `card`, `text-input` and `select` each read `--gb-surface-2` meaning
    /// "raised" or "sunken", and each was wrong **on one theme only** — because
    /// the token promises a step and not a direction, and takes opposite
    /// directions in the two files. `--gb-surface-raised` and
    /// `--gb-surface-sunken` were added to say which way they go; this is what
    /// holds them to it, and what records why the third token cannot be held to
    /// anything.
    @Nested
    @DisplayName("which way a surface goes")
    class Elevation {

        /// A token's colour as the cascade delivers it, composited over
        /// `--gb-surface` when it has alpha.
        ///
        /// `--gb-surface-sunken` is `rgba(0, 0, 0, …)` in both themes and
        /// deliberately so — an alpha over whatever is underneath is the only way
        /// to say "dimmer than its own surface" in a subset with no colour
        /// functions (ADR-0166) — so comparing its raw value against anything
        /// would be comparing a black nobody paints.
        private double luminanceOver(Theme theme, String token, int backdrop) {
            var argb = styleWith(theme, "button { background: var(" + token + ") }")
                    .background();
            var alpha = ((argb >>> 24) & 0xFF) / 255.0;
            var r = alpha * ((argb >> 16) & 0xFF) + (1 - alpha) * ((backdrop >> 16) & 0xFF);
            var g = alpha * ((argb >> 8) & 0xFF) + (1 - alpha) * ((backdrop >> 8) & 0xFF);
            var b = alpha * (argb & 0xFF) + (1 - alpha) * (backdrop & 0xFF);
            return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
        }

        private double linear(double eight) {
            var c = eight / 255.0;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }

        private int surfaceOf(Theme theme) {
            return styleWith(theme, "button { background: var(--gb-surface) }").background();
        }

        /// The promise the name makes, and the only one that matters: a raised
        /// thing is never *darker* than what it sits on. Equal is allowed — the
        /// light theme's `--gb-surface-raised` is the same white as
        /// `--gb-surface`, because on a light theme there is nowhere lighter to
        /// go and the edge carries the elevation instead (ADR-0166).
        @ParameterizedTest
        @EnumSource(Theme.class)
        @DisplayName("raised is never darker than the surface it sits on")
        void raisedGoesUp(Theme theme) {
            var surface = surfaceOf(theme);
            var raised = luminanceOver(theme, "--gb-surface-raised", surface);

            assertTrue(
                    raised >= luminanceOver(theme, "--gb-surface", surface) - 1e-9,
                    () -> theme + "'s --gb-surface-raised is darker than --gb-surface, which is the"
                            + " direction its name forbids");
        }

        @ParameterizedTest
        @EnumSource(Theme.class)
        @DisplayName("and sunken is never lighter")
        void sunkenGoesDown(Theme theme) {
            var surface = surfaceOf(theme);
            var sunken = luminanceOver(theme, "--gb-surface-sunken", surface);

            assertTrue(
                    sunken <= luminanceOver(theme, "--gb-surface", surface) + 1e-9,
                    () -> theme + "'s --gb-surface-sunken is lighter than --gb-surface");
        }

        /// The trap, asserted rather than described. `--gb-surface-2` is a step
        /// **up** from `--gb-surface` on the dark theme and a step **down** on the
        /// light one, which is exactly why a widget that read it meaning "raised"
        /// looked right to whoever wrote it and wrong to everybody on the other
        /// theme.
        ///
        /// If this ever fails, the two files have been made to agree and the
        /// question the `TODO.md` entry asked — whether `--gb-surface-2` should
        /// keep existing — is worth reopening. It is not a failure to fix by
        /// changing this test.
        @Test
        @DisplayName("--gb-surface-2 takes opposite directions in the two themes, which is why it promises none")
        void theSecondSurfacePromisesNothing() {
            var darkSurface = surfaceOf(Theme.NORD_DARK);
            var lightSurface = surfaceOf(Theme.NORD_LIGHT);

            var darkStep = luminanceOver(Theme.NORD_DARK, "--gb-surface-2", darkSurface)
                    - luminanceOver(Theme.NORD_DARK, "--gb-surface", darkSurface);
            var lightStep = luminanceOver(Theme.NORD_LIGHT, "--gb-surface-2", lightSurface)
                    - luminanceOver(Theme.NORD_LIGHT, "--gb-surface", lightSurface);

            assertTrue(
                    darkStep > 0, () -> "--gb-surface-2 is expected to be lighter than --gb-surface on the dark theme");
            assertTrue(
                    lightStep < 0,
                    () -> "--gb-surface-2 is expected to be darker than --gb-surface on the light theme."
                            + " If the themes have been made to agree, reopen the question of whether"
                            + " --gb-surface-2 should keep existing rather than editing this test.");
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

            assertEquals(
                    styleWith(Theme.NORD_LIGHT, bg).background(),
                    styleWith(Theme.NORD_DARK, text).background());
            assertEquals(
                    styleWith(Theme.NORD_DARK, bg).background(),
                    styleWith(Theme.NORD_LIGHT, text).background());
        }

        @Test
        @DisplayName("an application rule still overrides the theme")
        void applicationBeatsTheme() {
            var base = Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, "button { background: var(--gb-bg) }");
            var app = Stylesheet.parse(CascadeLayer.APPLICATION, "button { background: #ff0000 }");
            var root = element("window");
            root.with(element("button"));

            var declarations = new StyleResolver(List.of(base, Theme.NORD_DARK.load(), app)).resolve(root.descend(1));

            assertEquals(
                    0xFFFF0000,
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
            assertTrue(alpha > 0 && alpha < 255, () -> theme + "'s selection alpha was " + alpha);
        }
    }
}
