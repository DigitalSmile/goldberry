package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// **A menu row too narrow for its label cuts it**, where it used to let it run
/// off the edge.
///
/// The sibling of [ItemAlignmentTest], which asserts the half that must not
/// change: a squeezed row still puts its label on **one line**. That was won by
/// `flex-shrink: 0` (ADR-0148), which stopped the wrap by stopping the shrink and
/// left the label overflowing the menu. This asserts the half that changed —
/// the label shrinks again, and is cut rather than wrapped, because
/// `white-space: nowrap` now measures the paragraph at its natural width whatever
/// width it is offered ([ADR-0255]).
///
/// The two together are the whole claim, and neither alone is: a test that only
/// checked the width would pass against a label that wrapped to two lines inside
/// it.
class ItemTruncationTest {

    /// Narrower than any row of this menu needs — the width a popup is measured
    /// again at when it would not fit the work area (ADR-0104).
    private static final int SQUEEZED = 150;

    /// A label nothing will fit, so the case is not an accident of the fixture.
    private static final String LONG = "Export the current selection as a document";

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static Menu menu() {
        return new Menu(new Item(LONG, () -> {}).accelerator("Ctrl+E"), new Item("Close", () -> {}));
    }

    /// Every text box in the menu, with the width it was laid out to.
    private static List<Placed> textBoxes(int width) {
        var target = TestFrames.of(width, 240, 1.0f, 0);
        var tree = new ElementTree(menu());
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR), TestFont.get());
        var found = new ArrayList<Placed>();
        try (var render = RenderTree.create()) {
            // A definite width, which is what the launcher's second measuring
            // pass hands a popup that would not fit.
            render.measure(renderer.render(tree), target.frame().scale(), width, Float.NaN);
            render.update(target.frame(), renderer.render(tree));
            render.forEachPlacedBox(placed -> {
                if (placed.box().text() != null) {
                    found.add(new Placed(
                            placed.box(),
                            placed.layout().width(),
                            placed.layout().height()));
                }
            });
        }
        return List.copyOf(found);
    }

    private record Placed(Box box, float width, float height) {

        String text() {
            return box.text().paragraph().text();
        }

        /// How wide the paragraph would be with nothing constraining it — what
        /// `nowrap` reports to Yoga, and what the box used to be given.
        double natural() {
            return box.text().paragraph().layout(Paragraph.UNCONSTRAINED).width();
        }
    }

    @Test
    @DisplayName("a menu row's label carries the cut flow, and the row it came from is where it got it")
    void theLabelInheritsTheRowsFlow() {
        // The label is an anonymous child box that no style is applied to, so it
        // can only have this because `Item.render` passed the row's own
        // `textFlow()` down by hand.
        var label = textBoxes(SQUEEZED).stream()
                .filter(placed -> LONG.equals(placed.text()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the label was never drawn"));

        assertTrue(label.box().text().flow().ellipsises(), "`item { white-space: nowrap; text-overflow: ellipsis }`");
    }

    @Test
    @DisplayName("a label too long for its row is laid out narrower than the text, not wider")
    void theLabelShrinks() {
        var label = textBoxes(SQUEEZED).stream()
                .filter(placed -> LONG.equals(placed.text()))
                .findFirst()
                .orElseThrow();

        assertTrue(
                label.width() < label.natural(),
                () -> "the label is " + label.width() + " wide against a natural " + label.natural()
                        + " — it did not shrink, so nothing was cut and the text runs off the menu");
        assertTrue(label.width() < SQUEEZED, "and it is inside the menu");
    }

    @Test
    @DisplayName("and it is still one line, which is what shrinking used to cost")
    void theLabelDoesNotWrap() {
        // The regression ADR-0148 fixed, reintroduced from the other direction if
        // `nowrap` ever stops reaching the measure function: a box narrower than
        // its text is exactly the state that used to wrap it.
        var label = textBoxes(SQUEEZED).stream()
                .filter(placed -> LONG.equals(placed.text()))
                .findFirst()
                .orElseThrow();
        var lineHeight = label.box().text().paragraph().font().lineHeight();

        assertEquals(lineHeight, label.height(), 0.5, "two lines would be twice this");
    }

    @Test
    @DisplayName("the accelerator is not cut, because half a shortcut is not one")
    void theAcceleratorKeepsItsWidth() {
        var accelerator = textBoxes(SQUEEZED).stream()
                .filter(placed -> "Ctrl+E".equals(placed.text()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the accelerator was never drawn"));

        // At least its natural width, rather than exactly it: Yoga rounds a
        // laid-out box to the pixel grid, so `Ctrl+E` measures 38.41 and is
        // placed in 39. What matters is the direction — a cut would be *under*.
        assertTrue(
                accelerator.width() >= accelerator.natural(),
                () -> "the accelerator is " + accelerator.width() + " wide against a natural "
                        + accelerator.natural() + " — a cramped row spends its missing pixels on the"
                        + " label and never on the shortcut");
    }

    @Test
    @DisplayName("a menu with room for its labels cuts nothing")
    void aWideMenuIsUnchanged() {
        var label = textBoxes(600).stream()
                .filter(placed -> LONG.equals(placed.text()))
                .findFirst()
                .orElseThrow();

        assertEquals(label.natural(), label.width(), 0.5, "nothing was taken away");
        assertFalse(label.width() > 600, "and the row still fits");
    }
}
