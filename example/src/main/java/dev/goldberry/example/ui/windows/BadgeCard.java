package dev.goldberry.example.ui.windows;

import java.util.List;
import java.util.Optional;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// A number on the application's dock or launcher icon, and whether the
/// desktop was told.
///
/// Read more: [The badge](https://goldberry.dev/docs/guide/windows.html#the-badge).
public record BadgeCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-badge";

    @Override
    public State<?> createState() {
        return new BadgeState();
    }

    static final class BadgeState extends State<BadgeCard> {

        private int count;

        private String answer = "No badge.";

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            return new ShowcaseCard(
                            ID,
                            "The badge",
                            "host.badge(n) puts a number on the dock or launcher icon, and 0 takes it away. A Linux"
                                    + " dock finds the application by its desktop entry; Windows has no badge, and"
                                    + " the answer says so.",
                            DocLink.to("guide/windows", "the-badge"))
                    .of(
                            new Row(
                                    List.of(
                                            new Button("Add one", () -> show(host, count + 1)).id("badge-add"),
                                            new Button("Clear", () -> show(host, 0)).id("badge-clear")),
                                    Attributes.NONE.classes("toolbar")),
                            new Text(answer, Attributes.NONE.id("badge-answer").classes("readout")));
        }

        private void show(Optional<Host> host, int value) {
            var told = host.map(window -> window.badge(value)).orElse(false);
            setState(() -> {
                count = value;
                answer = "badge(" + value + ") → " + (told ? "the desktop was told" : "the desktop was not told");
            });
        }
    }
}
