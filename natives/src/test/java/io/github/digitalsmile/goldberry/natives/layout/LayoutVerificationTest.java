package io.github.digitalsmile.goldberry.natives.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.digitalsmile.goldberry.natives.GoldberryShim;
import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.NativePlatform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Verifies the hand-written layouts against the library that was actually
/// compiled for this machine.
///
/// This is the check ADR-0010 rests on. It needs a built `libgoldberry`, so it
/// skips — loudly, with a reason — when the superbuild has not run. It must run
/// on every target in CI: passing on Linux says nothing about Win64's 4-byte
/// `long` or aarch64's alignment rules. Which is why CI passes
/// `-Dgoldberry.native.required=true`, turning that skip into a failure
/// ([NativeLibraryRequirement], ADR-0016).
class LayoutVerificationTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("the library's ABI matches what these bindings were written against")
    void abiVersionMatches() {
        assertEquals(GoldberryShim.SUPPORTED_ABI_VERSION, GoldberryShim.get().abiVersion());
    }

    @Test
    @DisplayName("the layout table is readable, which proves the entry struct is modelled right")
    void layoutTableIsReadable() {
        var entries = LayoutProbe.read();

        assertFalse(entries.isEmpty(), "the layout table is empty");
    }

    @Test
    @DisplayName("every hand-written layout agrees with the compiled library")
    void handWrittenLayoutsAgreeWithC() {
        var platform = NativePlatform.current();
        var mismatches = LayoutVerifier.verify(platform, Layouts.registry(), LayoutProbe.read());

        assertEquals(
                java.util.List.of(),
                mismatches,
                () -> "hand-written layouts disagree with libgoldberry on " + platform.classifier()
                        + ":\n  " + String.join("\n  ", mismatches));
    }
}
