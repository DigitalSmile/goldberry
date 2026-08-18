package io.github.digitalsmile.goldberry.widgets.controls;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
import io.github.digitalsmile.goldberry.widgets.controls.radio.Radio;
import io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A control's glyph is a fixed size, and stays one when the window is too small.
///
/// Reported as "the knob is outside the pill when I resize the window", and the
/// pill was the part that moved: Yoga runs with CSS's defaults, so **every node
/// has `flex-shrink: 1`** and a `width: 36px` is a *preferred* width that a
/// cramped row is free to take back. The 16px thumb inside it shrinks by a
/// different amount — it is a different node with different content — so the two
/// stop agreeing and the disc hangs over the edge.
///
/// Nothing about this is specific to `toggle`. `check-indicator` and
/// `radio-indicator` are 16px glyphs on the same terms, and a checkbox in a
/// narrow sidebar squashes into an ellipse. That is why the assertions run over
/// every part in the catalog rather than over the one that was reported.
///
/// The widths here are deliberately absurd — 40px for a row that wants ~200 —
/// because a bug that needs the window dragged to exactly the wrong size is a bug
/// that reaches a user and not CI.
class ControlShrinkTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    /// The laid-out size of the element at `path`, with the window `width` wide.
    private HitTest.Region regionAt(Widget content, int width, int... path) {
        target = TestFrames.of(width, 120, 1.0f, 0);
        var tree = new ElementTree(content);
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #row { padding: 0; gap: 0; align-items: center }
                                """)),
                TestFont.get());

        var element = tree.root();
        for (var index : path) {
            element = element.children().get(index);
        }

        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            var wanted = element;
            return HitTest.capture(render).stream()
                    .filter(region -> region.owner() == wanted)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no region for the element at that path"));
        }
    }

    private static Widget row(Widget child) {
        return new Row(List.of(child), new Attributes("row", Set.of(), "row"));
    }

    @Test
    @DisplayName("a toggle's pill is 36×20 in a window far too narrow for it")
    void togglePillDoesNotShrink() {
        // Child 0 of the toggle is the track. The thumb inside it is a fixed
        // 16px, so a pill that gave up 8px of width would have the disc hanging
        // over its end -- which is exactly what was seen.
        var wide = regionAt(row(new Toggle("Show the prose", true)), 400, 0, 0);
        var narrow = regionAt(row(new Toggle("Show the prose", true)), 40, 0, 0);

        assertEquals(36, wide.width(), 0.5, "the pill at a comfortable width");
        assertEquals(36, narrow.width(), 0.5, "and the same pill in a window that cannot fit it");
        assertEquals(20, narrow.height(), 0.5);
    }

    @Test
    @DisplayName("a checkbox's glyph is 16×16 in a window far too narrow for it")
    void checkboxGlyphDoesNotShrink() {
        var narrow = regionAt(row(new Checkbox("Show the prose", Checkbox.Value.CHECKED)), 40, 0, 0);

        assertEquals(16, narrow.width(), 0.5, "§3's glyph is 16 and a cramped row does not get to argue");
        assertEquals(16, narrow.height(), 0.5, "and it is square, not an ellipse");
    }

    @Test
    @DisplayName("a radio's glyph is 16×16 in a window far too narrow for it")
    void radioGlyphDoesNotShrink() {
        var narrow = regionAt(row(new Radio("dark", "Show the prose", true, null, false, null)), 40, 0, 0);

        assertEquals(16, narrow.width(), 0.5);
        // A squashed circle is the most visible form of this bug: `border-radius`
        // follows the box, so an ellipse is what a shrunk radio draws.
        assertEquals(16, narrow.height(), 0.5);
    }

    @Test
    @DisplayName("the thumb inside a squeezed pill is still 16 square")
    void thumbDoesNotShrink() {
        var narrow = regionAt(row(new Toggle("Show the prose", true)), 40, 0, 0, 0);

        assertEquals(16, narrow.width(), 0.5);
        assertEquals(16, narrow.height(), 0.5);
    }

    /// The other axis, and the one a column finds. `#options` in the showcase is
    /// a column, so a short window squeezes control *heights* the same way a
    /// narrow one squeezes glyph widths — and §1.3's hit target goes with it.
    @Test
    @DisplayName("a control keeps its height in a column with no room")
    void heightSurvivesAColumn() {
        target = TestFrames.of(300, 40, 1.0f, 0);
        var content = new Column(
                List.of(new Checkbox("One", Checkbox.Value.CHECKED),
                        new Toggle("Two", true),
                        new Radio("a", "Three", true, null, false, null)),
                new Attributes("col", Set.of(), "col"));
        var tree = new ElementTree(content);
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION,
                                "#col { flex-direction: column; padding: 0; gap: 0 }")),
                TestFont.get());

        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            var regions = HitTest.capture(render);
            for (var index = 0; index < 3; index++) {
                var element = tree.root().children().get(index);
                var region = regions.stream().filter(r -> r.owner() == element).findFirst().orElseThrow();
                assertEquals(32, region.height(), 0.5,
                        "control " + index + " should keep §1.3's 32px hit target");
            }
        }
    }
}
