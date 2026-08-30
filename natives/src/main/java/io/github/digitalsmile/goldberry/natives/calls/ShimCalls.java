package io.github.digitalsmile.goldberry.natives.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// The three functions `libgoldberry` exports for itself.
///
/// The smallest example of the shape every `…Calls` record has.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record ShimCalls(AbiVersion abiVersion, LayoutTable layoutTable, LayoutCount layoutCount) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static ShimCalls bind(SymbolLookup lookup) {
        return new ShimCalls(new AbiVersion(lookup), new LayoutTable(lookup), new LayoutCount(lookup));
    }

    /// The ABI version the loaded `libgoldberry` reports.
    ///
    /// Checked against `GoldberryShim.SUPPORTED_ABI_VERSION` on first use: a
    /// mismatched pair of artifacts is a link error worth raising at start-up
    /// rather than at the first call that reads a struct differently.
    ///
    /// `int goldberry_abi_version(void)`
    ///
    /// @return the library’s ABI version
    public static final class AbiVersion {

        private static final MethodHandle FD_goldberry_abi_version = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        AbiVersion(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_abi_version");
        }

        public int call() {
            try {
                return (int) FD_goldberry_abi_version.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_abi_version", t);
            }
        }
    }

    /// The first entry of the layout table.
    ///
    /// The table is how the hand-written struct layouts are checked against the
    /// library actually compiled for this machine (ADR-0010). The segment is
    /// zero-length — a bare pointer carries no extent — so the caller resizes it
    /// against [ShimCalls.LayoutCount].
    ///
    /// `void* goldberry_layout_table(void)`
    ///
    /// @return a `const goldberry_layout_entry*`
    public static final class LayoutTable {

        private static final MethodHandle FD_goldberry_layout_table = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        LayoutTable(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_layout_table");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_goldberry_layout_table.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_layout_table", t);
            }
        }
    }

    /// How many entries the layout table holds.
    ///
    /// `int goldberry_layout_count(void)`
    ///
    /// @return the entry count
    public static final class LayoutCount {

        private static final MethodHandle FD_goldberry_layout_count = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        LayoutCount(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_layout_count");
        }

        public int call() {
            try {
                return (int) FD_goldberry_layout_count.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_layout_count", t);
            }
        }
    }
}
