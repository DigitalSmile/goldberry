package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Blend2D’s process-wide runtime query.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record RuntimeCalls(RuntimeQueryInfo runtimeQueryInfo) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static RuntimeCalls bind(SymbolLookup lookup) {
        return new RuntimeCalls(new RuntimeQueryInfo(lookup));
    }

    /// Fills `out` with one of Blend2D’s runtime information structs.
    ///
    /// `int bl_runtime_query_info(int, void*)`
    public static final class RuntimeQueryInfo {

        private static final MethodHandle FD_bl_runtime_query_info =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        RuntimeQueryInfo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_runtime_query_info");
        }

        /// Calls `bl_runtime_query_info`.
        ///
        /// @param infoType which `BLRuntimeInfoType` table to fill in
        /// @param out a caller-allocated struct of the shape that info type describes
        public int call(int infoType, MemorySegment out) {
            try {
                return (int) FD_bl_runtime_query_info.invokeExact(address, infoType, out);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_runtime_query_info", t);
            }
        }
    }
}
