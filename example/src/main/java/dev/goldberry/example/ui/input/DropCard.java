package dev.goldberry.example.ui.input;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.goldberry.Host;
import dev.goldberry.Window;
import dev.goldberry.bind.Subscription;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.input.drop.FileDrop;
import dev.goldberry.input.drop.TextDrop;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// What was last dropped on the window: the files, or the text, and the point
/// it landed on.
///
/// The listeners are the window's, so the card subscribes while it is on screen
/// and closes both subscriptions when it leaves.
///
/// Read more: [Dropped files and text](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
public record DropCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-drop";

    @Override
    public State<?> createState() {
        return new DropState();
    }

    /// The window under `host`, or empty for a host that has none: a test, or a
    /// picture taken offscreen.
    static Optional<Window> windowOf(Host host) {
        try {
            return Optional.of(host.window());
        } catch (UnsupportedOperationException noWindow) {
            return Optional.empty();
        }
    }

    static final class DropState extends State<DropCard> {

        private final List<Subscription> listening = new ArrayList<>();

        private boolean asked;

        private String dropped = "Drop files or a selection of text anywhere on this window.";

        @Override
        public Widget build(BuildContext context) {
            if (!asked) {
                asked = true;
                var window = context.host().flatMap(DropCard::windowOf);
                window.ifPresentOrElse(
                        this::listen, () -> dropped = "This host has no window, so nothing can be dropped on it.");
            }
            return new ShowcaseCard(
                            ID,
                            "Dropped files and text",
                            "A drop arrives as one event with every path and the point it landed on, so a board"
                                    + " knows where to put it. Drag files or text from another application onto"
                                    + " this window.",
                            DocLink.to("guide/input", "dropped-files-and-text"))
                    .of(new Panel(
                            List.of(new Text(
                                    dropped, Attributes.NONE.id("drop-readout").classes("readout"))),
                            Attributes.NONE.id("drop-target").classes("drop-target")));
        }

        private void listen(Window window) {
            listening.add(window.onFileDrop(drop -> setState(() -> dropped = describe(drop))));
            listening.add(window.onTextDrop(drop -> setState(() -> dropped = describe(drop))));
        }

        static String describe(FileDrop drop) {
            var names = drop.paths().stream()
                    .map(Path::getFileName)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            return drop.count() + (drop.count() == 1 ? " file" : " files") + " at " + at(drop.at()) + ": " + names;
        }

        static String describe(TextDrop drop) {
            return drop.count() + (drop.count() == 1 ? " line" : " lines") + " of text at " + at(drop.at()) + ": "
                    + drop.first();
        }

        private static String at(LogicalPoint point) {
            return String.format(Locale.ROOT, "(%.0f, %.0f)", point.x(), point.y());
        }

        @Override
        protected void dispose() {
            listening.forEach(Subscription::close);
            listening.clear();
        }
    }
}
