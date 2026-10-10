package dev.goldberry.widgets.core.canvas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.handler.Measured;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.layout.Align;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The widgets a `canvas` places over its drawing, rebuilt from
/// [Canvas#overlay] each time the canvas's content box changes size.
///
/// Three nodes deep under the canvas. This one holds the size, because a widget
/// cannot and an element's state can. Under it a [Frame] covers the canvas's
/// content box, clips to it, and is measured, which is how the size arrives.
/// Under that, one [Slot] per [Positioned], placed absolutely at its
/// rectangle.
///
/// **A frame behind.** The children are built from the size the last frame laid
/// the canvas out at, so the first frame of a canvas has none and the frame
/// after a resize shows the old placement once. Measuring here keeps to what
/// measuring is for: what is read is the canvas's size, and what it decides is
/// where absolute boxes go, which cannot change that size, so the loop settles
/// on the second frame.
///
/// @param overlay what to place, given the content box's size
record CanvasLayer(Function<LogicalSize, List<Positioned>> overlay) implements Widget.Stateful {

    /// One layer per canvas, so it is matched to itself across every rebuild
    /// and the size it has measured survives the application rebuilding the
    /// canvas.
    private static final Object KEY = new Object();

    CanvasLayer {
        Objects.requireNonNull(overlay, "overlay");
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public State<CanvasLayer> createState() {
        return new Layer();
    }

    /// Below half a pixel a size has not changed, which keeps a canvas whose
    /// layout lands on a fraction from rebuilding its children every frame.
    private static final float SETTLED = 0.5f;

    private static final class Layer extends State<CanvasLayer> {

        private @Nullable LogicalSize size;

        private void measured(LogicalSize next) {
            var known = size;
            if (known != null
                    && Math.abs(known.width() - next.width()) < SETTLED
                    && Math.abs(known.height() - next.height()) < SETTLED) {
                return;
            }
            setState(() -> size = next);
        }

        @Override
        public Widget build(BuildContext context) {
            var known = size;
            var slots = new ArrayList<Widget>();
            if (known != null) {
                for (var positioned : widget().overlay().apply(known)) {
                    slots.add(new Slot(positioned.widget(), positioned.at()));
                }
            }
            return new Frame(List.copyOf(slots), this::measured);
        }
    }

    /// The canvas's content box, as a box of its own: what the slots are placed
    /// in and clipped to, and what is measured.
    ///
    /// Placed by [Canvas#render], which has the canvas's padding and border in
    /// hand; this only says it clips.
    record Frame(List<Widget> slots, Consumer<LogicalSize> sink) implements Widget.Leaf, Styled, Paints, Measured {

        @Override
        public List<Widget> children() {
            return slots;
        }

        @Override
        public String cssType() {
            return "canvas-layer";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public void measured(Extent bounds, Extent part) {
            sink.accept(new LogicalSize(bounds.width(), bounds.height()));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    /// One positioned widget's rectangle, which the widget fills.
    ///
    /// Keyed by the widget's own key, so a widget that keeps its key keeps its
    /// element, and its focus and state, when the list is rebuilt in another
    /// order or at another size.
    ///
    /// **It is where a press on a positioned widget stops.** Events bubble, and
    /// the canvas is an ancestor of every widget on it, so without this a press
    /// on an event's button would also reach the week's `Input` as a press on
    /// the grid, and open a quick-add under the event that was clicked. The
    /// widget hears everything first; the canvas hears nothing that landed on
    /// it. The wheel is the exception and goes on up, since it is aimed at
    /// whatever scrolls rather than at the thing under the pointer.
    record Slot(Widget child, LogicalRect at) implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public @Nullable Object key() {
            return child.key();
        }

        @Override
        public List<Widget> children() {
            return List.of(child);
        }

        @Override
        public String cssType() {
            return "canvas-item";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() != PointerEvent.Kind.WHEEL) {
                event.consume();
            }
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            var filled = new Box[children.size()];
            for (var i = 0; i < filled.length; i++) {
                filled[i] = children.get(i).grow(1);
            }
            return Box.of()
                    .style(style)
                    .position(Position.ABSOLUTE)
                    .inset(new Insets(
                            Length.points(at.top()), Length.UNDEFINED, Length.UNDEFINED, Length.points(at.left())))
                    .size(Length.points(at.width()), Length.points(at.height()))
                    .direction(FlexDirection.COLUMN)
                    .alignItems(Align.STRETCH)
                    .children(filled);
        }
    }
}
