package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.widgets.text.Text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `docs/design-system.md` §1.2: "Every text/surface pair meets **WCAG 4.5:1**
/// […] Contrast is validated in CI against both themes."
///
/// This is that validation, and until now the sentence had nothing behind it.
/// [Badge] is what made it bite — a filled chip in one of §1.2's aurora hues is
/// the hardest contrast case the system has, because `--gb-text` on
/// `--gb-warning` is white on a pale yellow at 1.35:1 ([ADR-0087]).
///
/// ## What it measures, and why it resolves rather than reads
///
/// Every pair goes through the **real cascade** — the toolkit's base stylesheet,
/// the theme, the same [StyleResolver] a window uses — and the ratio is computed
/// from the `background` and `color` that come out. Parsing the CSS for
/// `var(--gb-badge-warning-text)` would check that a token has the value someone
/// wrote down; this checks what a user's eye actually receives, which is the only
/// version of the claim worth making. A rule that stops matching, a token that
/// stops resolving, and a theme that redefines an alias all fail here.
///
/// Small text throughout: §1.2 allows 3:1 only at ≥20px, and nothing in the
/// catalog draws text that large — `caption` is 11px and `body` is 13.
///
/// ## The exemption list is empty, and is asserted to be
///
/// Its first run found seven shipped `button` pairs below the floor —
/// `button.danger` on both themes and `button.primary` on light, the worst of
/// them at 2.95:1 — which [ADR-0088] fixed. [#KNOWN_FAILURES] is what held them
/// in the meantime, and it stays because it is the mechanism rather than the
/// debt: it is asserted as an **exact set**, so a pair that newly breaks cannot
/// be parked in it quietly, and a pair that gets fixed fails this test until it
/// is removed. A check narrowed to what already passes is
/// [ADR-0082](../../../../../../book/src/adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)'s
/// trap, so the sweep covers everything.
class ContrastTest {

    /// §1.2's floor for text under 20px, which is all of it.
    private static final double FLOOR = 4.5;

    /// The pairs that do not meet [#FLOOR], and there are none.
    ///
    /// Deliberately still here rather than deleted with the last entry. An empty
    /// list that is asserted equal to the measured failures is a stronger
    /// statement than no list at all: it says *nothing* is exempt, and it is the
    /// only place a future exemption could be added — where it would have to
    /// carry a reason and a measured ratio, in a diff, rather than being a
    /// `filter` somebody slipped into the sweep.
    private static final List<String> KNOWN_FAILURES = List.of();

    /// One thing a user reads: a widget, the pseudo-classes in force, and the
    /// stylesheet needed to put it in that state.
    private record Pair(String name, Widget widget, String extraCss) {
    }

    private static List<Pair> pairs() {
        var all = new ArrayList<Pair>();
        // The chip this change adds, in every variant §1.2 sanctions. A default
        // badge and five semantic ones -- these are the pairs ADR-0087 chose,
        // and this is what says the choice was right rather than plausible.
        all.add(new Pair("badge", new Badge("99"), ""));
        for (var variant : List.of("accent", "danger", "warning", "success", "info")) {
            all.add(new Pair("badge." + variant, new Badge("99").styled(variant), ""));
        }
        // Every button variant in every state that changes its fill. `:hover`
        // and `:active` are forced with an application rule rather than by
        // driving input, because what is under test is a colour pair and not the
        // route that reaches it.
        // `.ghost` is deliberately absent. Its fill is `transparent` and its
        // hover is a `#ffffff14` wash, and a translucent fill has no contrast
        // ratio at all -- the answer depends on what it is composited over. It
        // would score spuriously well here (alpha is ignored, so `transparent`
        // measures as black), which is worse than not measuring it: covering it
        // would be the check pretending to a guarantee it cannot make.
        for (var variant : List.of("", ".primary", ".danger")) {
            var widget = variant.isEmpty()
                    ? new Button("Save")
                    : new Button("Save").styled(variant.substring(1));
            all.add(new Pair("button" + variant, widget, ""));
            for (var state : List.of("hover", "active")) {
                // The forced rule copies the toolkit's own -- a `button:hover`
                // in the application layer wins over the base layer's, and
                // `background`/`color` are the only two properties that matter.
                all.add(new Pair("button" + variant + ":" + state, widget,
                        "button" + variant + " { background: var(--gb-button"
                                + (variant.isEmpty() ? "" : "-" + variant.substring(1))
                                + "-bg-" + state + ") }"));
            }
        }
        // A segmented control has **two** fills that carry text, and they are
        // picked by different rules: a resting label sits on the bar's plate, and
        // the selected one sits on the indicator's saturated accent, whose
        // foreground follows the fill rather than the theme (ADR-0087).
        //
        // The resting pair forces the bar's colour onto the segment, and that is
        // not a convenience: a segment's own fill is `transparent`, so what a
        // reader actually receives is the label over the *bar*. Measuring the
        // segment as it computes would score `transparent` as black and pass
        // spuriously -- the trap that keeps `button.ghost` out of this sweep.
        all.add(new Pair("segmented option", new Option("grid", "Grid"),
                "option { background: var(--gb-segmented-bg) }"));
        all.add(new Pair("segmented option:checked", new Option("list", "List"),
                selectedSegment("var(--gb-segmented-selected-bg)")));
        // The hover and press pairs are deliberately absent, on `button.ghost`'s
        // terms: a segment's feedback is a translucent wash over whichever
        // background is behind it, and a translucent fill has no contrast ratio
        // at all -- the answer depends on what it is composited over (ADR-0099).

        // The plain text pairs §1.2 names first: body text on each of the three
        // surfaces a window actually paints, and muted text on two of them.
        for (var surface : List.of("bg", "surface", "surface-2")) {
            all.add(new Pair("text on --gb-" + surface, new Text("Aa"),
                    "text { background: var(--gb-" + surface + "); color: var(--gb-text) }"));
            all.add(new Pair("muted text on --gb-" + surface, new Text("Aa"),
                    "text { background: var(--gb-" + surface + "); color: var(--gb-text-muted) }"));
        }
        return all;
    }

