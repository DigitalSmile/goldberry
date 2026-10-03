package dev.goldberry.example.ui.drawing;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Dash;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.canvas.Input;

/// A plan the wheel scales and a drag moves.
///
/// The transform is the card's own arithmetic. What the toolkit supplies is the
/// wheel's notches and a drag that keeps reporting after it leaves the box,
/// because the router captures the pointer on press.
///
/// Read more: [Input](https://goldberry.dev/docs/components/drawing.html#input).
record PlanCard() implements Widget.Stateful {

    /// The smallest and largest the plan may be scaled to.
    static final double MIN_ZOOM = 0.4;

    static final double MAX_ZOOM = 3.0;

    @Override
    public State<?> createState() {
        return new PlanState();
    }

    static final class PlanState extends State<PlanCard> {

        private float panX;

        private float panY;

        private float zoom = 1;

        /// Where the press was, less the pan at the time, so a drag moves the plan
        /// by exactly what the pointer moved.
        private float dragX;

        private float dragY;

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            "plan-card",
                            "A wheel and a drag",
                            "Drag the plan to move it and roll the wheel to scale it. The drag keeps reporting"
                                    + " after it leaves the canvas, and consuming the wheel keeps it from scrolling"
                                    + " the page.",
                            DocLink.to("components/drawing", "canvas"))
                    .of(new Canvas(
                            this::paint,
                            new Input() {
                                @Override
                                public void onPointer(PointerEvent event) {
                                    switch (event.kind()) {
                                        case PRESSED ->
                                            setState(() -> {
                                                dragX = event.x() - panX;
                                                dragY = event.y() - panY;
                                            });
                                        case MOVED -> {
                                            if (!Float.isNaN(event.pressX())) {
                                                setState(() -> {
                                                    panX = event.x() - dragX;
                                                    panY = event.y() - dragY;
                                                });
                                            }
                                        }
                                        case WHEEL ->
                                            setState(() -> zoom = (float) Math.clamp(
                                                    zoom * Math.pow(1.1, -event.ticksY()), MIN_ZOOM, MAX_ZOOM));
                                        default -> {}
                                    }
                                    event.consume();
                                }

                                @Override
                                public String accessibleName() {
                                    return "Plan, pan and zoom";
                                }
                            },
                            Attributes.NONE.id("plan")));
        }

        private void paint(Frame frame, LogicalSize size) {
            var cx = size.width() / 2 + panX;
            var cy = size.height() / 2 + panY;

            frame.strokePath(
                    Path.roundRect(cx - 54 * zoom, cy - 30 * zoom, 108 * zoom, 60 * zoom, 6 * zoom),
                    Stroke.of(1.5),
                    Ink.LINE);
            frame.strokePath(
                    Path.line(cx - 54 * zoom, cy, cx + 54 * zoom, cy),
                    Stroke.of(1).dashed(5, 4),
                    Ink.MUTED);
            frame.fillPath(Path.circle(cx, cy, 5 * zoom), Ink.ACCENT);
            frame.strokePath(Path.circle(cx, cy, 26 * zoom), Stroke.round(1.5).dash(Dash.of(0.5, 6)), Ink.WARN);
        }
    }
}
