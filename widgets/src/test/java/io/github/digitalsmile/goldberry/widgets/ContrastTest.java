package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.contrast.Contrast;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.text.Text;

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
/// ## The text exemption list is empty, and is asserted to be
///
/// Its first run found seven shipped `button` pairs below the floor —
/// `button.danger` on both themes and `button.primary` on light, the worst of
/// them at 2.95:1 — which [ADR-0088] fixed. [#KNOWN_FAILURES] is what held them
/// in the meantime, and it stays because it is the mechanism rather than the
/// debt: it is asserted as an **exact set**, so a pair that newly breaks cannot
/// be parked in it quietly, and a pair that gets fixed fails this test until it
/// is removed. A check narrowed to what already passes is
/// ADR-0082's
/// trap, so the sweep covers everything.
///
/// ## The non-text lists are not empty, and that is the finding
///
/// §1.2 has a second floor — 3:1 for anything that is **not** text — and until
/// [ADR-0239] nothing measured it. Three sweeps do now: a mark against the box it
/// is drawn in, a ring against the surface behind it, and a control against that
/// surface by the better of its fill and its edge. Between them they found
/// nineteen pairs below the floor; three of those were §2.2's focus ring and
/// [ADR-0240] fixed them, so sixteen are held in [#MARKS_BELOW_FLOOR] and
/// [#BOUNDARIES_BELOW_FLOOR] on [#KNOWN_FAILURES]'s exact-set terms, and
/// [#RINGS_BELOW_FLOOR] is empty.
///
/// They are recorded rather than fixed, and the distinction is deliberate: every
/// one is a **theme colour**, and ADR-0088's move — slide the ramp until it
/// clears — changes what the toolkit looks like. That is a design decision with a
/// golden-image tail rather than something a test may take on its own authority.
/// The focus ring was the exception and is the precedent: its ramp had simply
/// been left behind when the accent's moved, so the fix was one token.
class ContrastTest {

    /// §1.2's floor for text under 20px, which is all of it — [Contrast]'s, so
    /// the number this asserts and the number an application audits against
    /// cannot drift apart ([ADR-0241]).
    private static final double FLOOR = Contrast.TEXT_FLOOR;

