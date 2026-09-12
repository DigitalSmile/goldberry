package io.github.digitalsmile.goldberry.render.backend.sdl3;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.sdl.SdlFileDialogs;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlDialogKind;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlFileDialogCallback;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlFileFilter;
import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogSpec;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogs;
import io.github.digitalsmile.goldberry.render.dialog.FileFilter;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;

/// [FileDialogs] over SDL3, and the one place in this backend where an answer
/// arrives on a thread the toolkit does not own.
///
/// ## The hop, and why it is the pump rather than an executor
///
/// SDL's callback comes back on whichever thread the platform answered on — the
/// DBus thread that talks to the XDG portal, on Linux. The SPI promises the UI
/// thread, so the answer is parked in a queue, [Sdl3Backend#wakeup()] ends the
/// wait it is blocked in, and [#deliverPending()] runs the consumers at the top
/// of the next pump.
///
/// [io.github.digitalsmile.goldberry.render.event.UiExecutor] is the toolkit's
/// general answer to this question and is deliberately not used here: it belongs
/// to the [io.github.digitalsmile.goldberry.render.event.EventLoop], which is
/// built *on* a backend and cannot be reached from inside one. A backend that
/// reached up into the loop it is driven by would be a cycle in the module's own
/// dependency graph and a second lifetime to get right — the queue below is nine
/// lines and needs neither.
///
/// ## Filters
///
/// [FileFilter]'s extensions are the toolkit's vocabulary; `png;jpg` is SDL's.
/// The translation is here rather than in the record, because it is one
/// platform's dialect and the next backend's would be another
/// (ADR-0287).
///
/// Confined to the UI thread, apart from the queue, which is why the queue is
/// concurrent.
final class Sdl3FileDialogs implements FileDialogs {

    private static final Logger LOG = Logs.of(Sdl3FileDialogs.class);

    private final SdlFileDialogs dialogs = SdlFileDialogs.get();
    private final ConcurrentLinkedQueue<Runnable> answers = new ConcurrentLinkedQueue<>();
    private final Runnable wakeup;

    Sdl3FileDialogs(Runnable wakeup) {
        this.wakeup = Objects.requireNonNull(wakeup, "wakeup");
    }

    @Override
    public boolean supported() {
        return true;
    }

    @Override
    public void show(@Nullable BackendWindow owner, FileDialogSpec spec, Consumer<FileChoice> onChoice) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(onChoice, "onChoice");

        var handle = handleOf(owner);
        var location = spec.startingPoint().map(Path::toString).orElse(null);
        var filters = spec.filters().stream().map(Sdl3FileDialogs::toSdl).toList();

        dialogs.show(kindOf(spec), handle, filters, location, spec.allowMany(), new SdlFileDialogCallback() {

            @Override
            public void chosen(List<String> paths, int filterIndex) {
                var chosen = paths.stream().map(Path::of).toList();
                queue(onChoice, new FileChoice.Chosen(chosen, filterAt(spec, filterIndex)));
            }

            @Override
            public void cancelled() {
                queue(onChoice, FileChoice.cancelled());
            }

            @Override
            public void failed(String error) {
                LOG.debug("a file dialog failed: {}", error);
                queue(onChoice, FileChoice.failed(error));
            }
        });
    }

    /// Runs the consumers of every dialog that has answered since the last pump.
    ///
    /// On the UI thread, from [Sdl3Backend#pumpEvents]. A consumer that throws
    /// propagates — an application's own bug belongs where it can be seen — and
    /// the answers after it in the same batch are still delivered, because one
    /// broken handler must not lose a second dialog's result.
    ///
    /// @return how many were delivered
    int deliverPending() {
        var delivered = 0;
        RuntimeException failure = null;
        // A snapshot rather than a loop until empty: a consumer that opens
        // another dialog must not be able to hold the pump.
        for (var pending = answers.size(); pending > 0; pending--) {
            var answer = answers.poll();
            if (answer == null) {
                break;
            }
            delivered++;
            try {
                answer.run();
            } catch (RuntimeException e) {
                failure = failure == null ? e : failure;
            }
        }
        if (failure != null) {
            throw failure;
        }
        return delivered;
    }

    /// Whether anything is waiting to be delivered — read by the pump before it
    /// decides how long to wait.
    boolean hasPending() {
        return !answers.isEmpty();
    }

    private void queue(Consumer<FileChoice> onChoice, FileChoice choice) {
        answers.add(() -> onChoice.accept(choice));
        // Enqueue, then wake: the other way round races, and a dialog answered
        // while the loop is parked for a second would sit there until something
        // else happened.
        wakeup.run();
    }

    private static SdlDialogKind kindOf(FileDialogSpec spec) {
        return switch (spec.kind()) {
            case OPEN_FILE -> SdlDialogKind.OPEN_FILE;
            case SAVE_FILE -> SdlDialogKind.SAVE_FILE;
            case OPEN_FOLDER -> SdlDialogKind.OPEN_FOLDER;
        };
    }

    private static SdlFileFilter toSdl(FileFilter filter) {
        return filter.matchesEverything()
                ? SdlFileFilter.all(filter.label())
                : new SdlFileFilter(filter.label(), String.join(";", filter.extensions()));
    }

    /// Which filter the platform says was selected, if it says at all.
    ///
    /// `-1` is SDL's "this platform does not report one", and it is the common
    /// answer rather than the exceptional one — GTK's save dialog does not.
    private static Optional<FileFilter> filterAt(FileDialogSpec spec, int index) {
        return index >= 0 && index < spec.filters().size()
                ? Optional.of(spec.filters().get(index))
                : Optional.empty();
    }

    private static @Nullable SdlWindowHandle handleOf(@Nullable BackendWindow owner) {
        return owner instanceof Sdl3Window window ? window.handle() : null;
    }

    @Override
    public String toString() {
        return "FileDialogs[sdl3]";
    }
}
