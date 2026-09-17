package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.select.Selector;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.layout.Length;

/// What a `tooltip` actually resolves to, against what `design-system.md` §3
/// says it should.
///
/// ## Why this exists
///
/// §3's `tooltip` row and the shipped rule disagree in **three** places, and only
/// one of the three had a comment saying so. The row reads *"padding 6/8; radius
/// 4; `caption`; delay 500ms show / 100ms move-between"*; the stylesheet writes
/// `padding: 8px 12px`, `border-radius: 8px` and `font-size:
/// var(--gb-font-body)` — the last with an argument beside it and the first two
/// with nothing at all.
///
/// Nothing was watching. `SupportedPropertyTest` asks whether a declaration does
/// *something*, `ContrastTest` asks what colours measure, and no test asks
/// whether a metric is the metric §3 pinned. Three of the four numbers in one row
/// had drifted, and it was found by reading the row ([ADR-0263]).
///
/// So this asserts the **shipped** numbers, and each one carries where it stands
/// in that argument: settled and amended into §3, or open and recorded in
/// `ARCHITECTURE.md` §17.1. Either way a further drift is a failing test rather
/// than a fourth silent departure.
class TooltipMetricsTest {

    private static ComputedStyle styleOf(String type) {
        return ComputedStyle.of(
                new StyleResolver(Controls.stylesheets(Theme.NORD_DARK)).resolve(new Probe(type)),
                CssLength.Context.DEFAULT);
    }

    /// A node that exists only to be styled — `SegmentedTest`'s, with no states.
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

    /// §1.3's legal ramp, which is the fact the padding disagreement turns on.
    private static final List<Integer> RAMP = List.of(2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64);

    /// **Settled, and §3 was amended.** The row said `6/8` and **6 is not on
    /// §1.3's ramp**, which the same document introduces with "no off-ramp
    /// values" — so the row as written could not be implemented without breaking
    /// a rule one section above it. That is a document bug rather than a design
    /// decision, and the shipped `8/12` is two legal steps.
    @Test
    @DisplayName("§3's padding, which is 8/12 because 6 is off §1.3's ramp")
    void padding() {
        var style = styleOf("tooltip");

        assertEquals(Length.points(8), style.padding().top());
        assertEquals(Length.points(8), style.padding().bottom());
        assertEquals(Length.points(12), style.padding().left());
        assertEquals(Length.points(12), style.padding().right());

        for (var edge : List.of(style.padding().top(), style.padding().left())) {
            assertTrue(
                    RAMP.contains((int) ((Length.Points) edge).value()), () -> edge + " is not on §1.3's legal ramp");
        }
    }

    /// §3's radius, which the sheet wrote as 8 for two hundred ADRs and now
    /// writes as 4 ([ADR-0380]). §1.5 groups radii as `4` (inputs, small
    /// controls) · `8` (buttons, cards) · `12` (dialogs, popovers, frost panels)
    /// and names no tooltip in any of them, so neither number *follows* — which
    /// is why it was a decision to take rather than an edit, and why the one
    /// taken is the one §3 wrote down.
    @Test
    @DisplayName("§3's radius, which is what ships")
    void radius() {
        assertEquals(Corners.all(4), styleOf("tooltip").decoration().corners());
    }

    /// §3's type rank, which the sheet wrote as `body` with an argument beside
    /// it: §1.4 gives `caption` to secondary text *under* a control, where the
    /// reader has the control for context, and a tooltip is the only text on
    /// screen at the moment it is read. The argument stands and the design
    /// system is the authority, so it is §1.4's to answer ([ADR-0380]).
    @Test
    @DisplayName("§3's `caption`, which is what ships")
    void typography() {
        var style = styleOf("tooltip");
        var caption = styleOf("badge").typography().size();

        assertEquals(caption, style.typography().size(), 1e-9, "a tooltip is `caption`, like a badge");
        assertEquals(11, caption, 1e-9, "and `caption` is 11");
    }

    /// The one number in the row that never drifted, asserted so the pair of
    /// delays stays a pair: §3 gives two and ADR-0262 built the second.
    @Test
    @DisplayName("and §3's two delays are both tokens now")
    void delays() {
        var sheets = Controls.stylesheets(Theme.NORD_DARK);
        var resolver = new StyleResolver(sheets);
        var root = new Probe("tooltip");

        assertEquals(
                500.0,
                ComputedStyle.durationMillis(resolver.customProperty(root, "--gb-tooltip-delay"))
                        .orElseThrow(),
                1e-9);
        assertEquals(
                100.0,
                ComputedStyle.durationMillis(resolver.customProperty(root, "--gb-tooltip-delay-move"))
                        .orElseThrow(),
                1e-9);
    }
}
