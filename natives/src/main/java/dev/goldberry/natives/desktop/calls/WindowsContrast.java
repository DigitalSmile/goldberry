package dev.goldberry.natives.desktop.calls;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import dev.goldberry.natives.desktop.DisplayAccessibility;
import dev.goldberry.natives.desktop.Setting;

/// Windows' high-contrast mode, through `SystemParametersInfoW`.
///
/// `SPI_GETHIGHCONTRAST` fills a `HIGHCONTRASTW`: a `cbSize`, a `dwFlags` whose
/// `HCF_HIGHCONTRASTON` bit is the switch, and a pointer to the scheme's name,
/// which is not read. The switch on is forced colours and more contrast both.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class WindowsContrast {

    private static final int GET_HIGH_CONTRAST = 0x0042;

    private static final int HIGH_CONTRAST_ON = 0x0001;

    /// `UINT cbSize; DWORD dwFlags; LPWSTR lpszDefaultScheme;` on 64 bits.
    private static final int STRUCT_BYTES = 16;

    private WindowsContrast() {}

    /// `BOOL SystemParametersInfoW(UINT, UINT, PVOID, UINT)`, the shape
    /// [WindowsMotion] calls too.
    private static final FunctionDescriptor FD = Bindings.describe(FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT));

    @SuppressWarnings("restricted")
    public static DisplayAccessibility read() {
        try (var arena = Arena.ofConfined()) {
            var lookup = SymbolLookup.libraryLookup("user32.dll", arena);
            var call = Bindings.link(FD);
            var address = Bindings.symbol(lookup, "SystemParametersInfoW");
            var contrast = arena.allocate(STRUCT_BYTES);
            contrast.fill((byte) 0);
            contrast.set(ValueLayout.JAVA_INT, 0, STRUCT_BYTES);
            var ok = Bindings.integer(call, address, GET_HIGH_CONTRAST, STRUCT_BYTES, contrast, 0);
            if (ok == 0) {
                return DisplayAccessibility.UNKNOWN;
            }
            var on = (contrast.get(ValueLayout.JAVA_INT, 4) & HIGH_CONTRAST_ON) != 0 ? Setting.ON : Setting.OFF;
            return new DisplayAccessibility(on, on, Setting.UNKNOWN);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return DisplayAccessibility.UNKNOWN;
        }
    }
}
