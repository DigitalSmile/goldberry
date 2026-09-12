package io.github.digitalsmile.goldberry.render.backend.headless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogKind;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogSpec;
import io.github.digitalsmile.goldberry.render.dialog.FileFilter;

/// The test double a test of an "Export…" button drives.
class HeadlessFileDialogsTest {

    @Test
    @DisplayName("cancels when nothing has been scripted, because an unfilled dialog is a cancelled one")
    void cancelsByDefault() {
        var dialogs = new HeadlessFileDialogs();
        var answers = new ArrayList<FileChoice>();

        dialogs.show(null, FileDialogSpec.openFile(), answers::add);

        assertEquals(1, answers.size());
        assertSame(FileChoice.cancelled(), answers.getFirst());
    }

    @Test
    @DisplayName("hands out scripted answers in order, one per dialog")
    void scriptsInOrder() {
        var dialogs = new HeadlessFileDialogs()
                .answerWith(FileChoice.of(Path.of("/a.png")))
                .answerWith(FileChoice.failed("the portal went away"));
        var answers = new ArrayList<FileChoice>();

        dialogs.show(null, FileDialogSpec.openFile(), answers::add);
        dialogs.show(null, FileDialogSpec.openFile(), answers::add);
        dialogs.show(null, FileDialogSpec.openFile(), answers::add);

        assertEquals(
                Path.of("/a.png"),
                assertInstanceOf(FileChoice.Chosen.class, answers.get(0)).path());
        assertInstanceOf(FileChoice.Failed.class, answers.get(1));
        assertSame(FileChoice.cancelled(), answers.get(2), "the script ran out, so the user closed the dialog");
    }

    @Test
    @DisplayName("records what was asked for, including the requests that were cancelled")
    void recordsWhatWasAskedFor() {
        var dialogs = new HeadlessFileDialogs();

        dialogs.show(null, FileDialogSpec.saveFile().filters(FileFilter.of("PNG image", "png")), choice -> {});
        dialogs.show(null, FileDialogSpec.openFolder(), choice -> {});

        assertEquals(List.of(FileDialogKind.SAVE_FILE, FileDialogKind.OPEN_FOLDER), kinds(dialogs));
        assertEquals(
                List.of(FileFilter.of("PNG image", "png")),
                dialogs.shown().getFirst().filters());
    }

    @Test
    @DisplayName("reports itself unsupported on request, and then fails rather than cancels")
    void canPretendToHaveNone() {
        var dialogs = new HeadlessFileDialogs().supported(false).answerWith(FileChoice.of(Path.of("/a.png")));
        var answers = new ArrayList<FileChoice>();

        dialogs.show(null, FileDialogSpec.openFile(), answers::add);

        assertInstanceOf(
                FileChoice.Failed.class,
                answers.getFirst(),
                "a backend with no dialogs must not deliver a script it could not have run");
    }

    @Test
    @DisplayName("reset() forgets both halves")
    void resets() {
        var dialogs = new HeadlessFileDialogs().answerWith(FileChoice.of(Path.of("/a.png")));
        dialogs.show(null, FileDialogSpec.openFile(), choice -> {});

        dialogs.reset();

        assertTrue(dialogs.shown().isEmpty());
        var answers = new ArrayList<FileChoice>();
        dialogs.show(null, FileDialogSpec.openFile(), answers::add);
        assertSame(FileChoice.cancelled(), answers.getFirst());
    }

    private static List<FileDialogKind> kinds(HeadlessFileDialogs dialogs) {
        return dialogs.shown().stream().map(FileDialogSpec::kind).toList();
    }
}
