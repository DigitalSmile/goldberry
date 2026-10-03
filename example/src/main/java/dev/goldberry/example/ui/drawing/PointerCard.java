package dev.goldberry.example.ui.drawing;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.value.CssColor;
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

/// A canvas that hears the pointer: a grid, and a crosshair wherever the pointer
/// is over it.
///
/// The canvas has padding, so this is the drawing that would be visibly wrong if
/// input were measured from the border box rather than from the corner the
/// painter draws at: the crosshair would trail the pointer by the padding.
///
/// Read more: [Input](https://goldberry.dev/docs/components/drawing.html#input).
record PointerCard() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new PointerState();
    }

    static final class PointerState extends State<PointerCard> {

        /// Where the pointer is, in the painter's coordinates, or null when it is
        /// not over the canvas. Null at rest, so the picture before anyone has
        /// touched it is the same on every machine.
        private PointerEvent.@Nullable Local at;

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            "pointer-card",
                            "Where the pointer is",
                            "An Input beside the painter makes a canvas hear the pointer, measured from the corner"
                                    + " the painter draws at. Move the pointer over the grid: the crosshair lands under"
                                    + " it although the canvas has padding.",
                            DocLink.to("components/drawing", "canvas"))
                    .of(new Canvas(
                            this::paint,
                            new Input() {
                                @Override
                                public void onPointer(PointerEvent event) {
                                    var content = event.content();
                                    setState(() -> at = event.kind() == PointerEvent.Kind.EXITED ? null : content);
                                }

                                @Override
                                public String accessibleName() {
                                    return "Pointer position";
                                }
                            },
                            Attributes.NONE.id("pointer")));
        }

        private void paint(Frame frame, LogicalSize size) {
            var width = size.width();
            var height = size.height();

            var grid = Path.builder();
            for (var x = 0f; x <= width; x += 24) {
                grid.moveTo(x, 0).lineTo(x, height);
            }
            for (var y = 0f; y <= height; y += 24) {
                grid.moveTo(0, y).lineTo(width, y);
            }
            frame.strokePath(grid.build(), Stroke.of(1), CssColor.fade(Ink.MUTED, 0.5));

            var point = at;
            if (point == null) {
                return;
            }
            frame.strokePath(
                    Path.builder()
                            .moveTo(0, point.y())
                            .lineTo(width, point.y())
                            .moveTo(point.x(), 0)
                            .lineTo(point.x(), height)
                            .build(),
                    Stroke.of(1).dashed(3, 3),
                    Ink.ACCENT);
            frame.fillPath(Path.circle(point.x(), point.y(), 4), Ink.ACCENT);
        }
    }
}
