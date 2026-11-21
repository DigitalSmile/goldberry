package dev.goldberry.natives.desktop.dragout;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongPredicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.ole.OleCalls;
import dev.goldberry.natives.desktop.dragout.ole.OleDataObject;
import dev.goldberry.natives.desktop.dragout.ole.OleDragImage;
import dev.goldberry.natives.desktop.dragout.ole.OleDropSource;
import dev.goldberry.natives.desktop.dragout.ole.OleStructs;
import dev.goldberry.natives.desktop.windows.Win32GlobalMemory;

/// A drag out on Windows: OLE's `DoDragDrop`, with an `IDataObject` and an
/// `IDropSource` of the toolkit's own.
///
/// `DoDragDrop` is a modal loop. [#run] returns when the drag is over, and
/// everything in between happens inside it, on the UI thread: OLE asks the
/// source on every turn whether to go on and how the pointer should look, the
/// source reads where the pointer is and tells the listener when it is over
/// one of the application's own windows, and runs one turn of the toolkit's
/// loop so that the windows keep painting and the in-app targets keep
/// answering. The listener hears the ending before [#run] returns.
///
/// A target in another process asks for the data later than the drop, as
/// some do, through the data object it still holds; the object stays alive
/// for as long as anyone does.
///
/// UNVERIFIED: written against the Windows SDK's documentation. The objects
/// are called through their vtables by tests on every platform, and through
/// OLE's own clipboard on the Windows lane; `DoDragDrop` itself needs a held
/// button no test can make.
public final class OleDragSource {

    private static final Logger LOG = Logs.of(OleDragSource.class);

    private final OleCalls calls;
    private final OleDataObject data;
    private final OleDropSource source;
    private final OleDragTracker tracker;
    private final int allowed;
    private boolean ran;

    private OleDragSource(
            OleCalls calls, OleDataObject data, OleDropSource source, OleDragTracker tracker, int allowed) {
        this.calls = calls;
        this.data = data;
        this.source = source;
        this.tracker = tracker;
        this.allowed = allowed;
    }

    /// Readies a drag, or answers empty off Windows, where OLE will not start
    /// on this thread, or where no format could be offered.
    ///
    /// @param formats  what the drag carries, most specific first
    /// @param actions  what a target may do with it
    /// @param picture  what follows the pointer, or null for OLE's cursors alone
    /// @param own      whether a top-level `HWND` is one of the application's
    /// @param listener told where the pointer is over own windows and how the
    ///                 drag ended, on the UI thread
    /// @param turn     one turn of the toolkit's loop, run while OLE holds the
    ///                 thread
    public static Optional<OleDragSource> open(
            List<OleFormat> formats,
            Set<DragAction> actions,
            @Nullable DragIcon picture,
            LongPredicate own,
            DragSourceListener listener,
            Runnable turn) {
        Objects.requireNonNull(own, "own");
        var calls = OleCalls.open();
        var memory = Win32GlobalMemory.open();
        if (calls.isEmpty() || memory.isEmpty()) {
            return Optional.empty();
        }
        var ole = calls.get();
        if (!ole.initialise()) {
            LOG.debug("OLE will not start on the UI thread; a drag stays inside the application");
            return Optional.empty();
        }
        var offered = resolve(formats, ole::registerFormat);
        if (offered.isEmpty()) {
            return Optional.empty();
        }
        var data = new OleDataObject(memory.get(), offered);
        var tracker = new OleDragTracker(
                () -> {
                    var located = ole.locate();
                    return located != null && own.test(located.window())
                            ? new OleDragTracker.Over(located.window(), located.x(), located.y())
                            : null;
                },
                listener,
                turn);
        var source = new OleDropSource(tracker);
        if (picture != null) {
            OleDragImage.attach(
                    data.pointer(),
                    picture.width(),
                    picture.height(),
                    picture.stride(),
                    picture.pixels(),
                    picture.hotspotX(),
                    picture.hotspotY());
        }
        return Optional.of(new OleDragSource(ole, data, source, tracker, effects(actions)));
    }

    /// Runs the drag, returning when it is over. True when it ran, and the
    /// listener has heard its ending; false when OLE refused it before it
    /// began, and the listener has heard nothing.
    ///
    /// @throws IllegalStateException when it has run already
    public boolean run() {
        if (ran) {
            throw new IllegalStateException("a drag runs once");
        }
        ran = true;
        int result;
        int effect;
        try (var arena = Arena.ofConfined()) {
            var performed = arena.allocate(JAVA_INT);
            result = calls.doDragDrop(data.pointer(), source.pointer(), allowed, performed);
            effect = performed.get(JAVA_INT, 0);
        } finally {
            // Ours; a target that keeps the data object holds its own.
            data.release();
            source.release();
        }
        if (result < 0 && !tracker.heard()) {
            LOG.debug("DoDragDrop refused the drag: 0x{}", Integer.toHexString(result));
            return false;
        }
        tracker.finish(result, effect);
        return true;
    }

    /// Ends the drag at OLE's next question, as cancelled.
    public void cancel() {
        tracker.cancel();
    }

    /// The `DROPEFFECT`s for `actions`.
    static int effects(Set<DragAction> actions) {
        var effects = 0;
        for (var action : actions) {
            effects |= switch (action) {
                case COPY -> OleStructs.DROPEFFECT_COPY;
                case MOVE -> OleStructs.DROPEFFECT_MOVE;
                case LINK -> OleStructs.DROPEFFECT_LINK;
            };
        }
        return effects;
    }

    /// `formats` with each registered name made a number, in order. A name the
    /// system will not register is left out, and a format offered twice is
    /// offered once, as first given.
    static List<OleDataObject.Offered> resolve(List<OleFormat> formats, Function<String, OptionalInt> register) {
        var offered = new ArrayList<OleDataObject.Offered>();
        var seen = new ArrayList<Integer>();
        for (var format : formats) {
            var name = format.registered();
            var number = name == null ? OptionalInt.of(format.standard()) : register.apply(name);
            if (number.isEmpty()) {
                LOG.debug("the clipboard format {} could not be registered; it is not offered", format.label());
                continue;
            }
            if (seen.contains(number.getAsInt())) {
                continue;
            }
            seen.add(number.getAsInt());
            offered.add(new OleDataObject.Offered(number.getAsInt(), format.bytes()::get));
        }
        return offered;
    }
}
