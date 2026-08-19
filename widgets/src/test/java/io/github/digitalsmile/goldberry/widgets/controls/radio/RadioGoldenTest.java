package io.github.digitalsmile.goldberry.widgets.controls.radio;

import io.github.digitalsmile.goldberry.widgets.controls.select.Select;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
import io.github.digitalsmile.goldberry.widgets.controls.knob.Knob;
import io.github.digitalsmile.goldberry.widgets.controls.progressbar.Progress;
import io.github.digitalsmile.goldberry.widgets.controls.radio.Radio;
import io.github.digitalsmile.goldberry.widgets.controls.radio.RadioGroup;
import io.github.digitalsmile.goldberry.widgets.controls.slider.Slider;
import io.github.digitalsmile.goldberry.widgets.controls.spinner.Spinner;
import io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a radio group actually looks like (§14, [ADR-0050]).
///
/// [RadioTest] checks the invariant and the traversal. These check the two things
/// no value assertion reaches: that the glyph is a **circle** — a `border-radius`
/// of half the box, drawn by the same four cubics as every other corner
/// ([ADR-0064]) — and that the dot is a filled shape inside it rather than a ring
/// or a tick.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class RadioGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Attributes id(String id, String... classes) {
        return new Attributes(id, Set.of(classes), id);
    }

    /// Which element gets which pseudo-class — `child` indexes the group's
    /// options, `part` reaches inside one to its glyph.
    private record PseudoState(int child, Integer part, Selector.PseudoClass pseudoClass) {

        static PseudoState on(int child, Selector.PseudoClass pseudoClass) {
            return new PseudoState(child, null, pseudoClass);
        }

        void applyTo(Element group) {
            var element = group.children().get(child);
            if (part != null) {
                element = element.children().get(part);
            }
            element.setPseudoClass(pseudoClass, true);
        }
    }

    /// The scene sizes are **content plus padding and nothing spare**, and they
    /// have to be: a control no longer shrinks to fit (ADR-0076), so a frame 4px
    /// too short now clips the last option instead of quietly squashing all
    /// three. Three options are 3x32 + 2x8 of gap + 2x12 of padding = 136.
    private void paint(
            String name, Theme theme, int width, int height, Widget group, PseudoState... states) {

        var tree = new ElementTree(group);
        for (var state : states) {
            state.applyTo(tree.root());
        }
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #group { padding: 12px; background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static RadioGroup group(String selected, String... classes) {
        return new RadioGroup(selected,
                List.of(
                        new Radio("light", "Light"),
                        new Radio("dark", "Dark"),
                        new Radio("system", "Follow the system")),
                null, null, false, id("group", classes));
    }

    @Test
    @DisplayName("a column of three, one selected, on the dark theme")
    void columnDark() {
        // The image that says the glyph is round and the dot is solid. A stroked
        // dot would read as a ring at 16px and as an obvious mistake at 48, which
        // is why the painter fills this mark rather than stroking it.
        paint("radio-group-dark", Theme.NORD_DARK, 300, 140, group("dark"));
    }

    @Test
    @DisplayName("the same group on the light theme, which is a different set of tokens")
    void columnLight() {
        // The dot is nord0 on dark and nord6 on light, mirroring the checkbox's
        // tick for the same 4.5:1 reason (§1.2) -- and through its own tokens, so
        // a theme can distinguish a radio from a checkbox without redefining both.
        paint("radio-group-light", Theme.NORD_LIGHT, 300, 140, group("dark"));
    }

    @Test
    @DisplayName("nothing selected, which is a real state and not an error")
    void nothingSelected() {
        // A model that has not loaded, or a value from a newer document. Three
        // empty circles rather than a guess at the first one.
        paint("radio-group-empty", Theme.NORD_DARK, 300, 140, group(null));
    }

    @Test
    @DisplayName("hovered, keyboard-focused and disabled")
    void interactionStates() {
        // The ring is around the *option*, not the group: focus lands on the
        // thing the user is about to pick, so a group of six does not draw a
        // rectangle around all of them.
        paint("radio-group-interaction", Theme.NORD_DARK, 300, 140,
                new RadioGroup("dark",
                        List.of(
                                new Radio("light", "Hover"),
                                new Radio("dark", "Focus"),
                                new Radio("system", "Unavailable").disabled(true)),
                        null, null, false, id("group")),
                new PseudoState(0, 0, Selector.PseudoClass.HOVER),
                PseudoState.on(1, Selector.PseudoClass.FOCUS_VISIBLE));
    }

    @Test
    @DisplayName("the dot caught mid-scale, which is the half of §3.1 that was missing")
    void dotScalesIn() {
        // §3.1: "check/dot: scale 0.6->1 + opacity, base" -- the half of that row
        // that could not be drawn until the mark became a node.
        //
        // A group holds exactly one selection, so a single frame cannot show a
        // dot at 0%, 50% and 100% at once. What it *can* show is better: the
        // moment a selection moves, with one dot growing in and the one it left
        // shrinking out, caught at 80 ms of a 160 ms base transition.
        //
        // The assertion this image carries is that **all three rings are the
        // same 16px circle**. That is the whole reason the dot is its own node:
        // a scale applied to the indicator would show three different circle
        // sizes here, which is what the naive fix looks like when it is wrong.
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #group { padding: 12px; background: var(--gb-bg) }
                                """)),
                TestFont.get()).clock(clock);

        // Driven through the binding rather than by poking `:checked`, because
        // the renderer mirrors `isChecked()` off the widget on every frame and
        // would overwrite a pseudo-class set by hand. This is also the real path:
        // the application sets the property and the tick follows (ADR-0063).
        var selection = Property.of((String) null);
        var tree = new ElementTree(new RadioGroup(null,
                List.of(new Radio("a", "Untouched"),
                        new Radio("b", "Arriving"),
                        new Radio("c", "Leaving")),
                selection, null, false, id("group")));

        // Frame one establishes the resting style; nothing transitions on it.
        renderer.render(tree);

        // "Leaving" is selected first and taken all the way, so it starts this
        // frame fully arrived.
        select(tree, selection, "c");
        renderer.render(tree);
        clock.advance(200);
        renderer.render(tree);

        // Then the selection moves, and the frame is taken halfway through.
        select(tree, selection, "b");
        renderer.render(tree);
        clock.advance(80);
        var midway = renderer.render(tree);
        assertTrue(renderer.isAnimating(), "the dot is still on its way");

        // What the eye cannot check on a 16px glyph: the dot really is part-way
        // between the two scales, and the ring it sits in is not scaled at all.
        var arriving = midway.children().get(1).children().getFirst();
        var dot = arriving.children().getFirst();
        assertEquals(Transform.NONE, arriving.transform(),
                "the 16px ring does not move -- which is the whole reason the dot"
                        + " is a node rather than a mark on this box");
        assertNotEquals(Transform.NONE, dot.transform(), "and the dot does");
        assertNotEquals(settledDotTransform(), dot.transform(), "caught before it arrived");

        GoldenImage.assertMatches("radio-group-scaling", 300, 140, 1.0f,
                frame -> BoxPainter.paint(frame, midway));
    }

    /// The `transform` a settled, selected dot resolves to — `scale(1)` — for the
    /// midway frame to be compared against, so the comparison is against what the
    /// stylesheet says rather than against a number written twice.
    private static Transform settledDotTransform() {
        var painted = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                .render(new ElementTree(new RadioGroup("a", new Radio("a", "A"))));
        // group > radio > radio-indicator > radio-dot
        return painted.children().getFirst().children().getFirst().children().getFirst()
                .transform();
    }

    /// Sets the bound value and flushes the rebuild it schedules.
    ///
    /// A change marks the element dirty and defers the build to the frame
    /// boundary ([ADR-0052]), so a test that read the tree without flushing would
    /// be looking at the frame before the one it asked for.
    private static void select(ElementTree tree, Property<String> selection, String value) {
        selection.set(value);
        tree.flush();
    }

    @Test
    @DisplayName("a checked control under the pointer keeps its accent")
    void checkedHover() {
        // The scene that was missing, and the bug it hid: `checkbox:hover
        // check-indicator` outranks `check-indicator:checked` -- two type
        // selectors and a pseudo-class beats one of each -- so hovering a ticked
        // box replaced the accent fill with the grey hover surface, and the mark,
        // drawn in a colour chosen to read on the accent, vanished into it.
        //
        // Every hover golden until now hovered an *unchecked* control, so the
        // combination that breaks was never drawn.
        var content = new Column(
                List.of(
                        new Checkbox("Checked and hovered", Checkbox.Value.CHECKED,
                                null, null, false, id("box")),
                        new RadioGroup("on",
                                List.of(new Radio("on", "Selected and hovered")),
                                null, null, false, id("group"))),
                id("panel"));

        var tree = new ElementTree(content);
        tree.root().children().getFirst().setPseudoClass(Selector.PseudoClass.HOVER, true);
        tree.root().children().get(1).children().getFirst()
                .setPseudoClass(Selector.PseudoClass.HOVER, true);

        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #panel { flex-direction: column; padding: 12px; gap: 8px;
                                         background: var(--gb-surface) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches("controls-checked-hover", 300, 100, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    @Test
    @DisplayName("on a surface panel, where the glyph used to disappear")
    void onASurface() {
        // The gap that let an invisible control ship. Every other golden in this
        // repository paints on `--gb-bg`, and the glyph's rest colour was
        // `--gb-surface` -- so it was the exact colour of the panel a control
        // normally sits on, and CI never once drew it there. Both themes, both
        // controls, on the surface they are actually used on.
        var content = surfaceScene();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            var name = theme == Theme.NORD_DARK ? "controls-on-surface-dark" : "controls-on-surface-light";
            var tree = new ElementTree(content);
            var renderer = new WidgetRenderer(
                    List.of(
                            Controls.baseStylesheet(),
                            theme.load(),
                            Stylesheet.parse(CascadeLayer.APPLICATION, """
                                    #panel { flex-direction: column; padding: 12px; gap: 8px;
                                             background: var(--gb-surface) }
                                    #group { gap: 8px }
                                    """)),
                    TestFont.get())
                    // **A virtual clock, and this scene needs one now.** Every
                    // golden here was deterministic under the system clock while
                    // nothing in it moved on its own; a `spinner` draws itself
                    // from the frame time, so under a wall clock this image is a
                    // different ring on every run -- which it duly was, 84 pixels
                    // apart, the first time it was regenerated (ADR-0081).
                    .clock(io.github.digitalsmile.goldberry.motion.Clock.virtual());

            // Grown from 220 when `progress` and `spinner` joined, and again when
            // `knob` did -- 32px of control plus the column's 8px gap. A
            // frame that fitted only because the last two controls were falling
            // off the bottom is the same defect ADR-0076 found in six scenes at
            // once: an image is not evidence of a control it clipped away.
            GoldenImage.assertMatches(name, 300, 370, 1.0f,
                    frame -> BoxPainter.paint(frame, renderer.render(tree)));
        }
    }

    /// One of every control that paints no background of its own, on a panel.
    ///
    /// Extracted so the golden and the coverage guard below cannot disagree about
    /// what is in it — which is the shape of the mistake this whole scene exists
    /// to catch.
    private static Widget surfaceScene() {
        return new Column(
                List.of(
                        new Checkbox("A checkbox at rest", Checkbox.Value.UNCHECKED),
                        // The toggle joins this scene rather than getting one of
                        // its own: the axis is "on --gb-surface", and a control
                        // added to the catalog without being added here is one
                        // more control CI has never drawn where it is used.
                        new Toggle("A switch at rest", false),
                        // The slider joins for the same reason, and it is the
                        // control that most needed it: its groove is the only
                        // surface in the catalog thin enough to vanish without
                        // anything else looking wrong.
                        new Slider(0, 100, 40, 0, null, null, false, id("gain")),
                        new RadioGroup("dark",
                                List.of(new Radio("light", "Unselected"),
                                        new Radio("dark", "Selected")),
                                null, null, false, id("group")),
                        // A progress bar's track is the same 4px groove the
                        // slider's is, and takes the same token -- so it is the
                        // same defect waiting, and this is where it is caught.
                        new Progress(0.4),
                        // A spinner has no surface at all: it is a stroke, and a
                        // stroke that matched the panel would be a control that
                        // vanished on it as completely as the checkbox did.
                        new Spinner(),
                        // The knob is the control this scene was most obviously
                        // waiting for: its track takes `--gb-border`, the same
                        // token the slider's groove and the progress bar's track
                        // do, and its ring is stroked on whatever is behind the
                        // dial rather than on the dial. The first drawing of it
                        // got exactly this wrong in the other direction -- the
                        // track ran across the body at 1.2:1 -- which is why it
                        // is in the scene rather than exempted from it
                        // (ADR-0089).
                        new Knob(0, 1, 0.4, 0, null),
                        // A closed `select` is a field, and a field is exactly
                        // the shape that disappears on a panel: it is a border
                        // and a fill one step off whatever is behind it, so the
                        // one thing this scene has to prove about it is that the
                        // step is visible in both themes (ADR-0141).
                        new Select("dark",
                                List.of(new Option("light", "Light"),
                                        new Option("dark", "Dark")),
                                null, null, "", false, id("theme"))),
                id("panel"));
    }

    /// Every control that paints no background of its own appears in that scene,
    /// and this is what makes it true tomorrow.
    ///
    /// The gap it closes is a real one, twice over. `controls-on-surface-*` exists
    /// because a checkbox's glyph was invisible on `--gb-surface` and every golden
    /// in the repository painted on `--gb-bg` ([ADR-0073]). Then `slider` shipped
    /// with a groove whose colour **was** `--gb-surface`, and the scene that
    /// exists for exactly that had simply not been extended to it — so the axis
    /// was covered and the control was not.
    ///
    /// `button` is exempt and says why: it paints its own opaque surface in every
    /// variant, so there is no panel it can disappear against.
    @Test
    @DisplayName("every control without a background of its own is in the surface scene")
    void everySurfacelessControlIsCovered() {
        // `button` paints its own opaque surface in every variant, so there is
        // no panel it can disappear against. `badge` is exempt on the same
        // terms and does better than an exemption: `badge-on-surface.png` is a
        // scene of its own, and it exists because the *default* chip is filled
        // with `--gb-surface-2`, which is one step from the panel under it --
        // the one distance worth having an image of (ADR-0087).
        //
        // `segmented` and `option` are exempt on the badge's terms and with the
        // badge's image: the bar paints a plate one step off `--gb-surface`, and
        // `segmented-on-surface.png` is that step. A segment has no fill of its
        // own until it is selected, and what it would disappear against is the
        // bar rather than a panel -- which is the axis every image in
        // `SegmentedGoldenTest` is already on (ADR-0097).
        var exempt = List.of("button", "badge", "segmented", "option");
        var inScene = new java.util.ArrayList<String>();
        collectTypes(new ElementTree(surfaceScene()).root(), inScene);

        for (var type : Controls.controlTypes()) {
            if (exempt.contains(type)) {
                continue;
            }
            org.junit.jupiter.api.Assertions.assertTrue(inScene.contains(type),
                    type + " is not in controls-on-surface-*, so CI has never drawn it on"
                            + " --gb-surface. Add it to surfaceScene() or exempt it with a reason.");
        }
    }

    private static void collectTypes(Element element, List<String> into) {
        if (element.type() != null) {
            into.add(element.type());
        }
        element.children().forEach(child -> collectTypes(child, into));
    }

    @Test
    @DisplayName("`radio-group.inline` is a row, because §3 gives the group no axis")
    void inline() {
        // The widget names the semantics and the stylesheet names the axis, which
        // is the opposite of `row` and `column` -- those *are* their axis, and a
        // stylesheet that could turn one into the other would make the name lie.
        paint("radio-group-inline", Theme.NORD_DARK, 460, 56, group("light", "inline"));
    }
}
