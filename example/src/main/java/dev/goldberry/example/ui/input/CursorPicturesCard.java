package dev.goldberry.example.ui.input;

import java.util.List;

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
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.canvas.Input;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// The showcase's own cursors at work: a card on a strip that says
/// `cursor: grab`, and the strip saying `cursor: grabbing` while the card is
/// held.
///
/// Two things are shown at once. The open and the closed hand are pictures the
/// showcase drew, given to the toolkit through `Application.cursors()`, because
/// no platform has either shape. And the hand closes **during** the drag: the
/// strip holds the pointer from the press, and when its own `cursor` changes in
/// the next frame the pointer follows it, where every other box under a drag
/// leaves the shape alone.
///
/// Read more: [The cursor](https://goldberry.dev/docs/guide/input.html#the-cursor).
public record CursorPicturesCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-cursor-pictures";

    /// The strip's id; the stylesheet gives it `grab`, and `grabbing` when held.
    static final String STRIP = "pick-strip";

    /// The class the strip wears while the card is held.
    static final String HELD = "held";

    private static final double CARD_WIDTH = 40;

    private static final double CARD_HEIGHT = 56;

    @Override
    public State<?> createState() {
        return new PickState();
    }

    static final class PickState extends State<CursorPicturesCard> {

        /// Where along the strip the card is, 0 to 1.
        private double fraction = 0.2;

        private boolean held;

        @Override
        public Widget build(BuildContext context) {
            var strip = Attributes.NONE.id(STRIP);
            return new ShowcaseCard(
                            ID,
                            "Cursors the application drew",
                            "grab and grabbing have no system cursor anywhere, so the showcase draws its own hands"
                                    + " at four sizes and gives them to Application.cursors(). Pick the card up: the"
                                    + " hand closes mid-drag, because the box holding the pointer changed its own"
                                    + " cursor.",
                            DocLink.to("guide/input", "the-cursor"))
                    .of(
                            new Canvas(
                                    this::paint,
                                    new Input() {
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
                                            return "A card to pick up and move along the strip";
                                        }
                                    },
                                    held ? strip.classes(HELD) : strip),
                            new Text(
                                    held
                                            ? "Held: the strip says cursor: grabbing, and the hand closed."
                                            : "cursor: grab. Press on the strip to pick the card up.",
                                    Attributes.NONE.id("pick-readout").classes("readout")),
                            new Row(
                                    List.of(shape("grab"), shape("grabbing")),
                                    Attributes.NONE.id("pick-shapes").classes("cursor-boxes")));
        }

        /// A box wearing one of the two shapes, to compare the pictures still.
        private static Widget shape(String name) {
            return new Panel(
                    List.of(new Text(name)), Attributes.NONE.id("pick-" + name).classes("cursor-box"));
        }

        private void handle(PointerEvent event) {
            switch (event.kind()) {
                case PRESSED -> {
                    var along = event.content().fractionX();
                    setState(() -> {
                        held = true;
                        fraction = along;
                    });
                    event.consume();
                }
                case MOVED -> {
                    if (held) {
                        var along = event.content().fractionX();
                        setState(() -> fraction = along);
                        event.consume();
                    }
                }
                case RELEASED -> {
                    if (held) {
                        setState(() -> held = false);
                        event.consume();
                    }
                }
                default -> {}
            }
        }

        private void paint(Frame frame, LogicalSize size) {
            var travel = Math.max(0, size.width() - CARD_WIDTH);
            var left = Math.clamp(fraction, 0, 1) * travel;
            var top = (size.height() - CARD_HEIGHT) / 2;
            // A held card is lifted: a step up, with its shadow left behind.
            var lift = held ? 4 : 0;
            if (held) {
                frame.fillPath(Path.roundRect(left + 3, top + 3, CARD_WIDTH, CARD_HEIGHT, 6), InputColors.MUTED);
            }
            var card = Path.roundRect(left, top - lift, CARD_WIDTH, CARD_HEIGHT, 6);
            frame.fillPath(card, held ? InputColors.WARN : InputColors.INK);
            frame.strokePath(card, Stroke.of(1.5), InputColors.ACCENT);
        }
    }
}
