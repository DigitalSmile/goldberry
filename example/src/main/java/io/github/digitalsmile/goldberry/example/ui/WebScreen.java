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
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialog;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.DialogAction;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialogs;
import io.github.digitalsmile.goldberry.widgets.shell.web.WebPage;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Web view** screen: §9's `web-view`, filling the tab.
///
/// The page is a real child window sitting over the widget's box, moved and
/// resized with it — so it *is* the tab, which is what [ADR-0442][adr442] made
/// possible.
///
/// ## The button is the demonstration, not a convenience
///
/// "Open a dialog over the page" shows the one thing an embedded page cannot
/// share the screen with, and shows what the toolkit does about it.
///
/// A `dialog` is an ordinary widget in the overlay layer, painted into the
/// frame; the page is a platform window *above* that frame. Nothing painted can
/// cover it. So a modal over a page used to be drawn where nobody could see it
/// — its scrim dimmed the margin around the page and nothing else, and its
/// buttons could not be pressed because the press landed on WebKit.
///
/// Since [ADR-0444][adr444] the page gets out of the way instead: while a modal
/// is in force, `WebView` parks its child window off the side of the
/// application's window, and puts it back when the dialog closes. Press the
/// button and the page **disappears**; answer the dialog and it comes back,
/// scrolled to wherever it was.
///
/// That disappearance is the point rather than a wart. A modal is a demand for
/// the whole of the user's attention, and the alternative is a modal nobody can
/// see. It applies to modals only: a `tooltip` or a `toast` over a page is still
/// invisible where they overlap, because blanking a page to show four words in a
/// corner would be worse than the problem.
///
/// ## On Wayland it shows a message instead
///
/// Embedding means reparenting the engine's window into this one, and Wayland
/// permits no such thing. The widget opens nothing and says why, rather than
/// dropping a loose browser window on the desktop — and the dialog is then
/// perfectly visible with no parking needed, which is its own half of the
/// demonstration.
///
/// [adr442]: ../../../../../../../../book/src/adr/0442-a-page-is-a-child-window-where-the-window-system-allows-one.md
/// [adr444]: ../../../../../../../../book/src/adr/0444-a-page-stands-aside-for-a-modal.md
public record WebScreen() implements Widget.Stateful {

    /// What the tab shows when it opens.
    static final String GOLDBERRY_URL = "https://github.com/digitalsmile/goldberry";

    /// The built-in document behind "Load the callback demo".
    ///
    /// Written here rather than fetched, so the demonstration works with no
    /// network at all — and so that what the page does is readable beside the
    /// handlers it calls. It exercises both directions: a resolved promise and a
    /// rejected one.
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
                // On load, so the mechanism has demonstrated itself before
                // anything is pressed -- and so that a run with no pointer can
                // still show it working.
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
        return new WebScreenState();
    }

    static final class WebScreenState extends State<WebScreen> {

        /// The open dialog, or null. Held so the button can take it away again and
        /// so a second press does not stack two of them.
        private @Nullable Overlay dialog;

        private @Nullable Host host;

        /// What is in the address field. Only what the user has **typed** — the
        /// page follows [#showing], which is what pressing Go copies this into.
        private String typed = GOLDBERRY_URL;

        /// The page the `web-view` is asked to show.
        ///
        /// This is the whole of the navigation mechanism: the screen holds the
        /// page it wants, `build` hands it to the widget, and the widget follows
        /// it ([ADR-0449]). There is nothing to call and nothing to hold on to.
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
                    Attributes.NONE.id("web-screen").classes("screen"));
        }

        /// The address field and the button that commits it.
        ///
        /// Typing changes [#typed] and nothing else — a `web-view` that reloaded
        /// on every keystroke would fetch `h`, `ht`, `htt`. Pressing Go is what
        /// makes the typed text the page.
        private Widget addressBar() {
            return new Row(
                    List.of(
                            new TextInput(typed, value -> setState(() -> typed = value))
                                    .placeholder("https://…")
                                    .withAttributes(Attributes.NONE.id("web-url")),
                            new Button("Go", this::go).withAttributes(Attributes.NONE.id("web-go"))),
                    Attributes.NONE.id("web-address"));
        }

        /// Shows whatever is in the field.
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
        /// The handlers are declared **here**, on the page value, rather than
        /// registered against something after it opens: a page is described by a
        /// value and its bindings are part of what it is ([ADR-0448]). They are
        /// read when the page opens, which is why they are attached to every
        /// page this screen makes rather than only to the demo one.
        private WebPage page(@Nullable String url) {
            var base = url == null ? WebPage.ofHtml(DEMO_DOCUMENT) : WebPage.of(url);
            return base.on("goldberrySays", arguments -> {
                        // `arguments` is a JSON array, unparsed -- the toolkit
                        // ships no reader (ADR-0448). One string argument is the
                        // whole of what this demo sends, so the quotes are taken
                        // off and nothing pretends to be a parser.
                        var said = unquote(arguments);
                        setState(() -> fromThePage = said);
                        // Valid JSON, because it resolves the page's promise and
                        // the page awaits it.
                        return "\"Java heard you at "
                                + java.time.LocalTime.now(java.time.ZoneId.systemDefault())
                                        .withNano(0) + "\"";
                    })
                    .on("goldberryFails", arguments -> {
                        // The other half of the contract: throwing REJECTS the
                        // page's promise, and the message is what its `catch`
                        // receives. A page awaiting a value that never arrives
                        // would simply hang.
                        throw new IllegalStateException("this handler always refuses, on purpose");
                    });
        }

        /// `["hello"]` to `hello`, and anything else to itself.
        ///
        /// Deliberately not a JSON parser. It handles the one shape this demo
        /// sends and says so, which is what an application without a reader on
        /// its classpath would honestly do.
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
