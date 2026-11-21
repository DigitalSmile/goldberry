package dev.goldberry.example.ui.input;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.input.dragout.DragOperation;
import dev.goldberry.input.dragout.DragOutcome;
import dev.goldberry.input.drop.Drop;
import dev.goldberry.input.drop.DropTarget;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// A picture and a note that leave the window: each is `draggable` with a
/// `dragOut` offer, so past the threshold the desktop carries it, into a file
/// manager, a browser or an editor. A tray beside them is an ordinary drop
/// target, which the same drag lands on when it comes back over the window.
///
/// Two readouts: the tray says what it holds, and the line under the row says
/// how the last drag ended, as its source heard it through `onDragOutEnd`.
///
/// Read more: [Dropped files and text](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
public record DragOutCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-drag-out";

    /// The ids of the two draggables, by what they carry.
    static String idOf(Keepsake keepsake) {
        return "drag-out-" + keepsake.label();
    }

    /// The tray's id.
    static final String TRAY = "drag-out-tray";

    /// The readout of the last drag's ending.
    static final String READOUT = "drag-out-readout";

    /// What the tray says it holds.
    static final String TRAY_READOUT = "drag-out-tray-readout";

    @Override
    public State<?> createState() {
        return new DragOutState(DragOutExports.temporary());
    }

    /// How the source heard `outcome`, in a sentence about `keepsake`.
    static String describe(Keepsake keepsake, DragOutcome outcome) {
        var what = "The " + keepsake.label();
        return switch (outcome) {
            case DragOutcome.Delivered(var operation) -> what + " went to another application, " + did(operation);
            case DragOutcome.Landed(var accepted) ->
                accepted
                        ? what + " came back over this window and was dropped on the tray."
                        : what + " came back over this window and was let go where nothing takes it.";
            case DragOutcome.Cancelled() -> what + " was put back.";
        };
    }

    private static String did(@Nullable DragOperation operation) {
        if (operation == null) {
            return "which did not say what it did with it.";
        }
        return switch (operation) {
            case COPY -> "which made a copy.";
            case MOVE -> "which says it moved it; the showcase keeps its own.";
            case LINK -> "which linked to it.";
        };
    }

    /// What the tray says when it holds `held`.
    static String holding(Set<Keepsake> held) {
        if (held.isEmpty()) {
            return "The tray. Drop either here to keep it in the window.";
        }
        return "The tray holds "
                + held.stream().map(keepsake -> "the " + keepsake.label()).collect(Collectors.joining(" and "))
                + ".";
    }

    static final class DragOutState extends State<DragOutCard> {

        private final DragOutExports exports;

        private final Set<Keepsake> held = EnumSet.noneOf(Keepsake.class);

        private String readout = "Drag the picture or the note out of the window, or onto the tray.";

        private boolean disposed;

        DragOutState(DragOutExports exports) {
            this.exports = exports;
        }

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            ID,
                            "Dragging out of the window",
                            "A draggable with a dragOut offer is the desktop's drag once it moves four pixels:"
                                    + " drop the picture in a file manager or a browser, the note in an editor."
                                    + " Over this window it is an in-app drag again, and the tray takes it.",
                            DocLink.to("guide/input", "dropped-files-and-text"))
                    .of(
                            new Row(
                                    List.of(picture(), note(), tray()),
                                    Attributes.NONE.id("drag-out-row").classes("drag-out-row")),
                            new Text(readout, Attributes.NONE.id(READOUT).classes("readout")));
        }

        private Widget picture() {
            var art = new Canvas(this::paint, Attributes.NONE.id("drag-out-art"));
            return keepsake(Keepsake.PICTURE, art);
        }

        private Widget note() {
            var preview = new Text(DragOutExports.NOTE, Attributes.NONE.classes("drag-out-preview"));
            return keepsake(Keepsake.NOTE, preview);
        }

        /// A tile that carries `keepsake`: in-app as itself, out of the window
        /// as its offer.
        private Widget keepsake(Keepsake keepsake, Widget shown) {
            return new Panel(
                            List.of(shown, new Text(keepsake.fileName(), Attributes.NONE.classes("drag-out-name"))),
                            Attributes.NONE.id(idOf(keepsake)).classes("keepsake"))
                    .draggable(keepsake)
                    .dragOut(() -> exports.offer(keepsake))
                    .onDragOutEnd(outcome -> ended(keepsake, outcome));
        }

        private Widget tray() {
            return new Panel(
                            List.of(new Text(holding(held), Attributes.NONE.id(TRAY_READOUT))),
                            Attributes.NONE.id(TRAY).classes("drag-tray"))
                    .dropTarget(DropTarget.of(Keepsake.class::isInstance, this::kept));
        }

        private void kept(Drop drop) {
            if (drop.payload() instanceof Keepsake keepsake) {
                setState(() -> held.add(keepsake));
            }
        }

        private void ended(Keepsake keepsake, DragOutcome outcome) {
            // A drag can end after the screen was left: the desktop held it.
            if (disposed) {
                return;
            }
            setState(() -> readout = describe(keepsake, outcome));
        }

        private void paint(Frame frame, LogicalSize size) {
            frame.drawImage(exports.picture(), 0, 0, size.width(), size.height());
        }

        @Override
        protected void dispose() {
            disposed = true;
        }
    }
}
