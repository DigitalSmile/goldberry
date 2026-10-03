package dev.goldberry.example.ui.layout;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAnchor;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// A viewport that opens at its **end**: the log, the console and the chat
/// timeline, which are one shape.
///
/// **Log a line** appends. Press it at the bottom and the view follows; scroll up
/// first and it does not move you at all. **Load older** puts a page of lines
/// *above* everything on screen, and nothing you are looking at moves, because the
/// offset moves by the height that arrived.
///
/// The lines are keyed: keeping a reader's line means recognising it a frame
/// later, and a list matched by position has no such line. The counter is the
/// identity, which is what a real timeline's message id is.
///
/// Read more: [A timeline](https://goldberry.dev/docs/layout/scroll.html#a-timeline).
public record ConsoleCard() implements Widget.Stateful {

    /// How many lines the log starts with. Enough to overflow the frame several
    /// times, so that "opens at the end" is visibly not "opens".
    public static final int LINES = 24;

    /// How many a press of Load older brings in: more than a screenful, so the
    /// thumb visibly shrinks while the text visibly does not move.
    public static final int PAGE = 12;

    /// The card the log is shown in.
    static final ShowcaseCard CARD = new ShowcaseCard(
            "console-card",
            "A log that opens at its end",
            "With anchor=end a scroll opens on its newest line and stays there while you are on it."
                    + " Scroll up and a new line leaves you alone; load older ones and the line you are reading"
                    + " stays still.",
            DocLink.to("layout/scroll", "scroll"));

    @Override
    public State<?> createState() {
        return new ConsoleState();
    }

    private static final class ConsoleState extends State<ConsoleCard> {

        /// The lines, newest last, each with the id it was written under.
        private final List<Line> lines = new ArrayList<>();

        private int next = 1;
        private int oldest;

        @Override
        protected void initState() {
            for (var i = 0; i < LINES; i++) {
                lines.add(log());
            }
        }

        @Override
        public Widget build(BuildContext context) {
            var rows = new ArrayList<Widget>(lines.size());
            for (var line : lines) {
                rows.add(new Text(
                        line.text(), Attributes.NONE.classes("scroll-row").key(line.id())));
            }
            return CARD.of(
                    new Row(
                                    new Button("Log a line", this::append)
                                            .withAttributes(Attributes.NONE.id("console-log")),
                                    new Button("Load older", this::prepend)
                                            .withAttributes(Attributes.NONE.id("console-older")))
                            .withAttributes(Attributes.NONE.id("console-bar").classes("toolbar")),
                    new Panel(
                            List.of(new Scroll(rows, ScrollAxis.VERTICAL, Attributes.NONE).anchor(ScrollAnchor.END)),
                            Attributes.NONE.id("console-demo").classes("scroll-demo")));
        }

        /// A message arrives at the bottom.
        private void append() {
            setState(() -> lines.add(log()));
        }

        /// A page of history arrives at the top.
        private void prepend() {
            setState(() -> {
                for (var i = 0; i < PAGE; i++) {
                    oldest++;
                    lines.addFirst(new Line("older-" + oldest, "…  earlier line −" + oldest));
                }
            });
        }

        private Line log() {
            var id = next++;
            return new Line("line-" + id, id + "  ▸  the build finished in " + (40 + id % 7) + "ms");
        }
    }

    /// One line, and the identity that outlives its position in the list.
    private record Line(String id, String text) {}
}
