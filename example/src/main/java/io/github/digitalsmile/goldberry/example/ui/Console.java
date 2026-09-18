package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAnchor;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// A viewport that opens at its **end** — the log, the console and the chat
/// timeline, which are one shape (`docs/gaps.md` G48, ADR-0392).
///
/// [Scrolling]'s card is the other half of the same primitive and is deliberately
/// beside it: one shows a reader being taken somewhere, this one shows a reader
/// being left exactly where they are while the document changes underneath.
///
/// ## What the two buttons are for
///
/// **Log a line** appends. Press it at the bottom and the view follows; scroll up
/// first and it does not move you at all, which is the distinction the whole
/// widget turns on and the one a demonstration has to let you feel rather than
/// read.
///
/// **Load older** puts twelve lines *above* everything on screen. Nothing you are
/// looking at moves, because the offset moves by the height that arrived — the
/// bar gets shorter and the words stay still.
///
/// ## Why the lines are keyed
///
/// Keeping a reader's line means recognising it a frame later, and a list matched
/// by position has no such line — element 0 would simply describe a different
/// message. The counter is the identity, which is what a real timeline's message
/// id is.
public record Console() implements Widget.Stateful {

    /// How many lines the log starts with. Enough to overflow the frame several
    /// times, so that "opens at the end" is visibly not "opens".
    public static final int LINES = 24;

    /// How many a press of Load older brings in — more than a screenful, so the
    /// thumb visibly shrinks while the text visibly does not move.
    public static final int PAGE = 12;

    @Override
    public State<?> createState() {
        return new ConsoleState();
    }

    private static final class ConsoleState extends State<Console> {

        /// The lines, newest last, each with the id it was written under.
        private final List<Line> lines = new ArrayList<>();

        private int next = 1;
        private int oldest;

        @Override
        protected void initState() {
            for (var i = 0; i < Console.LINES; i++) {
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
            return Notifications.card(
                    "console-card",
                    "A viewport that opens at its end",
                    List.of(
                            new Text(
                                    "It opens on the newest line and stays there while you are on it."
                                            + " Scroll up and a new line leaves you alone; load older ones"
                                            + " and the words you are reading do not move.",
                                    Attributes.NONE.classes("caption")),
                            new Row(
                                            new Button("Log a line", this::append)
                                                    .withAttributes(Attributes.NONE.id("console-log")),
                                            new Button("Load older", this::prepend)
                                                    .withAttributes(Attributes.NONE.id("console-older")))
                                    .withAttributes(
                                            Attributes.NONE.id("console-bar").classes("toolbar")),
                            new Panel(
                                    List.of(new Scroll(rows, ScrollAxis.VERTICAL, Attributes.NONE)
                                            .anchor(ScrollAnchor.END)),
                                    Attributes.NONE.id("console-demo").classes("scroll-demo"))));
        }

        /// A message arrives at the bottom.
        private void append() {
            setState(() -> lines.add(log()));
        }

        /// A page of history arrives at the top.
        private void prepend() {
            setState(() -> {
                for (var i = 0; i < Console.PAGE; i++) {
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
