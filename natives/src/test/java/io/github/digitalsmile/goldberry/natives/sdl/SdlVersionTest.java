package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SdlVersionTest {

    /// `SDL_VERSIONNUM(major, minor, patch)` is
    /// `major * 1000000 + minor * 1000 + patch`, from `SDL3/SDL_version.h`.
    @ParameterizedTest
    @CsvSource({
        "3002000, 3, 2, 0",
        "3002014, 3, 2, 14",
        "3004001, 3, 4, 1",
        "3000000, 3, 0, 0",
        "12345678, 12, 345, 678",
    })
    @DisplayName("decoding matches SDL_VERSIONNUM")
    void decodeMatchesSdl(int encoded, int major, int minor, int patch) {
        assertEquals(new SdlVersion(major, minor, patch), SdlVersion.decode(encoded));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3002000, 3002014, 12345678, Integer.MAX_VALUE})
    @DisplayName("encode and decode are inverses")
    void roundTrips(int encoded) {
        assertEquals(encoded, SdlVersion.decode(encoded).encode());
    }

    @Test
    @DisplayName("a negative version number is rejected")
    void negativeEncodedRejected() {
        assertThrows(IllegalArgumentException.class, () -> SdlVersion.decode(-1));
    }

    @Test
    @DisplayName("negative components are rejected")
    void negativeComponentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SdlVersion(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SdlVersion(3, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SdlVersion(3, 2, -1));
    }

    @Test
    @DisplayName("ordering is by major, then minor, then patch")
    void ordersCorrectly() {
        var base = new SdlVersion(3, 2, 0);

        assertTrue(base.isAtLeast(new SdlVersion(3, 2, 0)));
        assertTrue(base.isAtLeast(new SdlVersion(3, 1, 99)));
        assertTrue(base.isAtLeast(new SdlVersion(2, 99, 99)));
        assertFalse(base.isAtLeast(new SdlVersion(3, 2, 1)));
        assertFalse(base.isAtLeast(new SdlVersion(4, 0, 0)));
    }

    @Test
    @DisplayName("reads as three numbers in order, so a log line names the linked build")
    void printsReadably() {
        // The claim is that all three components are there and in order; the
        // separator is not a contract and is not pinned.
        var text = new SdlVersion(3, 2, 14).toString();

        assertTrue(text.matches("\\D*3\\D+2\\D+14\\D*"), text);
    }
}
