package dev.goldberry.example.ui.windows;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Overlay;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Corner;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// A note floated over the window's corner with `host.overlay`, and taken away
/// again with the handle it returned.
///
/// Read more: [In the window](https://goldberry.dev/docs/guide/windows.html#in-the-window).
public record OverlayCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-overlay";

    @Override
    public State<?> createState() {
        return new OverlayState();
    }

    static final class OverlayState extends State<OverlayCard> {

        /// The note on the window's layer, or null when there is none.
        private @Nullable Overlay note;

        private String answer = "No overlay.";

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            var open = note != null && note.isAttached();
            var toggle = open
                    ? new Button("Take it away", this::remove).id("overlay-remove")
                    : new Button(
                                    "Float a note",
                                    () -> host.ifPresentOrElse(
                                            window -> {
                                                var shown = window.overlay(note(), Corner.BOTTOM_START);
                                                setState(() -> {
                                                    note = shown;
                                                    answer = "A note in the bottom-start corner, over everything.";
                                                });
                                            },
                                            () -> setState(() -> answer = HostWindow.NONE)))
                            .id("overlay-show")
                            .styled("primary");
            return new ShowcaseCard(
                            ID,
                            "In the window",
                            "host.overlay floats a widget over the content in a corner, taking no space and costing"
                                    + " the tree nothing. The handle it returns removes it, at once or the way the"
                                    + " widget leaves.",
                            DocLink.to("guide/windows", "in-the-window"))
                    .of(
                            new Row(List.of(toggle), Attributes.NONE.classes("toolbar")),
                            new Text(
                                    answer, Attributes.NONE.id("overlay-answer").classes("readout")));
        }

        /// What floats: a small panel with its own way out.
        private Widget note() {
            return new Panel(
                    List.of(
                            new Text("An overlay, in the window's own layer."),
                            new Button("Dismiss", this::remove).id("overlay-dismiss")),
                    Attributes.NONE.id("overlay-note").classes("overlay-note"));
        }

        private void remove() {
            var shown = note;
            if (shown != null) {
                shown.dismiss();
            }
            setState(() -> {
                note = null;
                answer = "Removed.";
            });
        }

        @Override
        protected void dispose() {
            var shown = note;
            if (shown != null) {
                shown.remove();
            }
        }
    }
}
