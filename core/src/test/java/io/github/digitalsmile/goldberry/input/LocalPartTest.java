package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Measuring the pointer against a **part** rather than against the control
/// ([ADR-0080](../../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)).
///
/// A control's hit target and the thing it is pointed *along* are the same box
/// until something else joins the row. `slider` is where that stopped being true
/// — a value label at the end takes its width off the track — and the failure it
/// produces is the quiet kind: every pixel is drawn where the stylesheet asked
/// and the far end of the track reads as 88%.
///
/// Written against bare widgets in `:core` rather than against `slider`, for the
/// reason [DragOriginTest] and [FocusScopeTest] are: `knob`, `split-pane` and a
/// scrollbar all want this and none of them will look like a slider.
class LocalPartTest {

    private final List<Double> seen = new ArrayList<>();

    /// A control that measures along its `part` child, exactly as [Handles]
    /// describes.
    private class Control implements Widget.Leaf, Styled, Handles {

        @Override
        public String cssType() {
            return "control";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public String localPart() {
            return "part";
        }

        @Override
        public List<Widget> children() {
            return List.of(new Part());
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.PRESSED) {
                seen.add(event.local().fractionX());
            }
        }
    }

    private record Part() implements Widget.Leaf, Styled {

        @Override
        public String cssType() {
            return "part";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }
    }

    /// The control is 100 wide and the part is the first 80 of it — a label of 20
    /// at the end, which is what `slider-value` is.
    private PointerRouter router(boolean withPart) {
        var router = new PointerRouter();
        var tree = new ElementTree(new Control());
        var regions = new ArrayList<HitTest.Region>();
        regions.add(HitTest.Region.of(tree.root(), 0, 0, 100, 32));
        if (withPart) {
            regions.add(HitTest.Region.of(tree.root().children().getFirst(), 0, 0, 80, 32));
        }
        router.updateRegions(List.copyOf(regions));
        return router;
    }

    /// The one press this test fired, which is also the assertion that exactly
    /// one was dispatched.
    private double only() {
        assertEquals(1, seen.size(), "one press, one fraction");
        return seen.getFirst();
    }

    @Test
    @DisplayName("the fraction is along the part, so the part's far end is 100%")
    void measuredAlongThePart() {
        router(true).pointerPressed(80, 16, PointerEvent.Button.PRIMARY, 1);

        // 80 of the control's 100 and all of the part's 80. Measured against the
        // control this reads 0.8, which is a plausible number and the wrong one.
        assertEquals(1, only(), 1e-6);
    }

    @Test
    @DisplayName("the middle of the part is the middle of the value, not the middle of the control")
    void middleIsThePartsMiddle() {
        router(true).pointerPressed(40, 16, PointerEvent.Button.PRIMARY, 1);

        assertEquals(0.5, only(), 1e-6);
    }

    /// A part that has not been painted — a label being built, a first frame —
    /// falls back to the control's own box. Slightly wrong beats refusing to
    /// move: a control whose scale is missing is still a control.
    @Test
    @DisplayName("a part with no rectangle falls back to the widget's own box")
    void missingPartFallsBack() {
        router(false).pointerPressed(80, 16, PointerEvent.Button.PRIMARY, 1);

        assertEquals(0.8, only(), 1e-6);
    }
}
