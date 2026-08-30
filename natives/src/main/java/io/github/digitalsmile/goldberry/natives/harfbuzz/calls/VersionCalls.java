package io.github.digitalsmile.goldberry.natives.harfbuzz.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// HarfBuzz's version query.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record VersionCalls(Version version) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static VersionCalls bind(SymbolLookup lookup) {
        return new VersionCalls(new Version(lookup));
    }

    /// Reads the version of HarfBuzz linked into `libgoldberry`.
    ///
    /// Static linking makes this a build fact rather than a runtime one.
    ///
    /// `void hb_version(void*, void*, void*)`
    ///
    /// @param major a caller-allocated `unsigned*`
    /// @param minor a caller-allocated `unsigned*`
    /// @param micro a caller-allocated `unsigned*`
    public static final class Version {

        private static final MethodHandle FD_hb_version =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "hb_version");
        }

        public void call(MemorySegment major, MemorySegment minor, MemorySegment micro) {
            try {
                FD_hb_version.invokeExact(address, major, minor, micro);
            } catch (Throwable t) {
                throw Downcalls.failure("hb_version", t);
            }
        }
    }
}
