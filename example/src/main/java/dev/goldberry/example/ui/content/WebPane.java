package dev.goldberry.example.ui.content;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.Overlay;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.web.WebView;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.overlay.dialog.Dialog;
import dev.goldberry.widgets.overlay.dialog.DialogAction;
import dev.goldberry.widgets.overlay.dialog.Dialogs;
import dev.goldberry.widgets.shell.web.WebPage;
import dev.goldberry.widgets.text.Text;

/// The Web view screen under its header: an address bar, a row of actions, and the
/// `web-view` taking the rest.
///
/// ## Navigating is rebuilding with another page
///
/// The state holds the [WebPage] it wants shown and `build` hands it to the
/// widget, which follows it. Typing changes the address field and nothing else;
/// pressing Go makes the typed text the page, so the view does not fetch `h`,
/// `ht`, `htt`.
///
/// ## The dialog button is a demonstration
///
/// A page is a platform window above the frame, so nothing painted can cover it.
/// While a modal is up the widget parks its page off the side of the window and
/// puts it back when the dialog closes: press the button and the page goes;
/// answer the dialog and it comes back where it was. On Wayland, where no page can
/// be embedded, the widget paints a message instead and the dialog needs no help.
///
/// Read more:
/// [Modals](https://goldberry.dev/docs/components/content.html#what-a-page-cannot-share-the-screen-with).
public record WebPane() implements Widget.Stateful {

    /// What the view shows when the screen opens.
    static final String GOLDBERRY_URL = "https://github.com/digitalsmile/goldberry";

    /// The built-in document behind "Load the callback demo".
    ///
    /// Written here rather than fetched, so the demonstration works with no
    /// network, and so what the page does is readable beside the handlers it calls.
    /// It exercises both directions: a resolved promise and a rejected one.
    static final String DEMO_DOCUMENT = """
            <!doctype html>
            <html><head><meta charset="utf-8"><style>
              body { font: 15px system-ui, sans-serif; margin: 0; padding: 32px;
                     background: #2e3440; color: #eceff4 }
              h1 { font-size: 18px; margin: 0 0 4px }
              p  { color: #d8dee9; max-width: 46em }
              button { font: inherit; padding: 8px 14px; margin: 4px 8px 4px 0;
                       border: 0; border-radius: 8px; background: #5e81ac; color: #eceff4 }
              #out { margin-top: 16px; padding: 12px; border-radius: 8px;
                     background: #3b4252; white-space: pre-wrap; min-height: 1.4em }
            </style></head><body>
              <h1>This document is talking to Java</h1>
              <p>Each button calls a function the application bound on the page value.
                 The call is a promise: Java's return value resolves it, and a Java
                 exception rejects it.</p>
              <button onclick="say()">Send a message to Java</button>
              <button onclick="fail()">Ask for one that fails</button>
              <div id="out">nothing yet</div>
              <script>
                const out = document.getElementById('out');
                // On load, so the mechanism has shown itself before anything is
                // pressed.
                window.addEventListener('DOMContentLoaded', () => say());
                async function say() {
                  try {
                    out.textContent = await window.goldberrySays('hello from the page');
                  } catch (e) { out.textContent = 'rejected: ' + e; }
                }
                async function fail() {
                  try {
                    out.textContent = await window.goldberryFails();
                  } catch (e) { out.textContent = 'rejected: ' + e; }
                }
              </script>
            </body></html>
            """;

    @Override
    public State<?> createState() {
        return new WebPaneState();
    }

    /// The page shown, the address typed, the last word from the page, and the
    /// dialog while it is open.
    static final class WebPaneState extends State<WebPane> {

        /// The open dialog, or null. Held so the button can take it away again and
        /// so a second press does not stack two of them.
        private @Nullable Overlay dialog;

        private @Nullable Host host;

        /// What is in the address field: only what the user has typed.
        private String typed = GOLDBERRY_URL;

        /// The page the `web-view` is asked to show.
        private WebPage showing = page(GOLDBERRY_URL);

