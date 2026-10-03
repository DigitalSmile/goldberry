package dev.goldberry.example.ui.windows;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.render.display.Display;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// The displays connected now, the primary one first, asked fresh on every
/// press because a monitor can come and go at any moment.
///
/// Read more: [Displays](https://goldberry.dev/docs/guide/windows.html#displays).
public record DisplaysCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-displays";

    @Override
    public State<?> createState() {
        return new DisplaysState();
    }

    /// One display on one line: its name, where it is, what a window may use of
    /// it, and its scale.
    static String describe(Display display) {
        return String.format(
                Locale.ROOT,
                "%s%s  %s  usable %s  %.0f%%",
                display.name(),
                display.primary() ? " (primary)" : "",
                PositionCard.rect(display.bounds()),
                PositionCard.size(display.usableBounds().size()),
                display.scale().factor() * 100);
    }

    static final class DisplaysState extends State<DisplaysCard> {

        private List<String> lines = List.of("Press List to ask.");

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            return new ShowcaseCard(
                            ID,
                            "Displays",
                            "host.displays() lists the displays connected now: each one's name, bounds, the usable"
                                    + " part a window may take, its scale, and which is primary. Remember a display"
                                    + " by its name; its id is for this run only.",
                            DocLink.to("guide/windows", "displays"))
                    .of(
                            new Button("List the displays", () -> list(host)).id("displays-list"),
                            new Column(
                                    lines.stream()
                                            .<Widget>map(line -> new Text(line, Attributes.NONE.classes("readout")))
                                            .toList(),
                                    Attributes.NONE.id("displays-lines").classes("readout-lines")));
        }

        private void list(Optional<Host> host) {
            var displays = host.map(Host::displays).orElse(List.of());
            setState(() -> lines = displays.isEmpty()
                    ? List.of("None: this host has no desktop under it.")
                    : displays.stream().map(DisplaysCard::describe).toList());
        }
    }
}
