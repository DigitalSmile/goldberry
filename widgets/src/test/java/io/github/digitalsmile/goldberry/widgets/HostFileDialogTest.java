package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogKind;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogSpec;
import io.github.digitalsmile.goldberry.render.dialog.FileFilter;

/// [io.github.digitalsmile.goldberry.Host#fileDialog], which is what a toolbar's
/// "Export…" actually calls.
///
/// The repaint is the whole reason this method exists rather than
/// `host.fileDialogs().show(...)`. A dialog's answer arrives from the platform's
/// own thread with no input event behind it, so nothing asks for a frame — the
/// same failure a tray row has (ADR-0191), and the one a widget test can catch
/// where a backend test cannot.
class HostFileDialogTest {

    @Test
    @DisplayName("asks for a frame after the answer, because nothing else will")
    void repaintsAfterTheAnswer() {
        var host = new TestHost();
        host.fileDialogs().answerWith(FileChoice.of(Path.of("/tmp/board.png")));
        var before = host.repaints();

        host.fileDialog(FileDialogSpec.saveFile(), choice -> {});

        assertEquals(before + 1, host.repaints(), "a choice that changed the model would be drawn on no frame");
    }

    @Test
    @DisplayName("asks for a frame after a cancel too, because a handler may have shown a spinner")
    void repaintsAfterACancel() {
        var host = new TestHost();
        var before = host.repaints();

        host.fileDialog(FileDialogSpec.openFile(), choice -> {});

        assertEquals(before + 1, host.repaints());
    }

    @Test
    @DisplayName("asks for a frame even when the handler throws")
    void repaintsWhenTheHandlerThrows() {
        var host = new TestHost();
        host.fileDialogs().answerWith(FileChoice.of(Path.of("/tmp/board.png")));
        var before = host.repaints();

        assertThrows(
                IllegalStateException.class,
                () -> host.fileDialog(FileDialogSpec.saveFile(), choice -> {
                    throw new IllegalStateException("the export failed");
                }));

        assertEquals(before + 1, host.repaints(), "the frame is owed whether or not the handler finished");
    }

    @Test
    @DisplayName("hands the handler what the user picked, once")
    void deliversTheChoiceOnce() {
        var host = new TestHost();
        host.fileDialogs().answerWith(FileChoice.of(Path.of("/tmp/board.png")));
        var answers = new ArrayList<FileChoice>();

        host.fileDialog(FileDialogSpec.saveFile().filters(FileFilter.of("PNG image", "png")), answers::add);

        assertEquals(1, answers.size());
        assertEquals(
                Path.of("/tmp/board.png"),
                assertInstanceOf(FileChoice.Chosen.class, answers.getFirst()).path());
    }

    @Test
    @DisplayName("passes the request through unchanged, so the platform sees what the widget asked for")
    void passesTheRequestThrough() {
        var host = new TestHost();

        host.fileDialog(
                FileDialogSpec.openFile()
                        .filters(FileFilter.of("Markdown", ".md"))
                        .startingAt(Path.of("/notes"))
                        .allowMany(true),
                choice -> {});

        var shown = host.fileDialogs().shown().getFirst();
        assertEquals(FileDialogKind.OPEN_FILE, shown.kind());
        assertEquals(FileFilter.of("Markdown", "md"), shown.filters().getFirst());
        assertEquals(Path.of("/notes"), shown.startingPoint().orElseThrow());
        assertTrue(shown.allowMany());
    }

    @Test
    @DisplayName("a host with no dialogs fails rather than cancels, so the button can say why")
    void failsWhenUnsupported() {
        var host = new TestHost();
        host.fileDialogs().supported(false);
        var answers = new ArrayList<FileChoice>();

        host.fileDialog(FileDialogSpec.openFile(), answers::add);

        assertInstanceOf(FileChoice.Failed.class, answers.getFirst());
    }

    @Test
    @DisplayName("the dialogs are the same ones every call, so a scripted answer is not lost")
    void oneInstance() {
        var host = new TestHost();

        assertSame(host.fileDialogs(), host.fileDialogs());
    }
}