    /// A selected segment's pair: the label's colour, over the fill the indicator
    /// paints **behind** it.
    ///
    /// The two are on different boxes now — the pill travels and the label does
    /// not — so the pair has to be assembled rather than read off one node. It is
    /// still the pair a reader receives, which is the only version of §1.2's
    /// claim worth checking.
    ///
    /// Written as a rule rather than reached through `:checked`, for the reason
    /// the button states are: what is under test is a colour pair, not the route
    /// that reaches it — and the pseudo-class is mirrored onto an element by
    /// `WidgetRenderer`, which this test deliberately does not run.
    private static String selectedSegment(String background) {
        return "option { background: " + background
                + "; color: var(--gb-segmented-selected-text) }";
    }

    @Test
    @DisplayName("every text-on-fill pair the toolkit ships meets §1.2's 4.5:1, on both themes")
    void everyPairIsLegible() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var pair : pairs()) {
                var sheets = new ArrayList<>(Controls.stylesheets(theme));
                if (!pair.extraCss().isEmpty()) {
                    sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, pair.extraCss()));
                }
                var style = ComputedStyle.of(
                        new StyleResolver(sheets).resolve(new ElementTree(pair.widget()).root()),
                        CssLength.Context.DEFAULT);

                var name = themeName(theme) + " " + pair.name();
                var ratio = contrast(style.background(), style.color());
                report.append(String.format(Locale.ROOT, "%n  %-34s %5.2f:1", name, ratio));
                if (ratio < FLOOR) {
                    failures.add(name);
                }
            }
        }

        // Asserted as a set equality, not as a subset: this is what stops the
        // exemption list from being a place failures go to be forgotten.
        assertEquals(KNOWN_FAILURES, failures,
                () -> "the pairs below §1.2's " + FLOOR + ":1 floor are not the ones on record."
                        + " A pair that was fixed must come off KNOWN_FAILURES; a pair that"
                        + " newly broke must be fixed. Measured:" + report);
    }

    /// Nothing is exempt, stated separately from the sweep.
    ///
    /// The sweep would pass with a populated list — that is what the list is for.
    /// This is the assertion that says the list is *empty*, so re-exempting a pair
    /// fails a test whose name says what happened, rather than silently turning a
    /// green run into a differently-green run.
    @Test
    @DisplayName("nothing is exempt from §1.2")
    void nothingIsExempt() {
        assertTrue(KNOWN_FAILURES.isEmpty(),
                "a pair was exempted from §1.2's floor. If that is deliberate, this test"
                        + " is where the argument goes -- and ADR-0088 is the precedent for"
                        + " fixing it instead: every failure it found was a ramp that needed"
                        + " sliding, not redesigning.");
    }

    private static String themeName(Theme theme) {
        return theme == Theme.NORD_DARK ? "nord-dark" : "nord-light";
    }

    /// WCAG 2.1's contrast ratio, `(L1 + 0.05) / (L2 + 0.05)`.
    ///
    /// Alpha is ignored, and every pair here is opaque — a translucent fill has no
    /// single ratio, because what it composites over decides the answer.
    /// `--gb-selection` is exactly that case and is deliberately not swept.
    private static double contrast(int backgroundArgb, int colorArgb) {
        var a = luminance(backgroundArgb);
        var b = luminance(colorArgb);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /// WCAG 2.1 relative luminance, from an `0xAARRGGBB`.
    private static double luminance(int argb) {
        var r = linear(((argb >> 16) & 0xFF) / 255.0);
        var g = linear(((argb >> 8) & 0xFF) / 255.0);
        var b = linear((argb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
