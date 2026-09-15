package io.github.digitalsmile.goldberry.natives.platform.calls;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// The one function that reports what this build of the platform layer can do.
///
/// A `…calls` package of its own, like every other holder: see [Downcalls] for
/// why a holder's handle has to be a `static final` constant of a class a
/// `--initialize-at-build-time` package pattern covers.
public record CapabilityCalls(PlatformCapabilities platformCapabilities) {

    /// Binds the function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static CapabilityCalls bind(SymbolLookup lookup) {
        return new CapabilityCalls(new PlatformCapabilities(lookup));
    }

    /// The capability bits this `libgoldberry` was compiled with.
    ///
    /// Build-time, and a plain constant in C: nothing is probed at run time, so
    /// this is as cheap as it looks and may be asked before SDL has started.
    ///
    /// `uint32_t goldberry_platform_capabilities(void)`
    ///
    /// @return the bits, as `NativeCapability#bit()` numbers them
    public static final class PlatformCapabilities {

        private static final MethodHandle FD_goldberry_platform_capabilities =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        PlatformCapabilities(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_platform_capabilities");
        }

        public int call() {
            try {
                return (int) FD_goldberry_platform_capabilities.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_platform_capabilities", t);
            }
        }
    }
}
