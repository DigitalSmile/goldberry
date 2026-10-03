package dev.goldberry.example.ui.windows;

import java.nio.file.Path;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.render.dialog.FileChoice;
import dev.goldberry.render.dialog.FileDialogSpec;
import dev.goldberry.render.dialog.FileFilter;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// The desktop's own open, save and folder dialogs, asked through the host.
///
/// A dialog is asynchronous, because a person is inside the call: the click
/// returns at once and the answer arrives later on a callback, which is why the
/// card has a state to keep it in. The answer is sealed over three cases, and
/// cancelling is one of them rather than a failure.
///
/// The buttons stay live with no window under them, so a picture of the card
/// is of the application that runs; a press with no host says so.
///
/// Read more: [The host](https://goldberry.dev/docs/guide/windows.html#the-host).
public record FileDialogsCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "dialogs-card";

    @Override
    public State<?> createState() {
        return new DialogsState();
    }

    static final class DialogsState extends State<FileDialogsCard> {

        /// What the last dialog answered, or the standing invitation.
        private String answer = IDLE;

        /// The window, captured in `build`, and null when there is none.
        private @Nullable Host host;

        private static final String IDLE = "Nothing chosen yet. The line changes when a dialog answers.";

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var dialogs = host == null || host.fileDialogs().supported();
            return new ShowcaseCard(
                            ID,
                            "The desktop's file dialogs",
                            "host.fileDialog opens the platform's own open, save and folder dialogs. The click"
                                    + " returns at once and the line below fills in when the dialog closes;"
                                    + " cancelling is not failing.",
                            DocLink.to("guide/windows", "the-host"))
                    .of(
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
                                    .id("dialog-actions")
                                    .styled("toolbar"),
                            new Text(answer, Attributes.NONE.id("dialog-result").classes("readout")));
        }

        /// Several existing files, filtered: the shape an import takes.
        private void openImages() {
            ask(FileDialogSpec.openFile()
                    .filters(FileFilter.of("Images", "png", "jpg", "jpeg"), FileFilter.everything("All files"))
                    .allowMany(true));
        }

        /// One path that need not exist yet, which is the whole difference
        /// between a save dialog and an open one.
        private void saveBoard() {
            ask(FileDialogSpec.saveFile()
                    .filters(FileFilter.of("PNG image", "png"))
                    .startingAt(Path.of(System.getProperty("user.home", "."), "board.png")));
        }

        /// A directory, which has nothing to filter.
        private void pickFolder() {
            ask(FileDialogSpec.openFolder());
        }

        private void ask(FileDialogSpec spec) {
            var window = host;
            if (window == null) {
                setState(() -> answer = "There is no window under this screen, so there is nowhere to put a dialog.");
                return;
            }
            // `Host.fileDialog` and not `fileDialogs().show(...)`: it makes this
            // window the one to be modal for, and asks for the frame the answer
            // needs.
            window.fileDialog(spec, choice -> setState(() -> answer = describe(choice)));
        }

        /// The three cases, with no `default`: a fourth would stop this compiling.
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
                case FileChoice.Cancelled _ -> "Cancelled, which is not a failure and leaves no error to show.";
                case FileChoice.Failed(var message) -> "The platform could not: " + message;
            };
        }
    }
}