        /// The last thing the page's own script said, or null.
        private @Nullable String fromThePage;

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            return new Column(
                    List.of(
                            addressBar(),
                            new Row(
                                    List.of(
                                            new Button("Open a dialog over the page", this::openDialog)
                                                    .withAttributes(Attributes.NONE.id("web-dialog")),
                                            new Button("Load the callback demo", this::loadDemo)
                                                    .withAttributes(Attributes.NONE.id("web-demo")),
                                            new Text(
                                                    fromThePage == null
                                                            ? "the page has not called back yet"
                                                            : "the page said: " + fromThePage,
                                                    Attributes.NONE
                                                            .id("web-callback")
                                                            .classes("caption"))),
                                    Attributes.NONE.id("web-actions")),
                            new WebView(showing).withAttributes(Attributes.NONE.id("web-page"))),
                    Attributes.NONE.id("web-body"));
        }

        /// The address field and the button that commits it.
        private Widget addressBar() {
            return new Row(
                    List.of(
                            new TextInput(typed, value -> setState(() -> typed = value))
                                    .placeholder("https://…")
                                    .withAttributes(Attributes.NONE.id("web-url")),
                            new Button("Go", this::go).withAttributes(Attributes.NONE.id("web-go"))),
                    Attributes.NONE.id("web-address"));
        }

        /// Shows whatever is in the field, with `https://` in front when it names
        /// no scheme.
        private void go() {
            var url = typed.trim();
            if (url.isEmpty() || url.equals(showing.url())) {
                return;
            }
            setState(() -> showing = page(url.contains("://") ? url : "https://" + url));
        }

        /// Shows the built-in document that calls back.
        private void loadDemo() {
            setState(() -> {
                showing = page(null);
                typed = "(the built-in demo document)";
            });
        }

        /// A page with this screen's callbacks on it, or the demo document when
        /// `url` is null.
        ///
        /// The handlers are declared on the page value rather than registered after
        /// it opens: a page is described by a value and its bindings are part of
        /// what it is. They go on every page this screen makes, so any page that
        /// knows the names can call them.
        private WebPage page(@Nullable String url) {
            var base = url == null ? WebPage.ofHtml(DEMO_DOCUMENT) : WebPage.of(url);
            return base.on("goldberrySays", arguments -> {
                        // `arguments` is a JSON array, unparsed. One string is all
                        // this demo sends, so the quotes are taken off and nothing
                        // pretends to be a parser.
                        var said = unquote(arguments);
                        setState(() -> fromThePage = said);
                        // Valid JSON, because it resolves the page's promise.
                        return "\"Java heard you at "
                                + LocalTime.now(ZoneId.systemDefault()).withNano(0) + "\"";
                    })
                    .on("goldberryFails", arguments -> {
                        // Throwing rejects the page's promise, and the message is
                        // what its `catch` receives.
                        throw new IllegalStateException("this handler always refuses, on purpose");
                    });
        }

        /// `["hello"]` to `hello`, and anything else to itself.
        private static String unquote(String arguments) {
            var text = arguments.strip();
            if (text.startsWith("[\"") && text.endsWith("\"]")) {
                return text.substring(2, text.length() - 2);
            }
            return text;
        }

        /// Opens a dialog that the page gets out of the way of.
        private void openDialog() {
            if (host == null || (dialog != null && dialog.isAttached())) {
                return;
            }
            dialog = Dialogs.show(
                    host,
                    new Dialog(
                                    "The page stood aside",
                                    List.of(
                                            new Text("A web-view is a platform window above the frame rather than a"
                                                    + " layer in it, so nothing Goldberry paints can cover it. This"
                                                    + " dialog would have been drawn behind the page."),
                                            new Text("So the page moved instead: while a modal is up the widget parks"
                                                    + " its window off the side of this one. Close this and the page"
                                                    + " comes back where it was, at the scroll position it had."),
                                            new Text("A tooltip or a toast over a page is still hidden by it. Only a"
                                                    + " modal is worth blanking a page for."),
                                            new DialogAction(
                                                    "Bring it back", DialogAction.Role.DISMISSIVE, this::closeDialog)),
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
