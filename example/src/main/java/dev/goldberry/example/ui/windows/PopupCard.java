package dev.goldberry.example.ui.windows;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Placement;
import dev.goldberry.Popup;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// A popup: a platform window of its own, placed below its button and free of
/// this window's bounds. Where the video driver has none, the empty answer is
/// shown rather than treated as a failure.
///
/// Read more: [In a window of its own](https://goldberry.dev/docs/guide/windows.html#in-a-window-of-its-own).
public record PopupCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-popup";

    /// The button the popup is placed against.
    static final String ANCHOR = "popup-open";

    @Override
    public State<?> createState() {
        return new PopupState();
    }

    static final class PopupState extends State<PopupCard> {

        private @Nullable Popup open;

        private String answer = "Closed.";

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            return new ShowcaseCard(
                            ID,
                            "In a window of its own",
                            "host.popup opens a real window, measured, placed against its anchor with a flip if it"
                                    + " would not fit, and closed by a press elsewhere or Escape. Empty is a normal"
                                    + " answer where the platform has no popups.",
                            DocLink.to("guide/windows", "in-a-window-of-its-own"))
                    .of(
                            new Row(
                                    List.of(new Button(
                                                    "Open a popup below",
                                                    () -> host.ifPresentOrElse(
                                                            window -> {
                                                                var popup = window.popup(
                                                                        content(), ANCHOR, Placement.BELOW);
                                                                setState(() -> {
                                                                    open = popup.orElse(null);
                                                                    answer = popup.isPresent()
                                                                            ? "Open, in a window of its own."
                                                                            : "Empty: this video driver opens no popup"
                                                                                    + " windows, so a menu here falls back to"
                                                                                    + " an overlay.";
                                                                });
                                                            },
                                                            () -> setState(() -> answer = HostWindow.NONE)))
                                            .id(ANCHOR)),
                                    Attributes.NONE.classes("toolbar")),
                            new Text(answer, Attributes.NONE.id("popup-answer").classes("readout")));
        }

        /// What the popup holds.
        private Widget content() {
            return new Column(
                    List.of(
                            new Text("A popup can hang past the window's edge."),
                            new Button("Close", this::close).id("popup-close")),
                    Attributes.NONE.id("popup-content").classes("popup-content"));
        }

        private void close() {
            var popup = open;
            if (popup != null) {
                popup.close();
            }
            setState(() -> {
                open = null;
                answer = "Closed.";
            });
        }

        @Override
        protected void dispose() {
            var popup = open;
            if (popup != null && popup.isOpen()) {
                popup.close();
            }
        }
    }
}
