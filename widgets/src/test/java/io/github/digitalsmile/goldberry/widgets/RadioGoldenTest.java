package io.github.digitalsmile.goldberry.widgets;

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
import io.github.digitalsmile.goldberry.widget.Widgets;
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

    private static Widgets.Attributes id(String id, String... classes) {
        return new Widgets.Attributes(id, Set.of(classes), id);
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
        paint("radio-group-dark", Theme.NORD_DARK, 300, 132, group("dark"));
    }

    @Test
    @DisplayName("the same group on the light theme, which is a different set of tokens")
    void columnLight() {
        // The dot is nord0 on dark and nord6 on light, mirroring the checkbox's
        // tick for the same 4.5:1 reason (§1.2) -- and through its own tokens, so
        // a theme can distinguish a radio from a checkbox without redefining both.
        paint("radio-group-light", Theme.NORD_LIGHT, 300, 132, group("dark"));
    }

    @Test
    @DisplayName("nothing selected, which is a real state and not an error")
    void nothingSelected() {
        // A model that has not loaded, or a value from a newer document. Three
        // empty circles rather than a guess at the first one.
        paint("radio-group-empty", Theme.NORD_DARK, 300, 132, group(null));
    }

    @Test
    @DisplayName("hovered, keyboard-focused and disabled")
    void interactionStates() {
        // The ring is around the *option*, not the group: focus lands on the
        // thing the user is about to pick, so a group of six does not draw a
        // rectangle around all of them.
        paint("radio-group-interaction", Theme.NORD_DARK, 300, 132,
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

        GoldenImage.assertMatches("radio-group-scaling", 300, 132, 1.0f,
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
        var content = new Widgets.Column(
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
        var content = new Widgets.Column(
                List.of(
                        new Checkbox("A checkbox at rest", Checkbox.Value.UNCHECKED),
                        new RadioGroup("dark",
                                List.of(new Radio("light", "Unselected"),
                                        new Radio("dark", "Selected")),
                                null, null, false, id("group"))),
                id("panel"));

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
                    TestFont.get());

            GoldenImage.assertMatches(name, 300, 132, 1.0f,
                    frame -> BoxPainter.paint(frame, renderer.render(tree)));
        }
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
