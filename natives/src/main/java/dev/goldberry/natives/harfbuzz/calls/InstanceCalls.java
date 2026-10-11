package dev.goldberry.natives.harfbuzz.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// HarfBuzz's instancer: a variable face pinned to one point of its design
/// space, and the bytes of what comes back.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record InstanceCalls(Instance instance, BlobGetData blobGetData) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static InstanceCalls bind(SymbolLookup lookup) {
        return new InstanceCalls(new Instance(lookup), new BlobGetData(lookup));
    }

    /// Pins every axis of the face in a blob, to its default and then to the
    /// values given, and serialises the static font that leaves.
    ///
    /// `void* goldberry_hb_instance(void*, const uint32_t*, const float*, unsigned)`
    public static final class Instance {

        private static final MethodHandle FD_goldberry_hb_instance =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        Instance(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_hb_instance");
        }

        /// Calls `goldberry_hb_instance`.
        ///
        /// @param blob   the variable font's `hb_blob_t*`
        /// @param tags   `count` axis tags, as `uint32_t`
        /// @param values `count` axis positions, as `float`, in the axis's own
        ///               units
        /// @param count  how many axes are named
        /// @return a new `hb_blob_t*` holding the static font, or NULL when the
        ///         face cannot be instanced
        public MemorySegment call(MemorySegment blob, MemorySegment tags, MemorySegment values, int count) {
            try {
                return (MemorySegment) FD_goldberry_hb_instance.invokeExact(address, blob, tags, values, count);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_hb_instance", t);
            }
        }
    }

    /// The bytes a blob holds.
    ///
    /// `const char* hb_blob_get_data(void*, unsigned*)`
    public static final class BlobGetData {

        private static final MethodHandle FD_hb_blob_get_data =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        BlobGetData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_hb_blob_get_data");
        }

        /// Calls `hb_blob_get_data`.
        ///
        /// @param blob   the blob
        /// @param length a caller-allocated `unsigned*` the length is written to
        /// @return the blob's first byte, owned by the blob
        public MemorySegment call(MemorySegment blob, MemorySegment length) {
            try {
                return (MemorySegment) FD_hb_blob_get_data.invokeExact(address, blob, length);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_hb_blob_get_data", t);
            }
        }
    }
}
