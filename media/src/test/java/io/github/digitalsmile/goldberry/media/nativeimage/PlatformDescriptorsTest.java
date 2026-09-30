package io.github.digitalsmile.goldberry.media.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.FunctionDescriptor;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.platform.linux.LinuxBindings;
import io.github.digitalsmile.goldberry.media.platform.macos.MacBindings;
import io.github.digitalsmile.goldberry.media.platform.windows.WindowsBindings;

/// The system decoders' half of the metadata, for all three systems. None of it
/// needs any of them: linking a descriptor opens no library.
@DisplayName("PlatformDescriptors")
class PlatformDescriptorsTest {

    @Test
    @DisplayName("records every system's downcalls, each shape once")
    void everySystem() {
        var downcalls = PlatformDescriptors.downcalls();
        assertEquals(downcalls.size(), new HashSet<>(downcalls).size());
        for (var system : List.of(MacBindings.downcalls(), LinuxBindings.downcalls(), WindowsBindings.downcalls())) {
            assertFalse(system.isEmpty());
            assertTrue(downcalls.containsAll(system));
        }
    }

    @Test
    @DisplayName("records every system's callbacks as upcalls")
    void everyCallback() {
        var expected = new HashSet<FunctionDescriptor>();
        expected.addAll(MacBindings.UPCALLS);
        expected.addAll(LinuxBindings.UPCALLS);
        expected.addAll(WindowsBindings.UPCALLS);
        assertEquals(expected, new HashSet<>(PlatformDescriptors.UPCALLS));
    }
}
