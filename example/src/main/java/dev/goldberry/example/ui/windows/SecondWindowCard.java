package dev.goldberry.example.ui.windows;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.WindowHost;
import dev.goldberry.bind.Subscription;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.Ownership;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// A second top-level window with a tree of its own, owned by this one: opened,
/// raised and closed from here, and told apart from this window by its host.
///
/// Read more: [More than one window](https://goldberry.dev/docs/guide/windows.html#more-than-one-window).
public record SecondWindowCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-second";

    /// What the second window opens as.
    static final WindowSpec SPEC =
            WindowSpec.of("A second window", LogicalSize.of(420, 220)).withOwnership(Ownership.OWNED);

    @Override
    public State<?> createState() {
        return new SecondState();
    }

    /// What the second window holds: a line, and a button that closes the
    /// window it is in through its own host.
    record SecondPage() implements Widget.Stateless {

        @Override
        public Widget build(BuildContext context) {
            return new Column(
                    List.of(
                            new Text("A window of its own: its own tree, focus and overlays, sharing the stylesheets"
                                    + " and the models."),
                            new Button(
                                            "Close this window",
                                            () -> context.host()
                                                    .flatMap(HostWindow::of)
                                                    .ifPresent(window -> window.close()))
                                    .id("second-close")),
                    Attributes.NONE.id("second-page").classes("second-page"));
        }
    }

    static final class SecondState extends State<SecondWindowCard> {

        private @Nullable WindowHost second;

        /// The second window's close listener, dropped with this card so it never
        /// reaches a state that is gone.
        private @Nullable Subscription closing;

        private String answer = "One window.";

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            var open = second != null && second.isOpen();
            var actions = open
                    ? List.<Widget>of(
                            new Button("Raise it", this::raise).id("second-raise"),
                            new Button("Close it", this::close).id("second-close-from-here"))
                    : List.<Widget>of(new Button("Open a second window", () -> open(host))
                            .id("second-open")
                            .styled("primary"));
            return new ShowcaseCard(
                            ID,
                            "More than one window",
                            "host.openWindow opens another window with a tree of its own and returns its host. An"
                                    + " owned window stays above this one and closes with it; empty means this host"
                                    + " cannot open one.",
                            DocLink.to("guide/windows", "more-than-one-window"))
                    .of(
                            new Row(actions, Attributes.NONE.classes("toolbar")),
                            new Text(answer, Attributes.NONE.id("second-answer").classes("readout")));
        }

        private void open(Optional<Host> host) {
            var opened = host.flatMap(window -> window.openWindow(SPEC, new SecondPage()));
            stopListening();
            closing = opened.map(window -> window.onClose(() -> {
                        if (isMounted()) {
                            setState(() -> {
                                second = null;
                                answer = "The second window closed.";
                            });
                        }
                    }))
                    .orElse(null);
            setState(() -> {
                second = opened.orElse(null);
                answer = opened.isPresent()
                        ? "Open, owned by this window."
                        : "Empty: this host has no desktop to open another window on.";
            });
        }

        private void raise() {
            var window = second;
            if (window != null) {
                HostWindow.of(window).ifPresent(it -> it.raise());
            }
        }

        private void close() {
            var window = second;
            if (window != null) {
                window.close();
            }
        }

        private void stopListening() {
            var listening = closing;
            closing = null;
            if (listening != null) {
                listening.close();
            }
        }

        @Override
        protected void dispose() {
            stopListening();
            var window = second;
            if (window != null && window.isOpen()) {
                window.close();
            }
        }
    }
}
