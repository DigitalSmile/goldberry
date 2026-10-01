package dev.goldberry.media.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Which system's package `PlatformDecoders` asks. The other systems' answers
/// are asked for too, which binds nothing on this one: each package refuses a
/// system that is not its own before it opens a library.
@DisplayName("PlatformDecoders")
class PlatformDecodersTest {

    @Test
    @DisplayName("asks the system's own package, and names a system it has no decoders for")
    void asksTheSystemsOwnPackage() {
        var here = System.getProperty("os.name", "");
        assertEquals(PlatformDecoders.unavailableReason(), PlatformDecoders.unavailableReason(here));
        assertEquals(Optional.of("no platform decoders for SunOS"), PlatformDecoders.unavailableReason("SunOS"));
        if (!here.toLowerCase().startsWith("windows")) {
            assertTrue(PlatformDecoders.unavailableReason("Windows 11")
                    .orElseThrow()
                    .contains("Windows"));
        }
        if (!here.toLowerCase().startsWith("mac")) {
            assertTrue(PlatformDecoders.unavailableReason("Mac OS X").isPresent());
        }
        assertEquals(
                PlatformDecoders.available(),
                PlatformDecoders.unavailableReason().isEmpty());
    }
}
