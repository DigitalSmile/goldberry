package io.github.digitalsmile.goldberry.widgets.panel.skeleton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `skeleton` — §5's placeholder, and the one looping decoration §1.7 allows
/// ([ADR-0164]).
///
/// The pulse is the interesting part, and it is tested as arithmetic rather than
/// as pixels: it has to be **continuous at the wrap**, or every skeleton on the
/// screen snaps once a second, and it has to hold at its **dimmest** under
/// reduced motion, because a placeholder frozen at full strength reads as content
/// that arrived and was blank.
class SkeletonTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a text skeleton is as many bars as it says")
    void lines() {
        assertEquals(3, Described.of(new ElementTree(
                new Skeleton(Skeleton.Shape.TEXT, 3, Attributes.NONE)),
                Skeleton.SkeletonBar.class).size());
        assertEquals(5, Described.of(new ElementTree(
                new Skeleton(Skeleton.Shape.TEXT, 5, Attributes.NONE)),
                Skeleton.SkeletonBar.class).size());
    }

    /// `lines` is for the text form; a circle is one circle however many lines
    /// somebody wrote.
    @Test
    @DisplayName("the other shapes are one bar, whatever lines says")
    void otherShapesAreOne() {
        for (var shape : List.of(Skeleton.Shape.TITLE, Skeleton.Shape.CIRCLE,
                Skeleton.Shape.RECT)) {
            assertEquals(1, Described.of(new ElementTree(new Skeleton(shape, 4,
                    Attributes.NONE)), Skeleton.SkeletonBar.class).size(),
                    shape + " should be one bar");
        }
    }

    /// A block of identical full-width bars reads as a table rather than as
    /// prose — and a lone short bar looks like a mistake, so the rule needs the
    /// second half.
    @Test
    @DisplayName("the last line of a paragraph is short, and a lone line is not")
    void lastLineIsShort() {
        var three = Described.of(new ElementTree(new Skeleton(Skeleton.Shape.TEXT, 3,
                Attributes.NONE)), Skeleton.SkeletonBar.class);
        assertFalse(three.get(0).last());
        assertFalse(three.get(1).last());
        assertTrue(three.get(2).last());

        var one = Described.of(new ElementTree(new Skeleton(Skeleton.Shape.TEXT, 1,
                Attributes.NONE)), Skeleton.SkeletonBar.class);
        assertFalse(one.getFirst().last(), "a one-line paragraph is a full line");
    }

    /// So `skeleton.title` selects from a document that only said
    /// `shape="title"`.
    @Test
    @DisplayName("the shape is a class on the skeleton and on its bars")
    void shapeIsAClass() {
        var tree = new ElementTree(new Skeleton(Skeleton.Shape.CIRCLE));

        assertTrue(Described.first(tree, Skeleton.class).classes().contains("circle"));
        assertTrue(Described.first(tree, Skeleton.SkeletonBar.class).classes()
                .contains("circle"));
    }

    /// A skeleton that stopped asking for frames would be a picture of a
    /// skeleton — `spinner`'s rule (ADR-0081).
    @Test
    @DisplayName("a skeleton is always animating")
    void animates() {
        assertTrue(new Skeleton().isAnimating());
    }

    /// A triangle wave, so the two ends meet and there is no snap back to the
    /// start. A sawtooth would be one line shorter and visibly wrong once a
    /// second.
    @Test
    @DisplayName("the pulse runs there and back, and is continuous at the wrap")
    void pulse() {
        var start = Skeleton.pulseAt(0, false);
        var quarter = Skeleton.pulseAt(250, false);
        var half = Skeleton.pulseAt(500, false);
        var threeQuarters = Skeleton.pulseAt(750, false);
        var end = Skeleton.pulseAt(999.999, false);

        // Dimmest at the ends, brightest in the middle: the fold is at the
        // halfway point, which is what makes one period one there-and-back
        // rather than two.
        assertEquals(0.45, start, 1e-9, "dimmest at the start");
        assertEquals(1.0, half, 1e-9, "brightest at the fold");
        assertTrue(quarter > start && quarter < half, "rising into the fold");
        assertEquals(quarter, threeQuarters, 1e-9, "and symmetrical coming out of it");
        assertEquals(start, end, 1e-2, "the wrap is continuous, so it does not snap");
    }

    /// **Dimmest**, not brightest: see the class note.
    @Test
    @DisplayName("reduced motion holds it at its dimmest")
    void reducedMotion() {
        var dimmest = Skeleton.pulseAt(0, false);
        for (var now : new double[] {0, 100, 250, 500, 750, 990}) {
            assertEquals(dimmest, Skeleton.pulseAt(now, true), 1e-9,
                    "reduced motion must not move at " + now);
        }
    }

    /// A negative clock is what a virtual one hands over when a test winds it
    /// back, and a phase that came out negative would make the pulse brighter
    /// than its own maximum.
    @Test
    @DisplayName("a clock before zero still gives a phase inside the range")
    void negativeClock() {
        for (var now : new double[] {-1, -250, -999, -1001}) {
            var pulse = Skeleton.pulseAt(now, false);
            assertTrue(pulse >= 0.45 - 1e-9 && pulse <= 1.0 + 1e-9,
                    "out of range at " + now + ": " + pulse);
        }
    }

    @Test
    @DisplayName("a skeleton of no lines is refused rather than drawing nothing")
    void noLines() {
        assertThrows(IllegalArgumentException.class,
                () -> new Skeleton(Skeleton.Shape.TEXT, 0, Attributes.NONE));
    }

    @Test
    @DisplayName("a skeleton inflates from markup")
    void inflates() {
        var widget = Widgets.inflater().inflate(
                KdlParser.parse("skeleton shape=\"text\" lines=4").getFirst());
        var it = assertInstanceOf(Skeleton.class, widget);

        assertEquals(Skeleton.Shape.TEXT, it.shape());
        assertEquals(4, it.lines());
    }

    @Test
    @DisplayName("a shape this toolkit has not got is refused")
    void badShape() {
        assertThrows(IllegalArgumentException.class, () -> Widgets.inflater()
                .inflate(KdlParser.parse("skeleton shape=\"hexagon\"").getFirst()));
    }
}
