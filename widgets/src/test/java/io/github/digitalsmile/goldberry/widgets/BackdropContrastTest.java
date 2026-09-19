package io.github.digitalsmile.goldberry.widgets;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.contrast.Contrast;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §1.2, measured on the **painted frame** rather than on the cascade — the
/// check [ContrastTest] says it cannot be ([ADR-0431]).
///
/// ## What the cascade cannot answer
///
/// [ContrastTest] resolves a `background` and a `color` and divides. That works
/// for every opaque pair in the system and for nothing else, and it says so in
/// four separate places: `button.ghost` is left out because its fill is
/// `transparent`, `--gb-selection` is left out because it is a wash, a segment's
/// hover and press are left out for the same reason, and `button.link` is
/// measured as ink alone because the variant itself cannot be swept. Each of
/// those exclusions is correct and each is a hole: a translucent fill *does* have
/// a contrast ratio, it just does not have **one** — it has as many as there are
/// things it can be composited over.
///
/// The exclusions are also load-bearing rather than fussy. `Contrast.ratio`
/// ignores alpha, so measuring `transparent` would score it as **black** and hand
/// `button.ghost` a passing 4.5:1 it has not got — a check pretending to a
/// guarantee, which is worse than the absence of one.
///
/// So this renders. A real widget tree, the real base stylesheet, the real theme,
/// the real rasterizer, and then [TestFrames.Target#pixel] — and the number that
/// comes back has already had the compositing done to it by the thing that will
/// do it on a user's screen. Nothing here reimplements `src-over`, which is the
/// other way this could have been written and the way that would have been wrong:
/// a hand-rolled `over(argb, backdrop)` is a second opinion about blending, and
/// the first opinion is the one that ships.
///
/// ## The backdrop list, and why it is finite
///
/// "A ghost button on any surface" is how `controls.css` describes the variant,
/// and a check over *any* surface is a check nobody can satisfy: an application
/// may paint a photograph behind a toolbar, and no token the toolkit ships can be
/// held responsible for it. What the toolkit **can** be held to is the surfaces it
/// paints itself, and there are five of them — [#BACKDROPS] — every one a
/// `--gb-surface*` or `--gb-bg` declaration in the shipped base stylesheet.
///
/// That list is pinned rather than counted, and [#theSurfacesAreTheOnesTheThemesDeclare]
/// is what keeps it honest: a theme that declares a sixth surface token fails
/// that test until the sweep covers it. An application's own backdrop is the
/// application's own business, and `Contrast.ratio` is public for exactly that
/// ([ADR-0241]).
///
/// Each backdrop is a **stack** and not a token, which is the second thing the
/// cascade could not do: `--gb-surface-sunken` is `rgba(0, 0, 0, 0.22)` on the
/// dark theme and `rgba(0, 0, 0, 0.07)` on the light one, so a text field's fill
/// is itself a wash over whatever holds the field. Resolving it gives a colour
/// with an alpha channel and no answer; painting it gives a pixel — `#3b4252`
/// becomes `#2e3440` on the dark theme and `#ffffff` becomes `#ededed` on the
/// light one, and neither number is written down anywhere.
///
/// ## What the first run found, which was nothing — and what that cost
///
/// Forty pairs, and every one of them clears 4.5:1. The four exclusions
/// [ContrastTest] carries were **correct to make and correct to leave**: the
/// colours behind them were fine, and there was simply no way to say so.
///
/// Two margins are worth writing down, because "passes" is not the same as
/// "comfortable" and a ramp that moves later should know what it is standing on:
///
/// - `button.ghost:active` on the dark theme's `--gb-surface-2` and
///   `--gb-surface-raised` is **4.79:1**, the tightest pair here. Both surfaces
///   are `--nord2`, so a pressed ghost button in a card, a dialog or a toast is
///   0.29 above the floor.
/// - `--gb-selection` on the same two is **5.41:1**. `controls.css` says of it
///   that "the label under it does not need a foreground of its own the way a
///   segment's does on its opaque pill" — a claim that had never been measured,
///   and is now true by 0.91 rather than by assertion.
///
/// The first draft of this file **did** report three failures, and they were its
/// own: `INSET` sampled three pixels into an unpadded row and took a bite out of
/// the `A`, which reads as ink-over-fill and moved `--gb-selection` on
/// `--gb-surface` from 5.92:1 to 4.14:1. A pixel test that samples the wrong pixel
/// is more confident and more wrong than the cascade test it replaces, so the
/// flat-region guard in [#shoot] is not tidiness — it is the thing that makes the
/// rest of this file believable.
class BackdropContrastTest {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 80;

