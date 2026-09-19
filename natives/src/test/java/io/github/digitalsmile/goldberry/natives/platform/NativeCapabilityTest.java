package io.github.digitalsmile.goldberry.natives.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/// The capability word — `docs/gaps.md` G32, ADR-0325.
///
/// The bit *values* are checked against the compiled library by the layout probe,
/// which is where a wrong one would actually be caught. What is left for this is
/// the Java side: that the bits are a usable set of flags at all, that a word
/// splits into the capabilities it names, and what happens with a bit this enum
/// has never heard of.
@DisplayName("the platform capability word")
class NativeCapabilityTest {

    @ParameterizedTest
    @EnumSource(NativeCapability.class)
    @DisplayName("every capability is a single bit")
    void everyCapabilityIsOneBit(NativeCapability capability) {
        // A capability occupying two bits would decode as two capabilities, one of
        // which nothing declared.
        assertEquals(
                1, Integer.bitCount(capability.bit()), capability + " is 0x" + Integer.toHexString(capability.bit()));
    }

    @Test
    @DisplayName("no two capabilities share a bit")
    void bitsAreDistinct() {
        var bits = new HashSet<Integer>();
        var shared = EnumSet.noneOf(NativeCapability.class);
        for (var capability : NativeCapability.values()) {
            if (!bits.add(capability.bit())) {
                shared.add(capability);
            }
        }
        assertTrue(shared.isEmpty(), "capabilities sharing a bit: " + shared);
    }

    @ParameterizedTest
    @EnumSource(NativeCapability.class)
    @DisplayName("a word of one bit decodes to that one capability")
    void decodesASingleBit(NativeCapability capability) {
        assertEquals(Set.of(capability), NativeCapabilities.decode(capability.bit()));
    }

    @Test
    @DisplayName("a library that can do nothing reports nothing, not a failure")
    void decodesZero() {
        // What a Linux build without libdbus-1-dev, libibus-1.0-dev or libudev-dev
        // actually answers. It has to be an ordinary value: the whole point is that
        // such a library still runs, and says so.
        assertEquals(Set.of(), NativeCapabilities.decode(0));
    }

    @Test
    @DisplayName("every capability survives a round trip through the word")
    void roundTrips() {
        var all = EnumSet.allOf(NativeCapability.class);
        assertEquals(all, NativeCapabilities.decode(NativeCapabilities.encode(all)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x100, 0x4000_0000, Integer.MIN_VALUE})
    @DisplayName("a bit this enum predates is ignored rather than a failure")
    void unknownBitsAreIgnored(int unknown) {
        // The tolerant rule SdlSystemTheme.of states: a library reporting a
        // capability a caller has never heard of is a caller that cannot use it,
        // not a caller that should fail to start.
        assertEquals(
                Set.of(NativeCapability.SYSTEM_THEME),
                NativeCapabilities.decode(NativeCapability.SYSTEM_THEME.bit() | unknown));
    }

    @Test
    @DisplayName("the decoded set is unmodifiable and reads in declaration order")
    void decodedSetIsStable() {
        var capabilities = NativeCapabilities.decode(NativeCapabilities.encode(EnumSet.allOf(NativeCapability.class)));
        assertEquals(List.of(NativeCapability.values()), List.copyOf(capabilities));
        assertThrows(UnsupportedOperationException.class, () -> capabilities.add(NativeCapability.SYSTEM_THEME));
    }
}
