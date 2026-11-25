package dev.goldberry.vector.thorvg;

import java.lang.foreign.FunctionDescriptor;
import java.util.List;

import dev.goldberry.vector.thorvg.calls.CanvasCalls;
import dev.goldberry.vector.thorvg.calls.EngineCalls;
import dev.goldberry.vector.thorvg.calls.PictureCalls;
import dev.goldberry.vector.thorvg.calls.VectorCalls;

/// Every foreign-call shape of the vector bindings, for the module's
/// `reachability-metadata.json`, which `…vector.nativeimage` writes.
///
/// Each `…Calls` record's components are its holders; initialising a holder
/// links its `FD_…` constant through [Downcalls#link], which records the
/// descriptor. No library is needed: linking a descriptor opens nothing.
/// ThorVG calls nothing back, so there are no upcalls.
///
/// Public in a package the module does not export, so that the writer in
/// `…vector.nativeimage` can read it.
public final class VectorDescriptors {

    /// The `…Calls` records whose nested holders are the downcall surface,
    /// the components of [VectorLibrary] that bind code.
    public static final List<Class<? extends Record>> CALLS =
            List.of(VectorCalls.class, EngineCalls.class, CanvasCalls.class, PictureCalls.class);

    private VectorDescriptors() {}

    /// Every downcall descriptor, after initialising every holder.
    public static List<FunctionDescriptor> downcalls() {
        for (var calls : CALLS) {
            for (var component : calls.getRecordComponents()) {
                try {
                    Class.forName(component.getType().getName(), true, calls.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("a holder of " + calls.getSimpleName() + " is not loadable", e);
                }
            }
        }
        return Downcalls.linked();
    }
}
