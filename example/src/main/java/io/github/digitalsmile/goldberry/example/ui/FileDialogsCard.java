package io.github.digitalsmile.goldberry.example.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogSpec;
import io.github.digitalsmile.goldberry.render.dialog.FileFilter;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Basic** screen's third Java card: the desktop's own open, save and
/// folder dialogs — `docs/gaps.md` G9, [ADR-0287][adr].
///
/// [adr]: ../../../../../../../../book/src/adr/0287-a-file-dialog-is-the-desktops-and-the-answer-comes-back-later.md
///
/// ## Why it is a card and not three lines of `basic.kdl`
///
/// For the reason the road card is in Java, one step further on: a document can
/// name an action, but it cannot **hold what the action answered**. A dialog is
/// asynchronous — a person is inside the call — so the result arrives long after
/// the click, on a callback, and something has to be there to remember it. That
/// is a state, and `docs/core-widgets.md`'s markup has no way to declare one
/// (ADR-0110).
///
/// ## What it is demonstrating
///
/// Three things, and the third is the one that is easy to miss:
///
/// 1. **There is no blocking overload and there will not be one.** The UI thread
///    is the only thread allowed to touch windows, and on Linux blocking it also
///    stops the pump the XDG portal needs in order to answer. So the buttons
///    return at once and the line below them fills in later.
/// 2. **The answer is sealed over three cases**, and [#describe] switches over it
///    with no `default` — cancelling is not failing, and an export the user
///    changed their mind about leaves no error to show.
/// 3. **Nothing here knows which platform it is on.** `FileFilter.of("Images",
///    "png", "jpg")` is the toolkit's vocabulary; Windows's `*.png;*.jpg`, GTK's
///    glob and macOS's uniform type identifiers are the backend's.
///
/// ## The buttons are live even with no window under them
///
/// A gallery golden is painted through `Offscreen`, which has no host — so
/// [BuildContext#host()] is empty there. They are **not** disabled for it: the
/// picture has to be of the application that runs, and three greyed-out buttons
/// would be a photograph of something nobody sees (ADR-0284). A click with no
/// host cannot happen in an image, and says so if it ever does.
public record FileDialogsCard() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new DialogsState();
    }

    static final class DialogsState extends State<FileDialogsCard> {

        /// What the last dialog answered, or the standing invitation.
        ///
        /// A field on the state and not on `ShowcaseModel`: nothing else in the
        /// window reads it, and a property on the application's model would be a
        /// binding for one label in one card.
        private String answer = IDLE;

        /// The window, captured in `build` — which is what
        /// [BuildContext#host()] is for — and null when there is none.
        private @Nullable Host host;

        private static final String IDLE = "Nothing chosen yet. The line changes when a dialog answers.";

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var dialogs = host == null || host.fileDialogs().supported();
            return new Card(
                    List.of(
                            new Text("The desktop's own file dialogs", Attributes.NONE.classes("card-title")),
                            new Row(
                                            new Button("Open images…", this::openImages)
                                                    .disabled(!dialogs)
                                                    .id("dialog-open")
                                                    .styled("primary"),
                                            new Button("Save a board…", this::saveBoard)
                                                    .disabled(!dialogs)
                                                    .id("dialog-save"),
                                            new Button("Pick a folder…", this::pickFolder)
                                                    .disabled(!dialogs)
                                                    .id("dialog-folder"))
                                    .id("dialog-actions"),
                            new Text(answer).id("dialog-result"),
                            new Text(
                                    "Asynchronous, because a person is inside the call: the click"
                                            + " returns at once and the line above fills in when the"
                                            + " dialog closes. Cancelling is not failing, and the"
                                            + " filters are extensions rather than one platform's"
                                            + " pattern dialect.",
                                    Attributes.NONE.classes("caption"))),
                    Attributes.NONE.id("dialogs-card").classes("wall-card"));
        }

        /// Several existing files, filtered — the shape an import takes.
        private void openImages() {
            ask(FileDialogSpec.openFile()
                    .filters(FileFilter.of("Images", "png", "jpg", "jpeg"), FileFilter.everything("All files"))
                    .allowMany(true));
        }

        /// One path that need not exist yet, which is the whole difference
        /// between a save dialog and an open one — and why there are three kinds
        /// rather than one with flags.
        private void saveBoard() {
            ask(FileDialogSpec.saveFile()
                    .filters(FileFilter.of("PNG image", "png"))
                    .startingAt(Path.of(System.getProperty("user.home", "."), "board.png")));
        }

        /// A directory, which has nothing to filter — so `FileDialogSpec` refuses
        /// filters on one rather than letting each platform drop them quietly.
        private void pickFolder() {
            ask(FileDialogSpec.openFolder());
        }

        private void ask(FileDialogSpec spec) {
            var window = host;
            if (window == null) {
                setState(() -> answer = "There is no window under this screen, so there is nowhere to put a dialog.");
                return;
            }
            // `Host.fileDialog` and not `fileDialogs().show(...)`: it fills in
            // this window as the one to be modal for, and asks for the frame the
            // answer needs -- a choice arrives from the platform's own thread
            // with no input event behind it, so nothing else would (ADR-0287).
            window.fileDialog(spec, choice -> setState(() -> answer = describe(choice)));
        }

        /// The three cases, with no `default` — which is what `sealed` is for: a
        /// fourth would stop this compiling rather than being read as one of the
        /// three.
        private static String describe(FileChoice choice) {
            return switch (choice) {
                case FileChoice.Chosen(var paths, var filter) ->
                    paths.size()
                            + (paths.size() == 1 ? " path: " : " paths: ")
                            + paths.stream()
                                    .map(Path::getFileName)
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(", "))
                            + filter.map(f -> "  (" + f.label() + ")").orElse("");
                case FileChoice.Cancelled ignored -> "Cancelled — which is not a failure, and leaves no error to show.";
                case FileChoice.Failed(var message) -> "The platform could not: " + message;
            };
        }
    }
}