    /// §1.2's floor for anything that is **not** text — a glyph, a border, an
    /// indicator. Lower because a shape is not read letter by letter, and it is
    /// still a floor: a warning triangle nobody can see is §1.2's own failure
    /// mode, since the rule that forbids colour as the only carrier of meaning
    /// assumes the thing carrying it is visible.
    private static final double LINE_FLOOR = Contrast.NON_TEXT_FLOOR;

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
    private record Pair(String name, Widget widget, String extraCss) {}

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
            var widget = variant.isEmpty() ? new Button("Save") : new Button("Save").styled(variant.substring(1));
            all.add(new Pair("button" + variant, widget, ""));
            for (var state : List.of("hover", "active")) {
                // The forced rule copies the toolkit's own -- a `button:hover`
                // in the application layer wins over the base layer's, and
                // `background`/`color` are the only two properties that matter.
                all.add(new Pair(
                        "button" + variant + ":" + state,
                        widget,
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
        all.add(new Pair(
                "segmented option", new Option("grid", "Grid"), "option { background: var(--gb-segmented-bg) }"));
        all.add(new Pair(
                "segmented option:checked",
                new Option("list", "List"),
                selectedSegment("var(--gb-segmented-selected-bg)")));
        // The hover and press pairs are deliberately absent, on `button.ghost`'s
        // terms: a segment's feedback is a translucent wash over whichever
        // background is behind it, and a translucent fill has no contrast ratio
        // at all -- the answer depends on what it is composited over (ADR-0099).

        // The plain text pairs §1.2 names first: body text on each of the three
        // surfaces a window actually paints, and muted text on two of them.
        for (var surface : List.of("bg", "surface", "surface-2")) {
            all.add(new Pair(
                    "text on --gb-" + surface,
                    new Text("Aa"),
                    "text { background: var(--gb-" + surface + "); color: var(--gb-text) }"));
            all.add(new Pair(
                    "muted text on --gb-" + surface,
                    new Text("Aa"),
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
        return "option { background: " + background + "; color: var(--gb-segmented-selected-text) }";
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
        assertEquals(
                KNOWN_FAILURES,
                failures,
                () -> "the pairs below §1.2's " + FLOOR + ":1 floor are not the ones on record."
                        + " A pair that was fixed must come off KNOWN_FAILURES; a pair that"
                        + " newly broke must be fixed. Measured:" + report);
    }

    /// Every semantic hue, drawn as a **line** on every surface a window paints.
    ///
    /// The sweep above measures words on a fill. This measures the other thing
    /// §1.2's hues are used for and the thing nothing checked until §7's
    /// `message` drew one: a glyph and a border **on** the page.
    ///
    /// It is here because the first measurement disagreed with the theme. Both
    /// files documented `--gb-danger` as "what a label, an icon or a border is
    /// drawn in", and the hue is 2.46:1 on the dark theme's `--gb-surface` and
    /// 1.28:1 for `--nord13` on the light one — five of eight pairs below this
    /// floor, in a sentence that had been true-looking for months. The `-line`
    /// rank is the fix and this is what holds it.
    @Test
    @DisplayName("every semantic hue is visible as a line on every surface, on both themes")
    void everyLineIsVisible() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var hue : List.of("info", "success", "warning", "danger")) {
                // A banner sits on the page or inside a panel, and `--gb-surface-2`
                // is where a `card` or a `group-box` would put one.
                for (var surface : List.of("bg", "surface", "surface-2")) {
                    var css = "text { background: var(--gb-" + surface + "); color: var(--gb-" + hue + "-line) }";
                    var sheets = new ArrayList<>(Controls.stylesheets(theme));
                    sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
                    var style = ComputedStyle.of(
                            new StyleResolver(sheets).resolve(new ElementTree(new Text("Aa")).root()),
                            CssLength.Context.DEFAULT);

                    var name = themeName(theme) + " --gb-" + hue + "-line on --gb-" + surface;
                    var ratio = contrast(style.background(), style.color());
                    report.append(String.format(Locale.ROOT, "%n  %-46s %5.2f:1", name, ratio));
                    if (ratio < LINE_FLOOR) {
                        failures.add(name);
                    }
                }
            }
        }

        assertEquals(
                List.of(),
                failures,
                () -> "a semantic hue is invisible as a line on a surface the toolkit"
                        + " paints. The fix is the one ADR-0087 used for fills: move the"
                        + " hue's lightness in that theme until it clears, and write the"
                        + " measurement beside it. Measured:" + report);
    }

    /// Every semantic hue, drawn as **words** on every surface a window paints.
    ///
    /// The third sweep, and the one the `--gb-*-line` survey turned up
    /// ([ADR-0229]). `-line` is a **3:1** rank by construction, because §1.2's
    /// floor for a glyph or a border is 3:1 — and several widgets draw a semantic
    /// hue as *text*: a `field-message`, a `statistic`'s delta, a chart's failure
    /// line. Text is 4.5:1, and measured, both of the ranks that existed were
    /// below it: `--gb-danger` is 2.46:1 on the dark theme's `--gb-surface` and
    /// `--gb-danger-line` is 3.53:1.
    ///
    /// So a hue has a fourth rank and this is what holds it there. The entry that
    /// asked for this called it "a survey somebody has to do rather than a
    /// failure waiting to happen"; between this and [#noBareHueDrawsInk] it is a
    /// failure waiting to happen now.
    @Test
    @DisplayName("every semantic hue is legible as words on every surface, on both themes")
    void everyTextRankIsLegible() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var hue : List.of("info", "success", "warning", "danger")) {
                for (var surface : List.of("bg", "surface", "surface-2")) {
                    var css = "text { background: var(--gb-" + surface + "); color: var(--gb-" + hue + "-text) }";
                    var sheets = new ArrayList<>(Controls.stylesheets(theme));
                    sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
                    var style = ComputedStyle.of(
                            new StyleResolver(sheets).resolve(new ElementTree(new Text("Aa")).root()),
                            CssLength.Context.DEFAULT);

                    var name = themeName(theme) + " --gb-" + hue + "-text on --gb-" + surface;
                    var ratio = contrast(style.background(), style.color());
                    report.append(String.format(Locale.ROOT, "%n  %-46s %5.2f:1", name, ratio));
                    if (ratio < FLOOR) {
                        failures.add(name);
                    }
                }
            }
        }

