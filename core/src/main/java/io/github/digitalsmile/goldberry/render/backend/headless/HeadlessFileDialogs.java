package io.github.digitalsmile.goldberry.render.backend.headless;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogSpec;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogs;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;

/// File dialogs with nobody to show them to.
///
/// A test double rather than a refusal, and for the same reason
/// [HeadlessPopup] exists: the rules a real backend has to keep — answered
/// exactly once, on the UI thread, with one of three outcomes — need somewhere to
/// be checked without a display. A test scripts what the user "does" and asserts
/// what the application did about it.
///
/// ```java
/// dialogs.answerWith(FileChoice.of(Path.of("/tmp/board.png")));
/// export.click();
/// assertEquals(Path.of("/tmp/board.png"), exported);
/// ```
///
/// **Cancel is the default**, because it is the outcome an unprepared test most
/// wants: an application that has not scripted an answer is one whose dialog
/// nobody filled in.
///
/// **The answer is immediate**, on the thread that called [#show]. A real
/// backend's arrives later and on another thread, which is why
/// [io.github.digitalsmile.goldberry.Host#fileDialog] and not this class is where
/// the hop and the repaint are tested.
public final class HeadlessFileDialogs implements FileDialogs {

    private final Deque<FileChoice> scripted = new ArrayDeque<>();
    private final List<FileDialogSpec> shown = new ArrayList<>();

    private boolean supported = true;

    /// Made by [HeadlessBackend], and by a test that wants one on its own.
    public HeadlessFileDialogs() {}

    @Override
    public boolean supported() {
        return supported;
    }

    /// Makes this backend claim it has no file dialogs, for the test that a menu
    /// hides its "Export…" item.
    ///
    /// [#show] still answers — with a [FileChoice.Failed], exactly as
    /// [FileDialogs#none()] does.
    public HeadlessFileDialogs supported(boolean value) {
        this.supported = value;
        return this;
    }

    /// Queues what the next dialog answers with. Several may be queued, and they
    /// are handed out in order.
    public HeadlessFileDialogs answerWith(FileChoice choice) {
        scripted.add(Objects.requireNonNull(choice, "choice"));
        return this;
    }

    /// What has been asked for so far, in order — including the requests that
    /// were cancelled.
    public List<FileDialogSpec> shown() {
        return List.copyOf(shown);
    }

    /// Forgets the scripted answers and the record of what was shown.
    public HeadlessFileDialogs reset() {
        scripted.clear();
        shown.clear();
        return this;
    }

    @Override
    public void show(@Nullable BackendWindow owner, FileDialogSpec spec, Consumer<FileChoice> onChoice) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(onChoice, "onChoice");
        shown.add(spec);
        if (!supported) {
            onChoice.accept(FileChoice.failed("this backend has no file dialogs"));
            return;
        }
        var answer = scripted.poll();
        onChoice.accept(answer == null ? FileChoice.cancelled() : answer);
    }

    @Override
    public String toString() {
        return "FileDialogs[headless, " + scripted.size() + " scripted]";
    }
}
