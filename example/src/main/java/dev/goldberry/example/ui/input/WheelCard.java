package dev.goldberry.example.ui.input;

import java.util.Locale;

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
import dev.goldberry.widgets.text.Text;

/// A plan the wheel zooms and a drag moves, with the wheel's own numbers under
/// it: the fractional lines a touchpad sends and the whole detents a mouse does.
///
/// Read more: [The wheel is lines](https://goldberry.dev/docs/guide/input.html#the-wheel-is-lines).
public record WheelCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-wheel";

    @Override
    public State<?> createState() {
        return new WheelState();
    }

    static final class WheelState extends State<WheelCard> {

        /// How far the plan has been dragged, and how far it has been zoomed.
        private float panX;

        private float panY;

        private float zoom = 1;

        /// The pan when the drag began, so the drag adds to it.
        private float startX;

        private float startY;

        private String readout = "Roll the wheel over the plan, or drag it.";

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            ID,
                            "The wheel is lines",
                            "A wheel event is in lines, fractional and positive down: deltaY keeps a touchpad's"
                                    + " fractions and ticksY counts a mouse's detents. Roll over the plan to zoom it,"
                                    + " and drag to move it.",
                            DocLink.to("guide/input", "the-wheel-is-lines"))
                    .of(
                            new Canvas(this::paint, new Input() {
                                        @Override
                                        public void onPointer(PointerEvent event) {
                                            handle(event);
                                        }

                                        @Override
                                        public boolean focusable() {
                                            return false;
                                        }

                                        @Override
                                        public String accessibleName() {
                                            return "Plan, pan and zoom";
                                        }
                                    })
                                    .id("wheel-plan"),
                            new Text(
                                    readout, Attributes.NONE.id("wheel-readout").classes("readout")));
        }

        private void handle(PointerEvent event) {
            switch (event.kind()) {
                case PRESSED ->
                    setState(() -> {
                        startX = panX;
                        startY = panY;
                    });
                case MOVED -> {
                    if (!Float.isNaN(event.dragX())) {
                        setState(() -> {
                            panX = startX + event.dragX();
                            panY = startY + event.dragY();
                        });
                    }
                }
                case WHEEL ->
                    setState(() -> {
                        zoom = (float) Math.clamp(zoom * Math.pow(1.1, -event.deltaY()), 0.4, 3.0);
                        readout = String.format(
                                Locale.ROOT,
                                "deltaY %.2f lines  ticksY %d  zoom %.2f×",
                                event.deltaY(),
                                event.ticksY(),
                                zoom);
                    });
                default -> {}
            }
            // The page behind does not scroll while the plan is being zoomed.
            event.consume();
        }

        private void paint(Frame frame, LogicalSize size) {
            InputPads.grid(frame, size);
            var cx = size.width() / 2 + panX;
            var cy = size.height() / 2 + panY;
            frame.strokePath(
                    Path.roundRect(cx - 54 * zoom, cy - 30 * zoom, 108 * zoom, 60 * zoom, 6 * zoom),
                    Stroke.of(1.5),
                    InputColors.INK);
            frame.strokePath(
                    Path.line(cx - 54 * zoom, cy, cx + 54 * zoom, cy),
                    Stroke.of(1).dashed(5, 4),
                    InputColors.MUTED);
            frame.fillPath(Path.circle(cx, cy, 5 * zoom), InputColors.ACCENT);
            frame.strokePath(Path.circle(cx, cy, 26 * zoom), Stroke.round(1.5).dash(Dash.of(0.5, 6)), InputColors.WARN);
        }
    }
}
