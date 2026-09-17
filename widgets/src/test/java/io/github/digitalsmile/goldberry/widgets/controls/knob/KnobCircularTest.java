package io.github.digitalsmile.goldberry.widgets.controls.knob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// §3's "circular-drag optional" ([ADR-0369]).
class KnobCircularTest {

    /// The angle a fraction of the travel points at.
    private static double at(double fraction) {
        return Knob.angleAt(fraction);
    }

    /// The angle in the middle of the 90° gap at the bottom.
    private static final double GAP = Knob.angleAt(1) + (2 * Math.PI - Knob.ARC_SWEEP) / 2;

    @Nested
    @DisplayName("the arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("on the travel, the angle is the value")
        void followsTheAngle() {
            assertEquals(0.25, Knob.circularFraction(at(0.25), 0.2), 1e-9);
            assertEquals(0.5, Knob.circularFraction(at(0.5), 0.4), 1e-9);
        }

        @Test
        @DisplayName("in the gap, the value stays at the end it is nearer")
        void gapHolds() {
            assertEquals(1, Knob.circularFraction(GAP, 0.97), 1e-9);
            assertEquals(0, Knob.circularFraction(GAP, 0.03), 1e-9);
        }

        @Test
        @DisplayName("coming out of the gap on the far side is a jump, and is refused")
        void noJumpAcrossTheGap() {
            assertEquals(1, Knob.circularFraction(at(0.02), 1.0), 1e-9, "pushed past the top, it stays at the top");
            assertEquals(0, Knob.circularFraction(at(0.98), 0.0), 1e-9);
        }
    }

    @Nested
    @DisplayName("the drag")
    class Drag {

        private static PointerEvent moveAt(double angle) {
            var event = new PointerEvent(PointerEvent.Kind.MOVED, 0, 0, null, 0, 0, 0, null);
            event.anchoredAt(0.3);
            var radius = 20;
            event.localTo(new PointerEvent.Local(
                    (float) (24 + radius * Math.cos(angle)), (float) (24 + radius * Math.sin(angle)), 48, 48));
            return event;
        }

        @Test
        @DisplayName("a circular knob asks for the value under the pointer's angle")
        void circularAsks() {
            var asked = new ArrayList<Double>();
            var knob = new Knob(0.3, asked::add).circular(true);

            var event = moveAt(at(0.75));
            knob.onPointer(event);

            assertEquals(0.75, asked.getLast(), 1e-3);
            assertTrue(event.isConsumed());
        }

        @Test
        @DisplayName("an ordinary knob ignores the angle and reads the vertical travel")
        void verticalByDefault() {
            var asked = new ArrayList<Double>();
            var knob = new Knob(0.3, asked::add);

            knob.onPointer(moveAt(at(0.75)));

            assertEquals(List.of(0.3), asked, "no vertical travel, so the anchor's value");
        }
    }

    @Test
    @DisplayName("markup says drag=\"circular\"")
    void markup() {
        var circular = (Knob) Widgets.inflater()
                .inflate(KdlParser.parse("knob drag=\"circular\"").getFirst());
        var plain = (Knob) Widgets.inflater().inflate(KdlParser.parse("knob").getFirst());

        assertTrue(circular.circular());
        assertFalse(plain.circular());
    }
}
