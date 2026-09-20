package io.github.digitalsmile.goldberry.natives.desktop.calls;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import io.github.digitalsmile.goldberry.natives.desktop.MotionPreference;

/// Windows' own answer: `SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION)`.
///
/// The setting is **Show animations in Windows** under Ease of Access, and the
/// call is the one every framework reads it with — WinUI, Chromium and Qt all
/// ask this. `user32.dll` is loaded in every process that has a window, so the
/// lookup costs nothing new ([ADR-0383]).
///
/// The boolean is the *opposite* way round from every other name here: `TRUE`
/// means animations are on, so `FALSE` is what asks for less movement.
public final class WindowsMotion {

    /// `SPI_GETCLIENTAREAANIMATION`.
    private static final int GET_CLIENT_AREA_ANIMATION = 0x1042;

    private WindowsMotion() {}

    /// `BOOL SystemParametersInfoW(UINT, UINT, PVOID, UINT)`.
    ///
    /// Declared here rather than inside [#read()] so that it is recorded whether
    /// or not this machine is a Windows one (ADR-0451): `read()` runs on Windows
    /// alone, an image is built wherever it is built, and the metadata has to
    /// name every shape it might cross.
    private static final FunctionDescriptor FD = Bindings.describe(FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT));

    @SuppressWarnings("restricted")
    public static MotionPreference read() {
        try (var arena = Arena.ofConfined()) {
            var lookup = SymbolLookup.libraryLookup("user32.dll", arena);
            var call = Bindings.link(FD);
            var address = Bindings.symbol(lookup, "SystemParametersInfoW");
            var answer = arena.allocate(ValueLayout.JAVA_INT);
            answer.set(ValueLayout.JAVA_INT, 0, 1);
            var ok = Bindings.integer(call, address, GET_CLIENT_AREA_ANIMATION, 0, answer, 0);
            if (ok == 0) {
                return MotionPreference.UNKNOWN;
            }
            return answer.get(ValueLayout.JAVA_INT, 0) != 0 ? MotionPreference.FULL : MotionPreference.REDUCED;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return MotionPreference.UNKNOWN;
        }
    }
}
