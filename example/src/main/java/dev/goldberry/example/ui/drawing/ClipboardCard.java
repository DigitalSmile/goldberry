package dev.goldberry.example.ui.drawing;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.image.Image;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.paint.CanvasStyle;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.text.Paragraph;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.canvas.Input;

/// A board that copies its picture out with `Ctrl+C` and takes one in with
/// `Ctrl+V`, dropped under the pointer.
///
/// A copy and an empty paste change nothing visible, so the card says what the
/// last key did: a demonstration whose success looks like its failure cannot be
/// told from a broken one.
///
/// Read more: [The clipboard](https://goldberry.dev/docs/guide/text.html#the-clipboard).
record ClipboardCard() implements Widget.Stateful {

    /// The canvas's id, which is what a click and a test aim at.
    static final String BOARD = "paste-board";

    @Override
    public State<?> createState() {
        return new ClipboardState();
    }

    static final class ClipboardState extends State<ClipboardCard> {

        /// What the last clipboard key did. Empty at rest.
        private String note = "";

        /// An image pasted onto the board, or null. Null at rest, so the golden is
        /// the same whatever is on the machine's clipboard.
        private @Nullable Image pasted;

        private float pastedX;

        private float pastedY;

        /// Where the pointer last was, for a paste to land at: NaN until it has
        /// been over the board, and a paste then goes to the corner.
        private float pointerX = Float.NaN;

        private float pointerY = Float.NaN;

        private @Nullable Host host;

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            return new ShowcaseCard(
                            "drawing-clipboard",
                            "Copy and paste a picture",
                            "An image goes onto the clipboard as a PNG and comes off it in any format that"
                                    + " decodes. Click the board, then press Ctrl+C to copy the picture out, or Ctrl+V"
                                    + " to paste a screenshot under the pointer.",
                            DocLink.to("guide/text", "the-clipboard"))
                    .of(new Canvas(this::paint, new BoardInput(), Attributes.NONE.id(BOARD)));
        }

        private Optional<Clipboard> clipboard() {
            return Optional.ofNullable(host).map(Host::clipboard);
        }

        private void paint(Frame frame, LogicalSize size, CanvasStyle style) {
            frame.drawImage(Samples.image(), 8, 8);
            var image = pasted;
            if (image != null) {
                // Scaled down to fit if it is a screenshot rather than an icon, and
                // drawn over the board, which is what a paste onto one is.
                var fit = Math.min(1, Math.min(220f / image.width(), 110f / image.height()));
                var wide = image.width() * fit;
                var tall = image.height() * fit;
                frame.drawImage(image, pastedX, pastedY, wide, tall);
                frame.strokePath(Path.roundRect(pastedX, pastedY, wide, tall, 3), Stroke.of(1), Ink.ACCENT);
            }
            if (!note.isEmpty()) {
                // The node's own font, so a theme switch moves this text with the
                // rest of the screen.
                Paragraph.of(style.font(), note).paint(frame, 8, size.height() - 22, size.width() - 16, Ink.ACCENT);
            }
        }

        private void copy(KeyEvent event, Clipboard board) {
            var copied = (pasted == null ? Samples.image() : pasted).toClipboard(board);
            setState(() -> note = copied ? "Copied as image/png." : "The platform declined it.");
            if (copied) {
                event.consume();
            }
        }

        private void paste(KeyEvent event, Clipboard board) {
            var found = Image.fromClipboard(board);
            setState(() -> {
                found.ifPresent(image -> {
                    pasted = image;
                    pastedX = Float.isNaN(pointerX) ? 8 : pointerX;
                    pastedY = Float.isNaN(pointerY) ? 8 : pointerY;
                });
                note = found.map(image -> "Pasted " + image.width() + "x" + image.height() + " under the pointer.")
                        .orElseGet(() -> describe(board));
            });
            // A paste that found nothing leaves the key for whoever else wants it.
            if (found.isPresent()) {
                event.consume();
            }
        }

        /// Why a paste found nothing, in the words of what is there: "empty" and
        /// "an image this toolkit cannot read" are different answers to somebody
        /// who has just pressed Ctrl+V.
        private static String describe(Clipboard board) {
            var types = board.types();
            if (types.isEmpty()) {
                return board.hasText() ? "The clipboard holds text, not an image." : "The clipboard is empty.";
            }
            return "Nothing here can decode: " + String.join(", ", types);
        }

        /// What the board hears: where the pointer is, and Ctrl+C and Ctrl+V.
        private final class BoardInput implements Input {

            @Override
            public void onPointer(PointerEvent event) {
                var at = event.content();
                setState(() -> {
                    pointerX = at.x();
                    pointerY = at.y();
                });
            }

            @Override
            public void onKey(KeyEvent event) {
                if (event.kind() != KeyEvent.Kind.PRESSED || !event.modifiers().control()) {
                    return;
                }
                clipboard().ifPresent(board -> {
                    switch (event.key()) {
                        case C -> copy(event, board);
                        case V -> paste(event, board);
                        default -> {}
                    }
                });
            }

            @Override
            public String accessibleName() {
                return "A board to paste pictures on";
            }
        }
    }
}