    /// Where inside a probe's rectangle the fill is sampled.
    ///
    /// Three pixels in from the leading edge at the vertical centre: inside the
    /// rounded corner at the box's widest point, and well before the label, which
    /// starts after §3's 12px of padding. A corner sample would land on the
    /// antialiasing of the radius and measure a colour nothing is drawn in.
    private static final int INSET = 3;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// One opaque backdrop the toolkit paints, as the stack that produces it.
    ///
    /// @param name  what to call it in a failure message
    /// @param under what the plate is painted on, which matters only when the
    ///              plate is translucent — and one of them is
    /// @param plate the surface under test
    private record Backdrop(String name, String under, String plate) {}

    /// The five surfaces a window paints, each with what is behind it.
    ///
    /// The pairings are the ones the catalog actually produces rather than every
    /// combination: `--gb-surface-2` is a `group-box-title` band inside a
    /// `group-box`, so what is behind it is `--gb-surface`; `--gb-surface-sunken`
    /// is a `text-input`, which is in a form, which is in a panel. A `card` is on
    /// the page. Getting these wrong would matter for exactly one entry — the
    /// sunken one, the only translucent plate — and that is the entry the pairing
    /// exists for.
    private static final List<Backdrop> BACKDROPS = List.of(
            new Backdrop("--gb-bg", "var(--gb-bg)", "var(--gb-bg)"),
            new Backdrop("--gb-surface", "var(--gb-bg)", "var(--gb-surface)"),
            new Backdrop("--gb-surface-2", "var(--gb-surface)", "var(--gb-surface-2)"),
            new Backdrop("--gb-surface-raised", "var(--gb-bg)", "var(--gb-surface-raised)"),
            new Backdrop("--gb-surface-sunken", "var(--gb-surface)", "var(--gb-surface-sunken)"));

    /// One thing to draw over a backdrop, and the rule that puts it in the state
    /// being measured.
    ///
    /// The states are **forced with an application rule** rather than driven
    /// through the router, which is [ContrastTest]'s choice and for its reason:
    /// what is under test is a colour pair, not the route that reaches it. What
    /// is *not* faked is the compositing — the rule names the same token the
    /// toolkit's own `button.ghost:hover` names, and the rasterizer does the rest.
    private record Probe(String name, Widget widget, String css) {}

    private static List<Probe> probes() {
        var ghost = new Button("Save").styled("ghost").id("probe");
        return List.of(
                new Probe("button.ghost", ghost, ""),
                new Probe("button.ghost:hover", ghost, "#probe { background: var(--gb-overlay-hover) }"),
                new Probe("button.ghost:active", ghost, "#probe { background: var(--gb-overlay-active) }"),
                // §3's "selection = `--gb-selection` full-row", which `list-row`,
                // `tree-row` and a chosen `option` all paint and which keeps
                // `--gb-text` on top of it in every one of them. A plain box
                // wearing the same two declarations is the pair those three rows
                // produce, without needing a model to put rows in.
                new Probe(
                        "--gb-selection",
                        new Column(List.of(new Text("Aa")), id("probe")),
                        // §3's `list` row: "height 32 (26); padding-x 12". The
                        // padding is the row's own metric and it is also what
                        // keeps `INSET` off the glyphs -- see `sampleOf`.
                        "#probe { background: var(--gb-selection); color: var(--gb-text);"
                                + " height: 32px; padding: 0 12px; justify-content: center }"));
    }

    /// What one render of one probe over one backdrop produced.
    ///
    /// @param plate the painted pixel of the surface, beside the probe
    /// @param fill  the painted pixel inside the probe
    /// @param ink   the probe's resolved `color`, which is opaque in every case
    ///              here and therefore has nothing to composite
    private record Shot(int plate, int fill, int ink) {}

