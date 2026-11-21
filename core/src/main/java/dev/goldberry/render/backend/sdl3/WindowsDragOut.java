package dev.goldberry.render.backend.sdl3;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.OleDragSource;
import dev.goldberry.render.dragout.DragRequest;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.window.NativeHandle;

/// A drag out on Windows: the offer in clipboard formats on an `IDataObject`
/// of the toolkit's own, dragged by OLE's `DoDragDrop`.
///
/// `DoDragDrop` does not return until the drag is over, so the drag runs
/// inside [#run], which the backend calls from inside the pump that started
/// it. The source runs a turn while it holds the thread
/// ([Sdl3Backend#turnWhileHeld]): the reports queued since the last one, the
/// loop's posted work and timers, and the frames that are due. SDL's own
/// queue is not read during the drag: reading it pumps Windows messages, and
/// OLE's modal loop is pumping them already.
///
/// SDL's own drop target hears the drag over the application's windows too,
/// and, for an offer of files or text, drops it there. Its `DROP_*` events are
/// read once the drag is over, so this drag takes every one of them until the
/// pump after its ending has read them; the position comes from the source,
/// which asks where the pointer is.
final class WindowsDragOut implements PlatformDragOut {

    private static final Logger LOG = Logs.of(WindowsDragOut.class);

    /// The drag the platform runs: [OleDragSource]'s two calls, apart so the
    /// bookkeeping around them is tested without Windows.
    interface HeldDrag {

        /// Runs the drag to its end; false when it never began.
        boolean run();

        /// Ends it at the platform's next question.
        void cancel();
    }

    private final HeldDrag source;
    private final DropEchoes echoes = new DropEchoes();

    WindowsDragOut(HeldDrag source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /// Readies `request` from `window`, or answers null where it is not on
    /// Windows or OLE will not take it. Nothing is dragged until [#run].
    static @Nullable WindowsDragOut open(
            Sdl3Backend backend, Sdl3Window window, DragRequest request, DragSession session) {
        var handle = window.nativeHandle();
        if (handle.isEmpty() || handle.get().kind() != NativeHandle.Kind.WIN32) {
            return null;
        }
        var own = DragReports.ownWindows(backend, NativeHandle.Kind.WIN32);
        var offer = DragOutData.withImageFile(request.offer());
        var opened = OleDragSource.open(
                WindowsDragData.formats(offer),
                DragOutData.actions(offer),
                DragOutData.icon(request.picture(), 1),
                own::containsKey,
                new DragReports(backend, own, session),
                () -> backend.turnWhileHeld(session));
        return opened.map(drag -> new WindowsDragOut(new HeldDrag() {
                    @Override
                    public boolean run() {
                        return drag.run();
                    }

                    @Override
                    public void cancel() {
                        drag.cancel();
                    }
                }))
                .orElse(null);
    }

    /// Runs the drag, returning when it is over: true when it ran, and its
    /// ending is queued for the session; false when OLE refused it before it
    /// began.
    boolean run() {
        try {
            var ran = source.run();
            if (!ran) {
                echoes.close();
            }
            return ran;
        } finally {
            echoes.ended();
        }
    }

    /// Counts pumps after the ending: the first reads the drop SDL's target
    /// queued during the drag, which is still this drag's, and the second
    /// finds it closed.
    @Override
    public void tick() {
        echoes.tick();
    }

    @Override
    public boolean isClosed() {
        return echoes.isClosed();
    }

    @Override
    public void close() {
        var ended = echoes.isEnded();
        if (!ended) {
            // From inside a turn: DoDragDrop ends at OLE's next question.
            source.cancel();
        }
        echoes.close();
        LOG.debug("a drag out on Windows was closed {}", ended ? "after its end" : "while it ran");
    }

    /// Every drop event while the drag lasts and for the pump after: SDL's
    /// target saw the application's own offer, and nothing of it is a drop
    /// from outside.
    @Override
    public boolean takesDrop(int type, Sdl3Window window, float x, float y) {
        return !echoes.isClosed();
    }
}
