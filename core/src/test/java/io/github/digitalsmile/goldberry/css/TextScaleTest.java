package io.github.digitalsmile.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.assets.BundledFont;

/// §1.4's **global text-scale token**, which `ARCHITECTURE.md` §17 had recorded
/// as "neither implemented nor gallery-enforced" ([ADR-0267]).
///
/// This is the implemented half. The enforcement half — "every component must
/// survive 150% without clipping (gallery-enforced)" — needs the mechanism to
/// exist before an image can be taken of it, and now it does.
class TextScaleTest {

    private static final Typography BODY = new Typography("Inter", 13, BundledFont.Weight.REGULAR, 18);

    @Test
    @DisplayName("the size and the line height both grow, because a line box that did not would overlap")
    void bothLengthsScale() {
        var scaled = BODY.scaled(1.5);

        assertEquals(19.5, scaled.size(), 1e-9);
        assertEquals(27, scaled.lineHeight(), 1e-9);
        assertEquals(BODY.family(), scaled.family());
        assertEquals(BODY.weight(), scaled.weight(), "a text scale is a size, not a weight");
    }

    /// A negative line height is a **ratio** rather than a length, stored negated
    /// — so scaling it as well would apply the factor twice: once through the
    /// size the ratio multiplies, and once again here.
    @Test
    @DisplayName("a ratio line-height is left alone, or the factor would be squared")
    void ratiosAreNotScaled() {
        var ratio = new Typography("Inter", 13, BundledFont.Weight.REGULAR, -1.4);

        var scaled = ratio.scaled(1.5);

        assertEquals(-1.4, scaled.lineHeight(), 1e-9, "still a ratio, and still the same one");
        assertEquals(19.5, scaled.size(), 1e-9);
        assertEquals(
                ratio.resolvedLineHeight() * 1.5,
                scaled.resolvedLineHeight(),
                1e-9,
                "so the line box grows exactly once");
    }

    @Test
    @DisplayName("a factor of one is the same object, which is what makes the default free")
    void oneIsIdentity() {
        assertSame(BODY, BODY.scaled(1));
    }

    @Test
    @DisplayName("and a factor that is not a positive number is refused")
    void badFactors() {
        // A zero or negative text scale is a window with no text in it, which is
        // worse than any argument for tolerating it.
        assertThrows(IllegalArgumentException.class, () -> BODY.scaled(0));
        assertThrows(IllegalArgumentException.class, () -> BODY.scaled(-1));
        assertThrows(IllegalArgumentException.class, () -> BODY.scaled(Double.NaN));
    }

    /// §1.4 gives the token a **range**, and the renderer clamps to it rather
    /// than refusing: a text scale is a user setting, and a window that failed to
    /// open over one is worse than a window whose text is as large as the design
    /// system allows.
    @Test
    @DisplayName("§1.4's 90–150% is a clamp on the renderer, not a refusal")
    void theRangeIsAClamp() {
        var renderer = new io.github.digitalsmile.goldberry.widget.WidgetRenderer(
                java.util.List.of(), io.github.digitalsmile.goldberry.text.font.Fonts.bundled());

        assertEquals(1.0, renderer.textScale(), 1e-9, "and one is the default, so nothing moves");
        assertEquals(1.5, renderer.textScale(3.0).textScale(), 1e-9);
        assertEquals(0.9, renderer.textScale(0.1).textScale(), 1e-9);
        assertEquals(1.25, renderer.textScale(1.25).textScale(), 1e-9);
        assertTrue(
                renderer.textScale(1.5) == renderer,
                "it chains like `reducedMotion`, because that is the switch it is modelled on");
    }
}