    private static Shot shoot(Theme theme, Backdrop backdrop, Probe probe) {
        var sheets = new ArrayList<>(Controls.stylesheets(theme));
        sheets.add(Stylesheet.parse(
                CascadeLayer.APPLICATION, """
                #scene { padding: 0; flex-direction: column; align-items: stretch;
                         height: 100%%; background: %s }
                #plate { padding: 16px; flex-direction: column; align-items: stretch;
                         height: 100%%; background: %s }
                """.formatted(backdrop.under(), backdrop.plate()) + probe.css()));
        var renderer = new WidgetRenderer(sheets, TestFont.get());
        var scene = new Column(List.of(new Column(List.of(probe.widget()), id("plate"))), id("scene"));

        // Two targets, and deliberately not one. The rectangles come from a
        // layout pass and the colours from a paint, and running both into the
        // same frame would composite a translucent wash over itself -- which is
        // precisely the quantity being measured, doubled. The layout is a pure
        // function of the tree and the size, so the two agree by construction.
        var rect = rectangleOf(renderer, scene);
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        int plate;
        int fill;
        try {
            BoxPainter.paint(target.frame(), renderer.render(new ElementTree(scene)));
        } finally {
            target.end();
        }
        plate = target.pixel(2, 2);
        var x = (int) rect.left() + INSET;
        var y = (int) (rect.top() + rect.height() / 2);
        fill = target.pixel(x, y);

        assertEquals(0xFF, plate >>> 24, "the plate must be opaque where it is measured, and it is not");
        assertEquals(0xFF, fill >>> 24, "the probe's fill must be opaque where it is measured, and it is not");
        // **The guard that caught the first draft of this test.** A sample that
        // lands on a glyph measures ink over fill rather than fill, and reports a
        // ratio that is wrong in the flattering direction -- the first run put
        // `INSET` three pixels into an unpadded row and took a bite out of the
        // `A`, which moved a 3.51:1 pair to 4.14:1 and would have been believed.
        // Ink is never flat, so two neighbours that agree are two pixels of
        // surface.
        assertEquals(
                fill,
                target.pixel(x + 1, y),
                () -> "the fill sample is not on a flat region, so it is on something drawn over"
                        + " the fill rather than on the fill. Move INSET, or give the probe the"
                        + " padding its real rule has.");

        return new Shot(plate, fill, inkOf(theme, sheets, scene));
    }

