package dev.goldberry.vector.thorvg.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;

import dev.goldberry.vector.thorvg.Downcalls;

/// The two functions of libgoldberry-vector's own (`goldberry_vector.c`): the
/// ABI number the loader checks first, and the line naming the build.
public record VectorCalls(Abi abi, Versions versions) {

    /// The symbols this record binds, in the order of its components.
    public static final List<String> SYMBOLS = List.of("goldberry_vector_abi", "goldberry_vector_versions");

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static VectorCalls bind(SymbolLookup lookup) {
        return new VectorCalls(new Abi(lookup), new Versions(lookup));
    }

    /// `int goldberry_vector_abi(void)`
    public static final class Abi {

        private static final MethodHandle FD_goldberry_vector_abi = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Abi(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_vector_abi");
        }

        public int call() {
            try {
                return (int) FD_goldberry_vector_abi.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_vector_abi", t);
            }
        }
    }

    /// `const char *goldberry_vector_versions(void)`
    ///
    /// A static string, valid for the life of the process.
    public static final class Versions {

        private static final MethodHandle FD_goldberry_vector_versions = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        Versions(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_vector_versions");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_goldberry_vector_versions.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_vector_versions", t);
            }
        }
    }
}
