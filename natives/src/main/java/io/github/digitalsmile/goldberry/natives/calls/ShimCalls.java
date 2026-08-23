package io.github.digitalsmile.goldberry.natives.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// The three functions `libgoldberry` exports for itself, one holder each.
///
/// The shape every `…calls` record has, and the smallest example of it — see
/// [io.github.digitalsmile.goldberry.natives.calls] for why the package exists.
public record ShimCalls(
        AbiVersion abiVersion,
        LayoutTable layoutTable,
        LayoutCount layoutCount) {

    /// Binds all three. Throws if `libgoldberry` exports any of them.
    public static ShimCalls bind(SymbolLookup lookup) {
        return new ShimCalls(
                new AbiVersion(lookup),
                new LayoutTable(lookup),
                new LayoutCount(lookup));
    }

    /// `int goldberry_abi_version(void)`
    public static final class AbiVersion {

        private static final MethodHandle FD_goldberry_abi_version =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT));

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

    /// `const goldberry_layout_entry* goldberry_layout_table(void)`
    public static final class LayoutTable {

        private static final MethodHandle FD_goldberry_layout_table =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

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

    /// `int goldberry_layout_count(void)`
    public static final class LayoutCount {

        private static final MethodHandle FD_goldberry_layout_count =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT));

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
