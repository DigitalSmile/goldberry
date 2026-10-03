package dev.goldberry.example.ui.windows;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.render.window.Attention;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// Asks the desktop to draw the eye to this window, after a delay long enough
/// to switch to another one, because a window in front needs no attention.
///
/// Read more: [Asking for attention](https://goldberry.dev/docs/guide/windows.html#asking-for-attention).
public record AttentionCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-attention";

    /// How long the card waits before it asks.
    static final Duration DELAY = Duration.ofSeconds(3);

    @Override
    public State<?> createState() {
        return new AttentionState();
    }

    static final class AttentionState extends State<AttentionCard> {

        private String answer = "Press, then switch to another window within three seconds.";

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            return new ShowcaseCard(
                            ID,
                            "Asking for attention",
                            "requestAttention asks the desktop to draw the eye to a window that is not in front: the"
                                    + " dock icon bounces, the taskbar button flashes, or X11 sets the urgency hint."
                                    + " It says which window, not why.",
                            DocLink.to("guide/windows", "asking-for-attention"))
                    .of(
                            new Row(
                                    List.of(
                                            new Button("Ask in three seconds", () -> ask(host)).id("attention-ask"),
                                            new Button("Withdraw", () -> withdraw(host)).id("attention-cancel")),
                                    Attributes.NONE.classes("toolbar")),
                            new Text(
                                    answer,
                                    Attributes.NONE.id("attention-answer").classes("readout")));
        }

        private void ask(Optional<Host> host) {
            var window = host.flatMap(HostWindow::of);
            if (host.isEmpty() || window.isEmpty()) {
                setState(() -> answer = HostWindow.NONE);
                return;
            }
            setState(() -> answer = "Asking in three seconds…");
            host.orElseThrow().after(DELAY, () -> {
                var asked = window.orElseThrow().requestAttention(Attention.UNTIL_FOCUSED);
                if (isMounted()) {
                    setState(() -> answer =
                            asked ? "Asked, until the window is focused again." : "The desktop has no way to ask.");
                }
            });
        }

        private void withdraw(Optional<Host> host) {
            var withdrawn = host.flatMap(HostWindow::of)
                    .map(window -> window.cancelAttention())
                    .orElse(false);
            setState(() -> answer = withdrawn ? "Withdrawn." : "Nothing to withdraw.");
        }
    }
}
