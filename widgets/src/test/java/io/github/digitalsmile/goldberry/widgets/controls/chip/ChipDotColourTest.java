package io.github.digitalsmile.goldberry.widgets.controls.chip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// A `chip`'s dot in a colour the document supplied — `docs/gaps.md` G36,
/// [ADR-0328].
///
/// A Project's hue is a row in a database, so there is no class a stylesheet
/// rule could name: the colour has to travel as data and arrive at the dot.
class ChipDotColourTest {

    /// Nord's `aurora-red`, which is the hue a Project might be given.
    private static final int RED = 0xFFBF616A;

    @Test
    @DisplayName("a colour turns the dot on as well as colouring it")
    void aColourImpliesADot() {
        var chip = new Chip("Goldberry").withDot(RED);

        assertTrue(chip.dot());
        assertEquals(RED, chip.dotColor());
    }

    @Test
    @DisplayName("a colour with no dot is refused rather than half-honoured")
    void aColourWithoutADotIsRefused() {
        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new Chip("Goldberry", null, false, RED, false, null, null, null, false, null));

        assertTrue(thrown.getMessage().contains("no dot to colour"), thrown.getMessage());
    }

    @Test
    @DisplayName("turning the dot off takes its colour with it")
    void withDotFalseClearsTheColour() {
        var chip = new Chip("Goldberry").withDot(RED).withDot(false);

        assertFalse(chip.dot());
        assertEquals(0, chip.dotColor());
    }

    @Test
    @DisplayName("a coloured dot and an icon at once is still refused")
    void theLeadingSlotStillHoldsOneThing() {
        var icon = Icon.of("dot", "M12 12h1", 16);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Chip("Live").withDot(RED).withIcon(icon));
    }

    @Test
    @DisplayName("every wither carries the colour along")
    void theWithersKeepTheColour() {
        var chip = new Chip("Goldberry")
                .withDot(RED)
                .selected(true)
                .onPress(() -> {})
                .onDismiss(() -> {})
                .disabled(true)
                .id("project")
                .styled("accent");

        assertEquals(RED, chip.dotColor());
        assertTrue(chip.dot());
    }

    @Test
    @DisplayName("markup says it too, in either spelling, and the two forms agree")
    void kdlAndJavaAgree() {
        var british = Widgets.inflater().inflateAll(KdlParser.parse("""
                        chip dot-colour="#bf616a" "Goldberry"
                        """)).getFirst();
        var american = Widgets.inflater().inflateAll(KdlParser.parse("""
                        chip dot-color="#bf616a" "Goldberry"
                        """)).getFirst();

        assertEquals(new Chip("Goldberry").withDot(RED), british);
        assertEquals(british, american);
    }

    @Test
    @DisplayName("a chip with no colour is the value it always was")
    void noColourIsTheOldValue() {
        var written = Widgets.inflater().inflateAll(KdlParser.parse("""
                        chip dot=#true "Degraded"
                        """)).getFirst();

        assertEquals(new Chip("Degraded").withDot(true), written);
        assertEquals(0, ((Chip) written).dotColor(), "so the stylesheet still decides");
    }

    @Test
    @DisplayName("the colour reaches the painted dot, over whatever the cascade resolved")
    void theDotIsPaintedInIt() {
        assertEquals(RED, dotBackground(new Chip("Goldberry").withDot(RED)));
        // And an uncoloured dot keeps the stylesheet's answer, whatever it is —
        // what matters is that it is not the colour above.
        assertFalse(dotBackground(new Chip("Goldberry").withDot(true)) == RED);
    }

    /// The `chip-dot` box's background, after a real render through
    /// `controls.css` — which is the only place "the colour won" is a fact rather
    /// than a claim.
    ///
    /// The dot is the chip's first child whenever there is one, which is what
    /// [Chip#children()] says and what the stylesheet's `:first-child` rules
    /// already rest on.
    private static int dotBackground(Chip chip) {
        var tree = new ElementTree(chip);
        assertInstanceOf(ChipDot.class, tree.root().children().getFirst().widget());
        var root = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                .render(tree);
        return root.children().getFirst().background();
    }
}
