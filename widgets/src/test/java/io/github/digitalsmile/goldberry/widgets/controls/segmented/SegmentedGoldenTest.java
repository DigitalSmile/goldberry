package io.github.digitalsmile.goldberry.widgets.controls.segmented;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a segmented control actually looks like (§14, [ADR-0050]).
///
/// [SegmentedTest] pins the two radii and the inset as numbers. These are the
/// images that say the numbers add up to a control: **the selected segment sits
/// *inside* the bar's rounded corners**, which is the whole of
/// [ADR-0097](../../../../../../../../book/src/adr/0097-a-selection-that-travels-needs-a-geometry.md)'s
/// drawing decision, and it is the kind of thing no assertion reaches — a fill
/// painted over the bar's curve resolves to exactly the same numbers and looks
/// like a corner that lost its radius.
///
/// The second is that **the bar is one object**. Segments have no fill and no
/// border of their own, so what tells a reader that three labels are one control
/// is the plate behind them and the 1px edge around it; both are easy to lose to
/// a token that stops resolving, and neither changes a single length.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class SegmentedGoldenTest {

    private Icon icon;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        icon = Icon.bundled("plus", 16);
    }

    @AfterEach
    void tearDown() {
        if (icon != null) {
            icon.close();
        }
    }

    private static Attributes id(String id, String... classes) {
        return new Attributes(id, Set.of(classes), id);
    }

    /// Which segment gets which pseudo-class — the states are set by hand rather
    /// than by moving a pointer, for the reason `ButtonGoldenTest` gives: an image
    /// is about what a state looks like, and the router's tests are about whether
    /// input reaches it.
    private record PseudoState(int segment, Selector.PseudoClass pseudoClass) {
        /// Through the track, and `+ 1` past the indicator: the pill is the
        /// track's first child so that it is painted under the labels
        /// ([ADR-0099]).
        void applyTo(Element bar) {
            bar.children().getFirst().children().get(segment + 1)
                    .setPseudoClass(pseudoClass, true);
        }
    }

    /// The scene is the bar inside a row, on `--gb-bg`.
    ///
    /// Sizes are content plus padding and nothing spare: a control does not shrink
    /// (ADR-0076), so a frame a few pixels short clips a segment rather than
    /// squashing the bar, and an image is not evidence of what it cut off.
    private void paint(String name, Theme theme, int width, Widget bar, PseudoState... states) {
        var content = new Row(List.of(bar), id("row"));
        var tree = new ElementTree(content);
        for (var state : states) {
            state.applyTo(tree.root().children().getFirst());
        }
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #row   { padding: 12px; gap: 8px; align-items: center;
                                         background: var(--gb-bg) }
                                #panel { padding: 12px; gap: 8px; align-items: center;
                                         background: var(--gb-surface) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, 56, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static Segmented bar(String selected) {
        return new Segmented(selected,
                List.of(new Option("list", "List"),
                        new Option("grid", "Grid"),
                        new Option("map", "Map")),
                null, null, false, id("view"));
    }

    /// The image this control exists to be checked by: three labels, one plate,
    /// and the middle segment filled — inside the corners rather than across
    /// them.
    @Test
    @DisplayName("three segments with the middle one selected, on dark")
    void selectedDark() {
        paint("segmented-dark", Theme.NORD_DARK, 220, bar("grid"));
    }

    /// The same bar on light, where every colour in it is a different token and
    /// the selected fill is the one that had to be taken *down* rather than up to
    /// carry its label (ADR-0088).
    @Test
    @DisplayName("the same bar on the light theme")
    void selectedLight() {
        paint("segmented-light", Theme.NORD_LIGHT, 220, bar("list"));
    }

    /// Resting, hovered, and the selected segment hovered — the third being the
    /// one that only an image and a specificity argument agree on. `option:hover`
    /// and `option:checked` have equal specificity, so a rule in the wrong order
    /// turns the selected segment grey under the pointer and takes its label,
    /// drawn for the accent, with it.
    @Test
    @DisplayName("hover on an unselected segment, and on the selected one")
    void hover() {
        paint("segmented-hover", Theme.NORD_DARK, 220, bar("grid"),
                new PseudoState(0, Selector.PseudoClass.HOVER));
        paint("segmented-hover-selected", Theme.NORD_DARK, 220, bar("grid"),
                new PseudoState(1, Selector.PseudoClass.HOVER));
    }

    /// The focus ring goes round the **segment**, at §2.2's 2px offset, and the
    /// bar's own padding is 2 — so the ring lands on the bar's edge and this is
    /// the image that says it is still legible there.
    @Test
    @DisplayName("the ring is on the segment and not on the bar")
    void focusRing() {
        paint("segmented-focus", Theme.NORD_DARK, 220, bar("grid"),
                new PseudoState(2, Selector.PseudoClass.FOCUS_VISIBLE));
    }

    /// A disabled bar fades as one thing — the plate, the edge, the labels and the
    /// selected fill — because opacity multiplies down a subtree and the flag
    /// stays on the node that declared it (ADR-0077).
    @Test
    @DisplayName("a disabled bar fades once, not twice")
    void disabled() {
        paint("segmented-disabled", Theme.NORD_DARK, 220, bar("grid").disabled(true));
    }

    /// An icon before a label, and an icon-only segment beside it. §3 allows both;
    /// what the image is for is the gap between the two and the fact that an
    /// icon-only segment is still 12px padded rather than collapsing to its glyph.
    @Test
    @DisplayName("a segment takes an icon, a label, or both")
    void icons() {
        paint("segmented-icons", Theme.NORD_DARK, 260, new Segmented("grid",
                List.of(new Option("list", "List").withIcon(icon),
                        new Option("grid", "Grid").withIcon(icon),
                        new Option("map", "", icon, false, null, false, Attributes.NONE)),
                null, null, false, id("view")));
    }

    /// Given a column to sit in, the bar fills the width and its segments divide
    /// it — rather than huddling at the left of a plate that stretched without
    /// them, which is what `flex-grow: 1` on a segment buys and the only reason
    /// that declaration is there. This is the shape the showcase's sidebar puts
    /// it in, and the one an assertion on a grow factor cannot show.
    @Test
    @DisplayName("in a column, the segments divide the plate")
    void stretched() {
        var content = new Column(List.of(bar("grid")), id("column"));
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #column { flex-direction: column; padding: 12px; gap: 8px;
                                          background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches("segmented-stretched", 320, 56, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    /// **The image this change exists for**: the pill caught between two
    /// segments, which is a picture no wall clock can take and no assertion can
    /// make.
    ///
    /// [SegmentedTest] proves the pill covers segment *k* exactly at rest, and
    /// that its transform is a percentage of its own width. What only this can
    /// show is that the thing in between is a *travelling pill* rather than one
    /// fill dimming while another brightens — the two are identical at both ends
    /// and completely different in the middle, which is the whole of what §3.1
    /// asks for (ADR-0099).
    @Test
    @DisplayName("the pill caught between two segments")
    void travel() {
        var clock = Clock.virtual();
        var mode = Property.of("list");
        var content = new Row(List.of(
                Segmented.of(mode, null,
                        new Option("list", "List"),
                        new Option("grid", "Grid"),
                        new Option("map", "Map")).id("view")),
                id("row"));
        var tree = new ElementTree(content);
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #row { padding: 12px; gap: 8px; align-items: center;
                                       background: var(--gb-bg) }
                                #view { width: 240px }
                                """)),
                TestFont.get()).clock(clock);

        // Frame 1 establishes the resting style. Nothing transitions on a first
        // frame: a bar appearing is not a bar moving, or every window would
        // start with its pill sliding in from the left.
        tree.flush();
        renderer.render(tree);
        assertFalse(renderer.isAnimating());

        // The far segment, so the travel is two cells and unmistakable.
        mode.set("map");
        tree.flush();
        renderer.render(tree);
        assertTrue(renderer.isAnimating(), "the selection moved, so the pill started");

        // 80 ms of `--gb-motion-base`'s 160. `ease-enter` decelerates, so this
        // is past halfway across and still visibly between the two -- which is
        // the frame worth keeping.
        clock.advance(80);
        var midway = renderer.render(tree);
        assertTrue(renderer.isAnimating(), "and has not arrived");

        GoldenImage.assertMatches("segmented-travel", 280, 56, 1.0f,
                frame -> BoxPainter.paint(frame, midway));

        clock.advance(120);
        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "the frame loop goes idle once the pill lands");
    }

    /// §1.7 rule 6: "all transitions collapse to 0ms". The pill still ends up on
    /// the right segment — reduced motion changes how it gets there and never
    /// where it goes.
    @Test
    @DisplayName("under reduced motion the pill arrives without travelling")
    void reducedMotion() {
        var clock = Clock.virtual();
        var mode = Property.of("list");
        var tree = new ElementTree(new Row(List.of(
                Segmented.of(mode, null,
                        new Option("list", "List"), new Option("grid", "Grid")).id("view")),
                id("row")));
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                .clock(clock);
        renderer.reducedMotion(true);

        tree.flush();
        renderer.render(tree);
        mode.set("grid");
        tree.flush();
        renderer.render(tree);

        assertFalse(renderer.isAnimating(), "there was nothing to animate");
    }

    /// On a panel rather than on the window, which is the gap
    /// `controls-on-surface-*` exists to close ([ADR-0073]). A bar paints its own
    /// plate, so it cannot vanish — but the plate is one step from `--gb-surface`
    /// on the dark theme, and one step is exactly the distance worth having an
    /// image of.
    @Test
    @DisplayName("the bar on a surface, one step from its own plate")
    void onSurface() {
        var content = new Panel(List.of(bar("grid")), id("panel"));
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #panel { padding: 12px; gap: 8px; align-items: center;
                                         background: var(--gb-surface) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches("segmented-on-surface", 220, 56, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }
}
