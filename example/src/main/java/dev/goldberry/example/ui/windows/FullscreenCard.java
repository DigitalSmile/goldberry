package dev.goldberry.example.ui.windows;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.bind.Subscription;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.text.Text;

/// Fullscreen, asked for and followed: the button is a request, and the line
/// under it is what the platform last reported.
///
/// Read more: [Fullscreen](https://goldberry.dev/docs/guide/windows.html#fullscreen).
public record FullscreenCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-fullscreen";

    @Override
    public State<?> createState() {
        return new FullscreenState();
    }

    static final class FullscreenState extends State<FullscreenCard> {

        private @Nullable Subscription following;

        private boolean asked;

        @Override
        public Widget build(BuildContext context) {
            var host = context.host().orElse(null);
            if (!asked && host != null) {
                asked = true;
                following = host.onFullscreenChanged(_ -> setState(() -> {}));
            }
            var can = host != null && host.canFullscreen();
            var now = host != null && host.isFullscreen();
            var readout = !can
                    ? "canFullscreen() is false: there is no window under this host."
                    : now ? "Fullscreen, as the platform last reported." : "A window, as the platform last reported.";
            return new ShowcaseCard(
                            ID,
                            "Fullscreen",
                            "Fullscreen is a state the platform owns: setFullscreen asks, onFullscreenChanged"
                                    + " answers, and the user can change it without the application. The"
                                    + " desktop's own control is reported here too.",
                            DocLink.to("guide/windows", "fullscreen"))
                    .of(
                            new Button(now ? "Leave fullscreen" : "Go fullscreen", () -> toggle(host))
                                    .disabled(!can)
                                    .id("fullscreen-toggle"),
                            new Text(
                                    readout,
                                    Attributes.NONE.id("fullscreen-answer").classes("readout")));
        }

        private static void toggle(@Nullable Host host) {
            if (host != null) {
                host.setFullscreen(!host.isFullscreen());
            }
        }

        @Override
        protected void dispose() {
            var subscription = following;
            if (subscription != null) {
                subscription.close();
                following = null;
            }
        }
    }
}
