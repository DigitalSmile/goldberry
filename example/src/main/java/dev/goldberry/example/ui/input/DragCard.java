package dev.goldberry.example.ui.input;

import java.util.Locale;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.canvas.Input;
import dev.goldberry.widgets.text.Text;

/// A track that is set by dragging along it, reading what the router reports on
/// every event of the gesture: where the press went down, how far the pointer
/// has travelled, and the fraction of the box it is at.
///
/// Read more: [Gestures: where the drag
/// started](https://goldberry.dev/docs/guide/input.html#gestures-where-the-drag-started).
public record DragCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-gestures";

    @Override
    public State<?> createState() {
        return new DragState();
    }

    static final class DragState extends State<DragCard> {

        /// Where along the track the value is, 0 to 1.
        private double fraction = 0.3;

        private String readout = "Drag along the track. No button held: dragX is NaN.";

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            ID,
                            "Where the drag started",
                            "The router remembers a gesture for the widget: pressX and pressY are where the button"
                                    + " went down, dragX and dragY how far it has moved since, and fractionX where"
                                    + " the pointer is along the box, 0 to 1.",
                            DocLink.to("guide/input", "gestures-where-the-drag-started"))
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
                                            return "A track set by dragging";
                                        }
                                    })
                                    .id("drag-track"),
                            new Text(readout, Attributes.NONE.id("drag-readout").classes("readout")));
        }

        private void handle(PointerEvent event) {
            var held = event.kind() == PointerEvent.Kind.PRESSED
                    || (event.kind() == PointerEvent.Kind.MOVED && !Float.isNaN(event.dragX()));
            if (!held) {
                return;
            }
            var along = event.content().fractionX();
            setState(() -> {
                fraction = along;
                readout = String.format(
                        Locale.ROOT,
                        "pressX %.0f  pressY %.0f  dragX %+.0f  dragY %+.0f  fractionX %.2f",
                        event.pressX(),
                        event.pressY(),
                        event.dragX(),
                        event.dragY(),
                        along);
            });
            event.consume();
        }

        private void paint(Frame frame, LogicalSize size) {
            var middle = size.height() / 2;
            var end = (float) (size.width() * fraction);
            frame.strokePath(Path.line(0, middle, size.width(), middle), Stroke.round(4), InputColors.MUTED);
            frame.strokePath(Path.line(0, middle, end, middle), Stroke.round(4), InputColors.INK);
            frame.fillPath(Path.circle(end, middle, 9), InputColors.ACCENT);
        }
    }
}