        assertEquals(
                List.of(),
                failures,
                () -> "a semantic hue is unreadable as words on a surface the toolkit paints."
                        + " The fix is the one the -line rank used: move the hue's lightness in"
                        + " that theme until it clears, and write the measurement beside it."
                        + " Measured:" + report);
    }

    /// The HUD's two coloured readings, on the HUD's **own** plate.
    ///
    /// Measured separately from every other pair because the plate is separate
    /// from every other surface: `--gb-hud-bg` is the same in both themes, since
    /// it lies over the application's own colours and the toolkit does not know
    /// what those are. A theme-varying hue is therefore the wrong thing to draw
    /// on it — the light theme's danger is a *dark* red, and a dark red on a
    /// near-black plate is not a warning, it is an absence.
    ///
    /// The plate is written out opaque here. It ships at 90% alpha, and that is
    /// **not** `button.ghost`'s situation: a fully transparent fill has no ratio
    /// at all because the answer depends entirely on what is behind it, where a
    /// 90% plate moves the composite by a fraction of a step. Naming the opaque
    /// colour says which number is being asserted rather than letting the
    /// measurement quietly ignore an alpha channel.
    @Test
    @DisplayName("both of the HUD's coloured readings are legible on its own plate")
    void everyHudReadingIsLegible() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var reading : List.of("warning", "danger")) {
                var css = "text { background: #1c212a; color: var(--gb-hud-" + reading + ") }";
                var sheets = new ArrayList<>(Controls.stylesheets(theme));
                sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
                var style = ComputedStyle.of(
                        new StyleResolver(sheets).resolve(new ElementTree(new Text("Aa")).root()),
                        CssLength.Context.DEFAULT);

                var name = themeName(theme) + " --gb-hud-" + reading + " on the plate";
                var ratio = contrast(style.background(), style.color());
                report.append(String.format(Locale.ROOT, "%n  %-46s %5.2f:1", name, ratio));
                if (ratio < FLOOR) {
                    failures.add(name);
                }
            }
        }

        assertEquals(List.of(), failures, () -> "a HUD reading is unreadable on the HUD's plate. Measured:" + report);
    }

    /// **No widget draws ink in a bare semantic hue**, which is what turns the
    /// three sweeps above into a guarantee rather than a sample.
    ///
    /// The sweeps measure *tokens*. This measures the stylesheet: a rule that
    /// draws `color:` or `border-color:` with `var(--gb-danger)` rather than one
    /// of its ranks is a widget that is not covered by any of them, and finding
    /// those was a survey somebody had to do by hand ([ADR-0229]). It found five
    /// rules, three of them below §1.2's floor.
    ///
    /// **Only `color` and `border-color`.** A `background` in the bare hue is a
    /// fill, which is the rank the hue itself *is*, and is measured by the
    /// text-on-fill sweep at the top of this class.
    @Test
    @DisplayName("no rule draws a line or a word in a bare semantic hue")
    void noBareHueDrawsInk() throws java.io.IOException {
        var css = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/io/github/digitalsmile/goldberry/widgets/controls.css"));
        var offenders = new ArrayList<String>();
        var pattern = java.util.regex.Pattern.compile(
                "(color|border-color)\\s*:\\s*var\\(--gb-(info|success|warning|danger)\\)");
        var lines = css.split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            var matcher = pattern.matcher(lines[i]);
            if (matcher.find()) {
                offenders.add("controls.css:" + (i + 1) + "  " + lines[i].trim());
            }
        }

        assertEquals(
                List.of(),
                offenders,
                () -> "these rules draw ink in a bare semantic hue, which is a fill rank:"
                        + " a stroke wants --gb-<hue>-line (3:1) and words want --gb-<hue>-text"
                        + " (4.5:1), and neither is what the bare hue measures."
                        + String.join(
                                "", offenders.stream().map(o -> "\n  " + o).toList()));
    }

    /// Nothing is exempt, stated separately from the sweep.
    ///
    /// The sweep would pass with a populated list — that is what the list is for.
    /// This is the assertion that says the list is *empty*, so re-exempting a pair
    /// fails a test whose name says what happened, rather than silently turning a
    /// green run into a differently-green run.
    /// What the non-text sweeps below measure and find wanting, **today**.
    ///
    /// These are three lists rather than one for [#KNOWN_FAILURES]'s reason,
    /// sharpened: each is asserted as an **exact set** against its own sweep, so
    /// a pair that newly breaks cannot be parked in one quietly and a pair that
    /// gets fixed fails the test until it is taken out. Every entry carries the
    /// measurement that put it there.
    ///
    /// ## They are debt, and they are not this change's to pay
    ///
    /// [#KNOWN_FAILURES] is empty and asserted to be, because ADR-0088 *fixed*
    /// the seven pairs the text sweep found. The same move is not available here.
    /// Every one of these is a **theme colour**, and sliding a ramp to clear 3:1
    /// changes what the toolkit looks like — the focus ring on the light theme
    /// would have to go markedly darker, and an unchecked checkbox needs a fill
    /// that is not literally `--gb-surface-2`. That is a design decision with a
    /// golden-image tail, and it is not one a test may take on its own authority.
    ///
    /// What this change owes, and delivers, is the **measurement**: the entry
    /// that asked for it said "none of them is measured, and ADR-0088's argument
    /// that the accent ramp did not need to move rests on exactly that unenforced
    /// number". It is enforced now, and it says the ramp does need to move.
    ///
    /// **The rings list is empty, and was not.** §2.2's focus ring was below 3:1
    /// on every surface of the light theme, which is the one mark in the system
    /// with no second means of being seen — [ADR-0240] fixed it by letting the
    /// ring follow the accent, as the dark theme's always had. It is kept as an
    /// empty list for [#KNOWN_FAILURES]'s reason: a ring that newly breaks has
    /// somewhere it would have to be written down.
    private static final List<String> RINGS_BELOW_FLOOR = List.of();

    /// A mark on the box it is drawn in — see [#RINGS_BELOW_FLOOR] for why these
    /// are recorded rather than fixed.
    private static final List<String> MARKS_BELOW_FLOOR = List.of(
            // 1.35:1. A near-white thumb on the light theme's grey groove, and
            // the worst mark measurement in either theme.
            "nord-light slider thumb",
            // 2.98:1, three times over: all three are `--gb-accent` on
            // `--gb-border`, which is one pair wearing three names. It misses by
            // 0.02, which is a ramp that needs sliding rather than rethinking.
            "nord-light slider fill",
            "nord-light progress fill",
            "nord-light knob arc");

    /// A control against the surface behind it, by the better of fill and edge —
    /// see [#RINGS_BELOW_FLOOR] for why these are recorded rather than fixed.
    ///
    /// All twelve, which is both themes × both controls × all three surfaces, and
    /// the shape of it is one fact: `--gb-checkbox-bg` **is** `--gb-surface-2` in
    /// the dark theme, so on a `group-box` an unchecked box differs from its
    /// backdrop by nothing at all and is held up entirely by a 1.17:1 edge.
    private static final List<String> BOUNDARIES_BELOW_FLOOR = List.of(
            // fill 1.45 / edge 1.69, 1.17 / 1.36, 1.00 / 1.17
            "nord-dark checkbox (unchecked) on --gb-bg",
            "nord-dark checkbox (unchecked) on --gb-surface",
            "nord-dark checkbox (unchecked) on --gb-surface-2",
            "nord-dark radio (unchecked) on --gb-bg",
            "nord-dark radio (unchecked) on --gb-surface",
            "nord-dark radio (unchecked) on --gb-surface-2",
            // fill 1.06 / edge 1.17, 1.22 / 1.35, 1.00 / 1.11
            "nord-light checkbox (unchecked) on --gb-bg",
            "nord-light checkbox (unchecked) on --gb-surface",
            "nord-light checkbox (unchecked) on --gb-surface-2",
            "nord-light radio (unchecked) on --gb-bg",
            "nord-light radio (unchecked) on --gb-surface",
            "nord-light radio (unchecked) on --gb-surface-2");

    /// The **non-text** half of §1.2, and the one the entry that asked for this
    /// called "not checked at all" ([ADR-0239]).
    ///
    /// ## What counts as the background of a mark drawn onto its own box
    ///
    /// That was the open question, and the tokens answer it: a mark is coloured
    /// by the `color` of the box it is drawn in and the box supplies its own
    /// `background`, so the pair is **one element's two properties** and not a
    /// composite of anything. A checked checkbox's tick is
    /// `--gb-checkbox-mark-checked` on `--gb-checkbox-bg-checked`, both set by the
    /// same `check-indicator:checked` rule; a slider's thumb sits on its groove;
    /// a knob's arc on its track. Nothing here needs the painted frame.
    ///
    /// Which is also the line this sweep stops at. A mark on a *translucent* fill
    /// has no single ratio, for `button.ghost`'s reason, and none of these is
    /// translucent.
    private static final List<String[]> MARKS = List.of(
            // §2.1's controls, each in the state where its mark is showing. The
            // unchecked halves are not marks at all -- `--gb-checkbox-mark` is
            // `transparent`, because an unchecked box draws no tick.
            new String[] {"checkbox tick", "checkbox-bg-checked", "checkbox-mark-checked"},
            new String[] {"radio dot", "radio-bg-checked", "radio-dot-checked"},
            new String[] {"toggle thumb (off)", "toggle-track-bg", "toggle-thumb-bg"},
            new String[] {"toggle thumb (on)", "toggle-track-bg-checked", "toggle-thumb-bg-checked"},
            // §3's ranges. The groove and the track are both `--gb-border`, and the
            // thumb and the fill are what has to be visible against them.
            new String[] {"slider thumb", "slider-track-bg", "slider-thumb-bg"},
            new String[] {"slider fill", "slider-track-bg", "slider-fill-bg"},
            new String[] {"progress fill", "progress-track-bg", "progress-fill-bg"},
            // The knob draws two marks on two different boxes: the arc rides the rim
            // over the track, and the pointer is drawn on the dial's body.
            new String[] {"knob arc", "knob-track", "knob-arc"},
            new String[] {"knob pointer", "knob-bg", "knob-pointer"});

    @Test
    @DisplayName("every mark a control draws is visible on the box it is drawn in, on both themes")
    void everyMarkIsVisible() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var mark : MARKS) {
                var name = themeName(theme) + " " + mark[0];
                var ratio = ratio(theme, "var(--gb-" + mark[1] + ")", "var(--gb-" + mark[2] + ")");
                report.append(String.format(Locale.ROOT, "%n  %-44s %5.2f:1", name, ratio));
                if (ratio < LINE_FLOOR) {
                    failures.add(name);
                }
            }
        }

        assertEquals(
                MARKS_BELOW_FLOOR,
                failures,
                () -> "a control draws a mark nobody can see on the box it is drawn in."
                        + " §1.2's floor for something that is not text is " + LINE_FLOOR
                        + ":1, and ADR-0088 is the precedent for the fix: slide the ramp"
                        + " in that theme until it clears, and write the measurement"
                        + " beside it. Measured:" + report);
    }

    /// The rings: what the toolkit draws **onto a window's surface** with no
    /// plate of its own, so the surface behind is the whole of the backdrop.
    ///
    /// Three measurements each rather than one, because a control may sit on the
    /// page, in a `card` or inside a `group-box` — the shape
    /// [#everyLineIsVisible] already uses. Neither of these has a second means of
    /// being seen, which is what separates them from [#BOUNDARIES] below: a focus
    /// ring that cannot be made out is not a control that is merely hard to find,
    /// it is a keyboard user with no idea where they are.
    private static final List<String[]> RINGS = List.of(
            // §2.2's ring, drawn *outside* the control at a 2px offset, so what it
            // lands on is the surface behind rather than the control's own fill.
            new String[] {"focus ring", "focus"},
            // §3's spinner is a stroked ring in the accent, drawn straight onto
            // whatever is behind it.
            new String[] {"spinner ring", "accent"});

    /// The control boundaries, as `(fill, border)` against the surface.
    ///
    /// ## Why this is a *maximum* and the sweeps above are not
    ///
    /// §1.2's non-text floor exists so a component can be identified, and a
    /// control offers two means of it at once: a fill that differs from the
    /// surface, and an edge drawn around it. WCAG asks that *some* means clears
    /// the floor, not that every one does — a filled button with no border is not
    /// a failure for having no border. So the measurement is the **better of the
    /// two**, and a control fails only when neither carries it.
    ///
    /// Measuring the two separately was the first version and it is wrong in a
    /// way that matters: it would have reported `--gb-border` failing on every
    /// surface in both themes, which is a *decorative divider* doing exactly what
    /// a 1px separator is supposed to do — subtle. The same token is also a
    /// control's edge, and only in that role is it held to 3:1.
    private static final List<String[]> BOUNDARIES = List.of(
            new String[] {"checkbox (unchecked)", "checkbox-bg", "border"},
            new String[] {"radio (unchecked)", "radio-bg", "border"});

    @Test
    @DisplayName("every ring the toolkit draws is visible on every surface, on both themes")
    void everyRingIsVisible() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var ring : RINGS) {
                for (var surface : List.of("bg", "surface", "surface-2")) {
                    var name = themeName(theme) + " " + ring[0] + " on --gb-" + surface;
                    var ratio = ratio(theme, "var(--gb-" + surface + ")", "var(--gb-" + ring[1] + ")");
                    report.append(String.format(Locale.ROOT, "%n  %-52s %5.2f:1", name, ratio));
                    if (ratio < LINE_FLOOR) {
                        failures.add(name);
                    }
                }
            }
        }

        assertEquals(
                RINGS_BELOW_FLOOR,
                failures,
                () -> "something the toolkit draws as a ring is invisible on a surface it"
                        + " paints. §1.2's floor for what is not text is " + LINE_FLOOR
                        + ":1. Measured:" + report);
    }

    @Test
    @DisplayName("every control is distinguishable from every surface, by its fill or its edge")
    void everyControlIsDistinguishable() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var control : BOUNDARIES) {
                for (var surface : List.of("bg", "surface", "surface-2")) {
                    var backdrop = "var(--gb-" + surface + ")";
                    var fill = ratio(theme, backdrop, "var(--gb-" + control[1] + ")");
                    var edge = ratio(theme, backdrop, "var(--gb-" + control[2] + ")");
                    var name = themeName(theme) + " " + control[0] + " on --gb-" + surface;
                    report.append(String.format(Locale.ROOT, "%n  %-48s fill %5.2f:1  edge %5.2f:1", name, fill, edge));
                    if (Math.max(fill, edge) < LINE_FLOOR) {
                        failures.add(name);
                    }
                }
            }
        }

        assertEquals(
                BOUNDARIES_BELOW_FLOOR,
                failures,
                () -> "a control cannot be told from the surface it sits on by either its"
                        + " fill or its edge, so §1.2's " + LINE_FLOOR + ":1 is met by"
                        + " neither means. Measured:" + report);
    }

    /// One measurement: two token expressions through the real cascade.
    ///
    /// A [Text] carrier and a forced rule, which is [#everyLineIsVisible]'s shape
    /// and for its reason — what is under test is a colour pair, not the route
    /// that reaches it, and the pseudo-classes these pairs live behind are
    /// mirrored onto an element by `WidgetRenderer`, which this test deliberately
    /// does not run.
    private static double ratio(Theme theme, String background, String color) {
        var sheets = new ArrayList<>(Controls.stylesheets(theme));
        sheets.add(Stylesheet.parse(
                CascadeLayer.APPLICATION, "text { background: " + background + "; color: " + color + " }"));
        var style = ComputedStyle.of(
                new StyleResolver(sheets).resolve(new ElementTree(new Text("Aa")).root()), CssLength.Context.DEFAULT);
        return contrast(style.background(), style.color());
    }

    @Test
    @DisplayName("nothing is exempt from §1.2")
    void nothingIsExempt() {
        assertTrue(
                KNOWN_FAILURES.isEmpty(),
                "a pair was exempted from §1.2's floor. If that is deliberate, this test"
                        + " is where the argument goes -- and ADR-0088 is the precedent for"
                        + " fixing it instead: every failure it found was a ramp that needed"
                        + " sliding, not redesigning.");
    }

    private static String themeName(Theme theme) {
        return theme == Theme.NORD_DARK ? "nord-dark" : "nord-light";
    }

    /// WCAG 2.1's contrast ratio, from [Contrast] rather than from a copy here.
    ///
    /// It was nine private lines in this file until [ADR-0241] made it something
    /// an application could call, and this now goes through the same code — which
    /// is the point rather than tidiness: an audit an application runs and a sweep
    /// CI runs that disagreed about the arithmetic would be worse than either
    /// alone.
    ///
    /// Alpha is ignored, and every pair here is opaque — a translucent fill has no
    /// single ratio, because what it composites over decides the answer.
    /// `--gb-selection` is exactly that case and is deliberately not swept.
    private static double contrast(int backgroundArgb, int colorArgb) {
        return Contrast.ratio(backgroundArgb, colorArgb);
    }
}
