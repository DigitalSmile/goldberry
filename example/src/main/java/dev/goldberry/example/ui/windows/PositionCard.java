package dev.goldberry.example.ui.windows;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.goldberry.Host;
import dev.goldberry.Window;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// Where this window is, in the desktop's coordinates, and what an application
/// would save to open it there again. Read on a press, and moved on another.
///
/// Read more: [Where a window opens](https://goldberry.dev/docs/guide/windows.html#where-a-window-opens).
public record PositionCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-position";

    @Override
    public State<?> createState() {
        return new PositionState();
    }

    /// What `window` reports about its place, a line per fact.
    static List<String> describe(Window window) {
        var lines = new ArrayList<String>();
        lines.add(
                "position()      " + window.position().map(PositionCard::point).orElse("empty"));
        lines.add("normalBounds()  "
                + window.normalBounds().map(PositionCard::rect).orElse("empty"));
        lines.add("normalSize()    " + size(window.normalSize()));
        lines.add("isMaximized()   " + window.isMaximized());
        lines.add("display()       "
                + window.display().map(display -> display.name()).orElse("empty"));
        return lines;
    }

    static String point(LogicalPoint point) {
        return String.format(Locale.ROOT, "%.0f, %.0f", point.x(), point.y());
    }

    static String size(LogicalSize size) {
        return String.format(Locale.ROOT, "%.0f × %.0f", size.width(), size.height());
    }

    static String rect(LogicalRect rect) {
        return size(rect.size()) + " at " + point(rect.origin());
    }

    static final class PositionState extends State<PositionCard> {

        private List<String> lines = List.of("Press Read to ask the window.");

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            return new ShowcaseCard(
                            ID,
                            "Where a window opens",
                            "A window opens where position() and display() say, clamped onto a display that exists."
                                    + " normalBounds() is what to save: the bounds it returns to from maximized."
                                    + " Wayland places windows itself, and answers empty.",
                            DocLink.to("guide/windows", "where-a-window-opens"))
                    .of(
                            new Row(
                                    List.of(
                                            new Button("Read", () -> read(host)).id("position-read"),
                                            new Button("Centre on its display", () -> centre(host))
                                                    .id("position-centre")),
                                    Attributes.NONE.classes("toolbar")),
                            new Column(
                                    lines.stream()
                                            .<Widget>map(line -> new Text(line, Attributes.NONE.classes("readout")))
                                            .toList(),
                                    Attributes.NONE.id("position-lines").classes("readout-lines")));
        }

        private void read(Optional<Host> host) {
            var window = host.flatMap(HostWindow::of);
            setState(() -> lines = window.map(PositionCard::describe).orElse(List.of(HostWindow.NONE)));
        }

        private void centre(Optional<Host> host) {
            var window = host.flatMap(HostWindow::of);
            if (window.isEmpty()) {
                setState(() -> lines = List.of(HostWindow.NONE));
                return;
            }
            var it = window.orElseThrow();
            var moved = it.display()
                    .map(display -> {
                        var area = display.usableBounds();
                        var size = it.size();
                        return it.move(LogicalPoint.of(
                                area.left() + (area.width() - size.width()) / 2,
                                area.top() + (area.height() - size.height()) / 2));
                    })
                    .orElse(false);
            setState(() -> {
                var now = new ArrayList<>(describe(it));
                now.addFirst(moved ? "move() → moved" : "move() → refused, as Wayland refuses every move");
                lines = now;
            });
        }
    }
}
