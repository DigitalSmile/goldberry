package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.web.WebView;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialog;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.DialogAction;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialogs;
import io.github.digitalsmile.goldberry.widgets.shell.web.WebPage;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Web view** screen: §9's `web-view`, filling the tab.
///
/// The page is a real child window sitting over the widget's box, moved and
/// resized with it — so it *is* the tab, which is what [ADR-0442][adr] made
/// possible.
///
/// ## The button is the demonstration, not a convenience
///
/// "Open a dialog over the page" is here to show the **one thing an embedded
/// page cannot do**, and to show it rather than assert it. A `dialog` is an
/// ordinary widget in the overlay layer, painted into the frame; the page is a
/// platform window *above* that frame. So the dialog is drawn, and it is
/// invisible wherever the page covers it — its scrim dims the margin around the
/// page and nothing else, and its buttons cannot be pressed because the press
/// lands on WebKit.
///
/// Press it and then `Escape`: the dialog was there the whole time, holding the
/// keyboard, exactly where it could not be seen. That is the sharp edge
/// `WebView`'s own documentation names, and a screen that merely *said* it would
/// be a screen nobody believed.
///
/// An application that needs a modal over a page has to take the page away first.
/// There is no compositing answer, because there is no raster.
///
/// ## On Wayland it shows a message instead
///
/// Embedding means reparenting the engine's window into this one, and Wayland
/// permits no such thing. The widget opens nothing and says why, rather than
/// dropping a loose browser window on the desktop — and the dialog is then
/// perfectly visible, which is its own half of the demonstration.
///
/// [adr]: ../../../../../../../../book/src/adr/0442-a-page-is-a-child-window-where-the-window-system-allows-one.md
public record WebScreen() implements Widget.Stateful {

    /// What the tab shows.
    static final String GOLDBERRY_URL = "https://github.com/digitalsmile/goldberry";

    @Override
    public State<?> createState() {
        return new WebScreenState();
    }

    static final class WebScreenState extends State<WebScreen> {

        /// The open dialog, or null. Held so the button can take it away again and
        /// so a second press does not stack two of them.
        private @Nullable Overlay dialog;

        private @Nullable Host host;

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            return new Column(
                            new Row(
                                            new Text(
                                                    "The Goldberry page, inside this tab",
                                                    Attributes.NONE.classes("caption")),
                                            new Button("Open a dialog over the page", this::openDialog)
                                                    .withAttributes(Attributes.NONE.id("web-dialog")))
                                    .withAttributes(Attributes.NONE.id("web-actions")),
                            new WebView(WebPage.of(GOLDBERRY_URL)).withAttributes(Attributes.NONE.id("web-page")))
                    .withAttributes(Attributes.NONE.id("web-screen").classes("screen"));
        }

        /// Opens a dialog the page will hide.
        private void openDialog() {
            if (host == null || (dialog != null && dialog.isAttached())) {
                return;
            }
            dialog = Dialogs.show(
                    host,
                    new Dialog(
                                    "Can you see this?",
                                    List.of(
                                            new Text("If the page opened, most of this dialog is behind it. A"
                                                    + " web-view is a platform window above the frame rather than a"
                                                    + " layer in it, so nothing Goldberry paints can cover it — a"
                                                    + " dialog, a popover, a tooltip and a toast all lose."),
                                            new Text("It really is here: it holds the keyboard, and Escape closes"
                                                    + " it. Only its scrim, around the edges of the page, shows."),
                                            new DialogAction("Close", DialogAction.Role.DISMISSIVE, this::closeDialog)),
                                    Attributes.NONE)
                            .id("web-dialog-panel"));
        }

        private void closeDialog() {
            if (dialog != null) {
                dialog.remove();
                dialog = null;
            }
        }

        /// Takes the dialog with the screen, so switching tabs does not leave a
        /// modal attached to a window nobody is looking at.
        @Override
        protected void dispose() {
            closeDialog();
        }
    }
}
