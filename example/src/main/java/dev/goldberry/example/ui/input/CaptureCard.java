package dev.goldberry.example.ui.input;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

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

/// A pad that keeps hearing a drag after it has left the pad, because the press
/// captured the pointer.
///
/// Read more: [A press captures the pointer](https://goldberry.dev/docs/guide/input.html#a-press-captures-the-pointer).
public record CaptureCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-capture";

    @Override
    public State<?> createState() {
        return new CaptureState();
    }

    static final class CaptureState extends State<CaptureCard> {

        /// Where the press went down and where the pointer is, in the pad's
        /// content box. Null before the first press.
        private float @Nullable [] press;

        private float @Nullable [] now;

        private String readout = "Press in the pad and drag out of it.";

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            ID,
                            "A press captures the pointer",
                            "A press keeps the pointer until the release, so a drag that leaves a widget still reaches"
                                    + " it. Press in the pad, drag well outside it and let go: it reports every move.",
                            DocLink.to("guide/input", "a-press-captures-the-pointer"))
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
                                            return "A pad that reports a drag";
                                        }
                                    })
                                    .id("capture-pad"),
                            new Text(
                                    readout,
                                    Attributes.NONE.id("capture-readout").classes("readout")));
        }

        private void handle(PointerEvent event) {
            var at = event.content();
            var point = new float[] {at.x(), at.y()};
            var outside = at.x() < 0 || at.y() < 0 || at.x() > at.width() || at.y() > at.height();
            switch (event.kind()) {
                case PRESSED ->
                    setState(() -> {
                        press = point;
                        now = point;
                        readout = "Pressed at " + where(point) + ".";
                    });
                case MOVED -> {
                    if (press != null && !Float.isNaN(event.dragX())) {
                        setState(() -> {
                            now = point;
                            readout = "Dragging at " + where(point) + (outside ? ", outside the pad." : ", inside.");
                        });
                    }
                }
                case RELEASED ->
                    setState(() -> {
                        now = point;
                        readout = "Released at " + where(point)
                                + (outside ? ", outside the pad, and the pad still heard it." : ", inside the pad.");
                    });
                default -> {}
            }
            event.consume();
        }

        private static String where(float[] point) {
            return String.format(Locale.ROOT, "(%.0f, %.0f)", point[0], point[1]);
        }

        private void paint(Frame frame, LogicalSize size) {
            InputPads.grid(frame, size);
            var from = press;
            var to = now;
            if (from == null || to == null) {
                return;
            }
            frame.strokePath(Path.line(from[0], from[1], to[0], to[1]), Stroke.of(1.5), InputColors.INK);
            frame.fillPath(Path.circle(from[0], from[1], 4), InputColors.WARN);
            frame.fillPath(Path.circle(to[0], to[1], 4), InputColors.ACCENT);
        }
    }
}
