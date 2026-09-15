package io.github.digitalsmile.goldberry.natives.platform;

import java.lang.foreign.SymbolLookup;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.platform.calls.CapabilityCalls;

/// What the loaded `libgoldberry` can ask the desktop.
///
/// One downcall, asked once, and the answer is a compile-time constant on the
/// other side — so this is a fact about the artifact rather than a probe of the
/// running system, and it can be read before any backend has started.
///
/// `:core` translates it into the toolkit's own vocabulary; an application reads
/// `Goldberry.capabilities()` and never this ([ADR-0174] keeps `natives.*` inside
/// `:natives`). See [NativeCapability] for why a library has to report this at
/// all, and `docs/gaps.md` G32 for the day it could not.
public final class NativeCapabilities {

    private static final Logger LOG = Logs.of(NativeCapabilities.class);

    private static final class Holder {
        private static final Set<NativeCapability> INSTANCE =
                read(NativeLibrary.get().lookup());
    }

    private NativeCapabilities() {}

    /// The capabilities of the loaded library, loading it on first call.
    ///
    /// @throws UnsatisfiedLinkError if `libgoldberry` cannot be found or loaded
    public static Set<NativeCapability> get() {
        return Holder.INSTANCE;
    }

    /// Reads the bits from an already-loaded library.
    ///
    /// @param lookup the loaded `libgoldberry`
    /// @return the capabilities it was built with
    public static Set<NativeCapability> read(SymbolLookup lookup) {
        var bits = CapabilityCalls.bind(lookup).platformCapabilities().call();
        var capabilities = decode(bits);
        // The first question a "why is this application in the wrong theme" report
        // has to answer, and it costs one line at start-up rather than a rebuild
        // of the native library to find out.
        LOG.debug("libgoldberry platform capabilities: {} (0x{})", capabilities, Integer.toHexString(bits));
        return capabilities;
    }

    /// Splits a capability word into the capabilities it names.
    ///
    /// Tolerant of bits this enum does not know, which is
    /// [io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlSystemTheme#of]'s
    /// rule: a library that reports a capability a caller has never heard of is a
    /// caller that cannot use it, not a caller that should fail to start.
    ///
    /// @param bits the word `goldberry_platform_capabilities` returned
    /// @return the capabilities it sets, in declaration order
    public static Set<NativeCapability> decode(int bits) {
        var capabilities = EnumSet.noneOf(NativeCapability.class);
        for (var capability : NativeCapability.values()) {
            if ((bits & capability.bit()) != 0) {
                capabilities.add(capability);
            }
        }
        // An unmodifiable EnumSet, not `Set.copyOf`: the declaration order is what
        // makes two logs of the same library read the same.
        return Collections.unmodifiableSet(capabilities);
    }

    /// The word that would decode to `capabilities` — the inverse of [#decode].
    ///
    /// For tests and for the log line: an `int` is what the C side deals in, and
    /// round-tripping is how the two halves are checked against each other.
    ///
    /// @param capabilities the capabilities to encode
    /// @return their bits, or zero for none
    public static int encode(Set<NativeCapability> capabilities) {
        var bits = 0;
        for (var capability : capabilities) {
            bits |= capability.bit();
        }
        return bits;
    }
}
