package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;

/// The two functions of libgoldberry-media's own (`goldberry_media.c`): the ABI
/// number the loader checks first, and the upstream versions.
public record MediaCalls(Abi abi, Versions versions) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static MediaCalls bind(SymbolLookup lookup) {
        return new MediaCalls(new Abi(lookup), new Versions(lookup));
    }

    /// `int goldberry_media_abi(void)`
    public static final class Abi {

        private static final MethodHandle FD_goldberry_media_abi = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Abi(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_media_abi");
        }

        public int call() {
            try {
                return (int) FD_goldberry_media_abi.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_media_abi", t);
            }
        }
    }

    /// `const char *goldberry_media_versions(void)`
    public static final class Versions {

        private static final MethodHandle FD_goldberry_media_versions = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        Versions(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_media_versions");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_goldberry_media_versions.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_media_versions", t);
            }
        }
    }
}
