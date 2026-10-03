package dev.goldberry.example.ui.input;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.paint.CanvasStyle;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.text.Paragraph;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.canvas.Input;
import dev.goldberry.widgets.text.Text;

/// A focusable pad that logs the two streams a keyboard produces: keys going
/// down, and the text they commit.
///
/// Read more: [Keys and text are different
/// events](https://goldberry.dev/docs/guide/input.html#keys-and-text-are-different-events).
public record KeysCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-keys";

    /// How many events the log keeps.
    static final int KEPT = 8;

    @Override
    public State<?> createState() {
        return new KeysState();
    }

    static final class KeysState extends State<KeysCard> {

        /// The newest event last.
        private final List<String> log = new ArrayList<>();

        private boolean focused;

        @Override
        public Widget build(BuildContext context) {
            var lines = log.isEmpty() ? List.of("Nothing yet.") : List.copyOf(log);
            return new ShowcaseCard(
                            ID,
                            "Keys and text are different events",
                            "A key event is a key going down or up; a text event is what the keyboard or the input"
                                    + " method committed. Click the pad and type: Shift+A is two keys and one"
                                    + " letter, and a dead key is a key with no text.",
                            DocLink.to("guide/input", "keys-and-text-are-different-events"))
                    .of(
                            new Canvas(this::paint, new Input() {
                                        @Override
                                        public void onKey(KeyEvent event) {
                                            if (event.kind() != KeyEvent.Kind.PRESSED) {
                                                return;
                                            }
                                            var keys = new Shortcut(event.key(), event.modifiers());
                                            add("key   " + keys + (event.isRepeat() ? "  (repeat)" : ""));
                                            // A key the pad does not want goes on: Tab still moves
                                            // focus, and Ctrl+T still reaches the window.
                                            var plain = !event.modifiers().control()
                                                    && !event.modifiers().alt()
                                                    && !event.modifiers().meta();
                                            if (plain && event.key() != Key.TAB) {
                                                event.consume();
                                            }
                                        }

                                        @Override
                                        public void onText(TextEvent event) {
                                            add("text  \"" + event.text() + "\"");
                                            event.consume();
                                        }

                                        @Override
                                        public void onFocusChanged(boolean now, boolean fromKeyboard) {
                                            setState(() -> focused = now);
                                        }

                                        @Override
                                        public boolean wantsText() {
                                            return true;
                                        }

                                        @Override
                                        public String accessibleName() {
                                            return "A pad that logs keys and text";
                                        }
                                    })
                                    .id("keys-pad"),
                            new Column(
                                    lines.stream()
                                            .<Widget>map(line -> new Text(line, Attributes.NONE.classes("readout")))
                                            .toList(),
                                    Attributes.NONE.id("keys-log").classes("readout-lines")));
        }

        private void add(String line) {
            setState(() -> {
                log.add(line);
                while (log.size() > KEPT) {
                    log.removeFirst();
                }
            });
        }

        private void paint(Frame frame, LogicalSize size, CanvasStyle style) {
            var label = focused ? "Type. The log below fills in." : "Click here, then type.";
            Paragraph.of(style.font(), label).paint(frame, 12, 12, size.width() - 24, style.ink());
            if (focused) {
                frame.strokePath(
                        Path.roundRect(1, 1, size.width() - 2, size.height() - 2, 6), Stroke.of(2), InputColors.ACCENT);
            }
        }
    }
}
