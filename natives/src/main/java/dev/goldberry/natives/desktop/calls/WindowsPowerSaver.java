package dev.goldberry.natives.desktop.calls;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

/// Whether Windows' battery saver is on: `GetSystemPowerStatus` in
/// `kernel32.dll`, a library every Windows process already has.
///
/// `SYSTEM_POWER_STATUS` is four bytes and two `DWORD`s, and its fourth byte,
/// `SystemStatusFlag`, is 1 while battery saver is on.
public final class WindowsPowerSaver {

    /// `BOOL GetSystemPowerStatus(LPSYSTEM_POWER_STATUS)`, declared here so it
    /// is recorded wherever an image is built.
    private static final FunctionDescriptor FD =
            Bindings.describe(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    /// `sizeof(SYSTEM_POWER_STATUS)`.
    private static final long STATUS_SIZE = 12;

    /// The offset of `SystemStatusFlag`.
    private static final long SYSTEM_STATUS_FLAG = 3;

    private WindowsPowerSaver() {}

    /// Whether battery saver is on, or empty where the call failed.
    @SuppressWarnings("restricted")
    public static Optional<Boolean> read() {
        try (var arena = Arena.ofConfined()) {
            var lookup = SymbolLookup.libraryLookup("kernel32.dll", arena);
            var call = Bindings.link(FD);
            var address = Bindings.symbol(lookup, "GetSystemPowerStatus");
            var status = arena.allocate(STATUS_SIZE, 4);
            if (Bindings.integer(call, address, status) == 0) {
                return Optional.empty();
            }
            return Optional.of(status.get(ValueLayout.JAVA_BYTE, SYSTEM_STATUS_FLAG) == 1);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }
}
