package dev.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Corners;
import dev.goldberry.css.StyleElement;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.select.Selector;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// What a `tooltip` actually resolves to, against the metrics the design system
/// gives it: padding 8/12, radius 4, `caption`, a 500ms show delay and a 100ms
/// move-between delay.
///
/// ## Why this exists
///
/// The design system's `tooltip` row and the shipped rule once disagreed in
/// **three** places, and only one of the three had a comment saying so. The row
/// asked for padding 6/8, radius 4 and `caption`; the stylesheet wrote
/// `padding: 8px 12px`, `border-radius: 8px` and `font-size:
/// var(--gb-font-body)` — the last with an argument beside it and the first two
/// with nothing at all.
///
/// Nothing was watching. `SupportedPropertyTest` asks whether a declaration does
/// *something*, `ContrastTest` asks what colours measure, and no test asked
/// whether a metric is the metric the design system pinned. Three of the four
/// numbers in one row had drifted, and it was found by reading the row.
///
/// So this asserts the **shipped** numbers, which are now the design system's
/// numbers: the padding row was amended because 6 is not on the spacing ramp,
/// and the radius and the type rank were brought back to the row because the
/// design system is the authority. A further drift is a failing test rather than
/// a fourth silent departure.
///
/// Read more:
/// [The design system: component metrics](https://goldberry.dev/docs/guide/design-system.html#component-metrics).
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

    /// The spacing ramp, which is the fact the padding disagreement turns on.
    private static final List<Integer> RAMP = List.of(2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64);

    /// **Settled, and the row was amended.** The row said `6/8` and **6 is not on
    /// the spacing ramp**, which the design system allows no off-ramp values of —
    /// so the row as written could not be implemented without breaking a rule
    /// one section above it. That is a document bug rather than a design
    /// decision, and the shipped `8/12` is two legal steps.
    @Test
    @DisplayName("the padding is 8/12, because 6 is off the spacing ramp")
    void padding() {
        var style = styleOf("tooltip");

        assertEquals(Length.points(8), style.padding().top());
        assertEquals(Length.points(8), style.padding().bottom());
        assertEquals(Length.points(12), style.padding().left());
        assertEquals(Length.points(12), style.padding().right());

        for (var edge : List.of(style.padding().top(), style.padding().left())) {
            assertTrue(RAMP.contains((int) ((Length.Points) edge).value()), () -> edge + " is not on the spacing ramp");
        }
    }

    /// The radius, which the sheet wrote as 8 for a long time and now writes as
    /// 4. The design system groups radii as `4` (inputs, small controls) · `8`
    /// (buttons, cards) · `12` (dialogs, popovers, frost panels) and names no
    /// tooltip in any of them, so neither number *follows* — which is why it was
    /// a decision to take rather than an edit, and why the one taken is the one
    /// the tooltip row wrote down.
    @Test
    @DisplayName("the radius is 4, as the design system's row says")
    void radius() {
        assertEquals(Corners.all(4), styleOf("tooltip").decoration().corners());
    }

    /// The type rank, which the sheet wrote as `body` with an argument beside
    /// it: the type scale gives `caption` to secondary text *under* a control,
    /// where the reader has the control for context, and a tooltip is the only
    /// text on screen at the moment it is read. The argument stands and the
    /// design system is the authority, so it is the type scale's to answer.
    @Test
    @DisplayName("the type rank is caption, as the design system's row says")
    void typography() {
        var style = styleOf("tooltip");
        var caption = styleOf("badge").typography().size();

        assertEquals(caption, style.typography().size(), 1e-9, "a tooltip is `caption`, like a badge");
        assertEquals(11, caption, 1e-9, "and `caption` is 11");
    }

    /// The one number in the row that never drifted, asserted so the pair of
    /// delays stays a pair: the row gives two, and a delay is a metric, so both
    /// are tokens.
    @Test
    @DisplayName("and the two delays are both tokens")
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
