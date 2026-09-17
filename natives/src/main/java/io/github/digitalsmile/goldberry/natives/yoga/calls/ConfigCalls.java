package io.github.digitalsmile.goldberry.natives.yoga.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Yoga's `YGConfig` — the settings a whole tree is laid out under.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record ConfigCalls(
        ConfigNew configNew,
        ConfigFree configFree,
        ConfigSetPointScaleFactor configSetPointScaleFactor,
        ConfigGetPointScaleFactor configGetPointScaleFactor,
        ConfigSetUseWebDefaults configSetUseWebDefaults,
        ConfigGetUseWebDefaults configGetUseWebDefaults) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static ConfigCalls bind(SymbolLookup lookup) {
        return new ConfigCalls(
                new ConfigNew(lookup),
                new ConfigFree(lookup),
                new ConfigSetPointScaleFactor(lookup),
                new ConfigGetPointScaleFactor(lookup),
                new ConfigSetUseWebDefaults(lookup),
                new ConfigGetUseWebDefaults(lookup));
    }

    /// Allocates a config with Yoga’s defaults.
    ///
    /// `void* YGConfigNew(void)`
    public static final class ConfigNew {

        private static final MethodHandle FD_YGConfigNew = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        ConfigNew(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigNew");
        }

        /// Calls `YGConfigNew`.
        ///
        /// @return the new `YGConfigRef`
        public MemorySegment call() {
            try {
                return (MemorySegment) FD_YGConfigNew.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigNew", t);
            }
        }
    }

    /// Releases a config. Every node made with it must be gone first.
    ///
    /// `void YGConfigFree(void*)`
    public static final class ConfigFree {

        private static final MethodHandle FD_YGConfigFree = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        ConfigFree(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigFree");
        }

        /// Calls `YGConfigFree`.
        ///
        /// @param config the config to release
        public void call(MemorySegment config) {
            try {
                FD_YGConfigFree.invokeExact(address, config);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigFree", t);
            }
        }
    }

    /// Sets the grid Yoga rounds computed edges to.
    ///
    /// This is what keeps a 1.5× display from leaving a half-pixel seam between
    /// two boxes that share an edge: both round to the same physical grid.
    ///
    /// `void YGConfigSetPointScaleFactor(void*, float)`
    public static final class ConfigSetPointScaleFactor {

        private static final MethodHandle FD_YGConfigSetPointScaleFactor =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        ConfigSetPointScaleFactor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigSetPointScaleFactor");
        }

        /// Calls `YGConfigSetPointScaleFactor`.
        ///
        /// @param factor physical pixels per logical one; 0 disables rounding entirely
        public void call(MemorySegment config, float factor) {
            try {
                FD_YGConfigSetPointScaleFactor.invokeExact(address, config, factor);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigSetPointScaleFactor", t);
            }
        }
    }

    /// The rounding grid in force.
    ///
    /// `float YGConfigGetPointScaleFactor(void*)`
    public static final class ConfigGetPointScaleFactor {

        private static final MethodHandle FD_YGConfigGetPointScaleFactor =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        ConfigGetPointScaleFactor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigGetPointScaleFactor");
        }

        /// Calls `YGConfigGetPointScaleFactor`.
        ///
        /// @return physical pixels per logical one
        public float call(MemorySegment config) {
            try {
                return (float) FD_YGConfigGetPointScaleFactor.invokeExact(address, config);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigGetPointScaleFactor", t);
            }
        }
    }

    /// Chooses between CSS’s flexbox defaults and Yoga’s own.
    ///
    /// They disagree about `flex-direction` and `align-content`, so this decides
    /// what a node that says nothing does.
    ///
    /// `void YGConfigSetUseWebDefaults(void*, _Bool)`
    public static final class ConfigSetUseWebDefaults {

        private static final MethodHandle FD_YGConfigSetUseWebDefaults =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        ConfigSetUseWebDefaults(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigSetUseWebDefaults");
        }

        /// Calls `YGConfigSetUseWebDefaults`.
        ///
        /// @param useWebDefaults true for CSS’s defaults, false for Yoga’s
        public void call(MemorySegment config, boolean useWebDefaults) {
            try {
                FD_YGConfigSetUseWebDefaults.invokeExact(address, config, useWebDefaults);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigSetUseWebDefaults", t);
            }
        }
    }

    /// Whether CSS’s defaults are in force.
    ///
    /// `_Bool YGConfigGetUseWebDefaults(void*)`
    public static final class ConfigGetUseWebDefaults {

        private static final MethodHandle FD_YGConfigGetUseWebDefaults =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        ConfigGetUseWebDefaults(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigGetUseWebDefaults");
        }

        /// Calls `YGConfigGetUseWebDefaults`.
        ///
        /// @return true for CSS’s defaults
        public boolean call(MemorySegment config) {
            try {
                return (boolean) FD_YGConfigGetUseWebDefaults.invokeExact(address, config);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigGetUseWebDefaults", t);
            }
        }
    }
}
