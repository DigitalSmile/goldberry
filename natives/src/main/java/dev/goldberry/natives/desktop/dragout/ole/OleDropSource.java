package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Upcalls;
import dev.goldberry.natives.desktop.windows.ComServer;

/// `IDropSource`: what `DoDragDrop` asks, on every turn of its modal loop,
/// whether the drag goes on and how the pointer should look.
///
/// Both questions are put to a [Callbacks]. One that throws ends the drag as
/// cancelled, since nothing may escape an upcall.
public final class OleDropSource extends ComServer {

    private static final Logger LOG = Logs.of(OleDropSource.class);

    /// `IID_IDropSource`.
    public static final String IID = "{00000121-0000-0000-C000-000000000046}";

    /// `HRESULT QueryContinueDrag(this, BOOL, DWORD)`.
    static final FunctionDescriptor QUERY_CONTINUE =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

    /// `HRESULT GiveFeedback(this, DWORD)`.
    static final FunctionDescriptor GIVE_FEEDBACK =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// The two questions, answered in `HRESULT`s.
    public interface Callbacks {

        /// Whether the drag goes on: `S_OK`, `DRAGDROP_S_DROP` or
        /// `DRAGDROP_S_CANCEL`.
        ///
        /// @param escape whether Escape was pressed since the last call
        /// @param keys   the `MK_` mask of the buttons and modifiers held now
        int queryContinueDrag(boolean escape, int keys);

        /// The pointer's shape for `effect`, the `DROPEFFECT` the target
        /// under it answered: `DRAGDROP_S_USEDEFAULTCURSORS`, or `S_OK` once
        /// the source has set one itself.
        int giveFeedback(int effect);
    }

    private final Callbacks callbacks;

    /// A drop source with one reference, the caller's.
    public OleDropSource(Callbacks callbacks) {
        super(Table.VTABLE, IID);
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    /// `IDropSource::QueryContinueDrag`.
    static int queryContinueDrag(MemorySegment self, int escape, int keys) {
        try {
            var source = find(self, OleDropSource.class);
            return source == null
                    ? OleStructs.DRAGDROP_S_CANCEL
                    : source.callbacks.queryContinueDrag(escape != 0, keys);
        } catch (Throwable t) {
            LOG.warn("a drag's source failed; the drag is cancelled", t);
            return OleStructs.DRAGDROP_S_CANCEL;
        }
    }

    /// `IDropSource::GiveFeedback`.
    static int giveFeedback(MemorySegment self, int effect) {
        try {
            var source = find(self, OleDropSource.class);
            return source == null ? OleStructs.DRAGDROP_S_USEDEFAULTCURSORS : source.callbacks.giveFeedback(effect);
        } catch (Throwable t) {
            LOG.warn("a drag's feedback failed", t);
            return OleStructs.DRAGDROP_S_USEDEFAULTCURSORS;
        }
    }

    /// The kind's table, made the first time a drop source is.
    private static final class Table {

        static final MemorySegment VTABLE =
                vtable(stub("queryContinueDrag", QUERY_CONTINUE), stub("giveFeedback", GIVE_FEEDBACK));

        @SuppressWarnings("restricted")
        private static MemorySegment stub(String name, FunctionDescriptor shape) {
            try {
                var target = MethodHandles.lookup().findStatic(OleDropSource.class, name, shape.toMethodType());
                return Linker.nativeLinker().upcallStub(target, shape, Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("no IDropSource method " + name, e);
            }
        }
    }
}