    /// Where `#probe` was laid out.
    ///
    /// A layout pass of its own, painting nothing — [RenderTree#update] settles
    /// Yoga and [HitTest#capture] reads the rectangles off it.
    private static HitTest.Region rectangleOf(WidgetRenderer renderer, Widget scene) {
        var tree = new ElementTree(scene);
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            var regions = HitTest.capture(render);
            var probe = probeElement(tree.root());
            return regions.stream()
                    .filter(region -> region.owner() == probe)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("#probe was not painted"));
        } finally {
            target.end();
        }
    }

    private static Element probeElement(Element element) {
        if (element.widget() instanceof Styled styled && "probe".equals(styled.id())) {
            return element;
        }
        for (var child : element.children()) {
            var found = probeElement(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// The probe's own `color`, from the cascade.
    ///
    /// **From the cascade on purpose, and it is the only thing here that is.** Ink
    /// is opaque in every pair in the toolkit — `--gb-text` and the ranks around
    /// it are palette entries, never washes — so there is nothing about it that a
    /// paint would decide and a resolution would not. What needed the painted
    /// frame was the *backdrop*, and that is what comes off the pixels. Reading
    /// the ink out of the frame instead would mean sampling the inside of a glyph,
    /// which at 13px is a handful of pixels that are all partly the backdrop.
    private static int inkOf(Theme theme, List<Stylesheet> sheets, Widget scene) {
        var probe = probeElement(new ElementTree(scene).root());
        var style = ComputedStyle.of(new StyleResolver(sheets).resolve(probe), CssLength.Context.DEFAULT);
        var ink = style.color();
        assertTrue(Contrast.isOpaque(ink), "this sweep assumes opaque ink, and " + name(ink) + " is not");
        return ink;
    }

    /// **The guarantee this test exists to make.** A ghost button's label, and a
    /// selected row's, over the pixel actually behind it.
    ///
    /// Forty measurements: five surfaces × four probes × two themes, and every one
    /// of them is a pair no check in the repo could previously express.
    @Test
    @DisplayName("every translucent fill leaves its label above §1.2's 4.5:1, on every surface the toolkit paints")
    void everyLabelIsLegibleOverWhatIsBehindIt() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var probe : probes()) {
                for (var backdrop : BACKDROPS) {
                    var shot = shoot(theme, backdrop, probe);
                    var ratio = Contrast.ratio(shot.fill(), shot.ink());
                    var name = themeName(theme) + " " + probe.name() + " on " + backdrop.name();
                    report.append(
                            String.format(Locale.ROOT, "%n  %-48s %5.2f:1  over %s", name, ratio, name(shot.fill())));
                    if (ratio < Contrast.TEXT_FLOOR) {
                        failures.add(name);
                    }
                }
            }
        }

        assertEquals(
                List.of(),
                failures,
                () -> "a label is unreadable over the colour its own fill composites to."
                        + " This is measured from the painted frame, so the fix is not a token"
                        + " swap in isolation: either the wash moves or the ink does, and the"
                        + " number beside each pair is what it has to clear. Measured:" + report);
    }

    /// A translucent state that composites to **nothing** is a state nobody can
    /// see, and it is the failure translucency invites.
    ///
    /// The shape of it is concrete: `--gb-overlay-hover` is a white wash on the
    /// dark theme and a black one on the light theme, and the light theme's
    /// `--gb-surface` and `--gb-surface-raised` are both literally `#ffffff`. Had
    /// the light theme taken the dark one's white wash — which is the obvious
    /// thing to do when a token is "the overlay", and what a single shared token
    /// would have forced — hovering a ghost button on a panel, a card, a dialog or
    /// a toast would have changed not one pixel. Nothing in the cascade could have
    /// reported that, because the declaration would have been present and correct
    /// on both.
    ///
    /// **No floor beyond "it moved".** §2.1 asks hover for "one surface step" and
    /// designs it to be subtle; §1.2's 3:1 is about telling a *component* from its
    /// background, not about telling a hover from a rest. Asserting 3:1 here would
    /// be inventing a rule the design system does not hold itself to and failing
    /// every state in the catalog on the strength of it. What is asserted is the
    /// claim the design system does make — that the surface steps — and the exact
    /// ratios are printed so the size of each step is on the record.
    @Test
    @DisplayName("every translucent state actually changes the pixel it is drawn over")
    void everyStateMovesTheSurface() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var probe : probes()) {
                if (probe.css().isEmpty()) {
                    // A resting ghost is `transparent` by design: it is *meant* to
                    // be indistinguishable from its surface, which is what the
                    // variant is for. Its label is what carries it, and the sweep
                    // above is where that is held.
                    continue;
                }
                for (var backdrop : BACKDROPS) {
                    var shot = shoot(theme, backdrop, probe);
                    var ratio = Contrast.ratio(shot.plate(), shot.fill());
                    var name = themeName(theme) + " " + probe.name() + " on " + backdrop.name();
                    report.append(String.format(
                            Locale.ROOT,
                            "%n  %-48s %5.3f:1  %s -> %s",
                            name,
                            ratio,
                            name(shot.plate()),
                            name(shot.fill())));
                    if (shot.plate() == shot.fill()) {
                        failures.add(name);
                    }
                }
            }
        }

        assertEquals(
                List.of(),
                failures,
                () -> "a wash composited to exactly the colour underneath it, so the state it"
                        + " signals is invisible on that surface. A wash is the theme's, and so"
                        + " is the direction it has to go: a light theme darkens and a dark theme"
                        + " lightens, and a wash that matches its own surface is the one that"
                        + " does neither. Measured:" + report);
    }

    /// The backdrop list is the themes', not this file's.
    ///
    /// [#BACKDROPS] is finite because the toolkit's surfaces are finite, and that
    /// is only true for as long as nobody adds a sixth without telling anyone.
    /// This reads both theme files for their `--gb-surface*` declarations and
    /// asserts the set — the same technique [ContrastTest]'s `noBareHueDrawsInk`
    /// uses, and for the same reason: a claim about *what the stylesheet contains*
    /// has to be checked against the stylesheet.
    ///
    /// `--gb-surface-2` and the two directions are all here. They are different
    /// colours in different places — `-2` is a step down on the light theme and a
    /// step up on the dark one, which is a trap with an ADR of its own
    /// ([ADR-0168]) — and measuring a composite over each separately is precisely
    /// how a wash that works on one theme and vanishes on the other gets caught.
    @Test
    @DisplayName("the surfaces swept are the surfaces the themes declare")
    void theSurfacesAreTheOnesTheThemesDeclare() throws IOException {
        var declared = new TreeSet<String>();
        var pattern = Pattern.compile("^\\s*(--gb-(?:bg|surface[\\w-]*))\\s*:", Pattern.MULTILINE);
        for (var theme : List.of("nord-light", "nord-dark")) {
            var css = Files.readString(
                    Path.of("../core/src/main/resources/io/github/digitalsmile/goldberry/css/" + theme + ".css"));
            var matcher = pattern.matcher(css);
            while (matcher.find()) {
                declared.add(matcher.group(1));
            }
        }

        assertEquals(
                Set.copyOf(BACKDROPS.stream().map(Backdrop::name).toList()),
                Set.copyOf(declared),
                "a theme declares a surface this sweep does not cover, or covers one it no"
                        + " longer declares. A new surface is a new backdrop a ghost button and a"
                        + " selection wash can land on, and it joins BACKDROPS with the stack it"
                        + " is painted on.");
    }

    private static String themeName(Theme theme) {
        return theme == Theme.NORD_DARK ? "nord-dark" : "nord-light";
    }

    private static String name(int argb) {
        return String.format(Locale.ROOT, "#%08x", argb);
    }
}
