package dev.goldberry.natives.desktop.calls;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.desktop.DisplayAccessibility;
import dev.goldberry.natives.desktop.Setting;

/// macOS's increase-contrast and reduce-transparency switches, as `NSWorkspace`
/// properties, through the Objective-C runtime.
///
/// The same four runtime calls [MacMotion] makes, for two other properties.
/// macOS has no forced colours, so that answers [Setting#UNKNOWN].
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class MacAccessibility {

    private MacAccessibility() {}

    private static final FunctionDescriptor FD_PTR__PTR =
            Bindings.describe(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final FunctionDescriptor FD_PTR__PTR_PTR =
            Bindings.describe(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final FunctionDescriptor FD_BYTE__PTR_PTR =
            Bindings.describe(FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    @SuppressWarnings("restricted")
    public static DisplayAccessibility read() {
        try (var arena = Arena.ofConfined()) {
            var objc = SymbolLookup.libraryLookup("libobjc.A.dylib", arena);
            var getClass = Bindings.link(FD_PTR__PTR);
            var registerName = Bindings.link(FD_PTR__PTR);
            var send = Bindings.link(FD_PTR__PTR_PTR);
            var sendBoolean = Bindings.link(FD_BYTE__PTR_PTR);
            var getClassAt = Bindings.symbol(objc, "objc_getClass");
            var registerNameAt = Bindings.symbol(objc, "sel_registerName");
            var sendAt = Bindings.symbol(objc, "objc_msgSend");
            var workspaceClass = Bindings.address(getClass, getClassAt, arena.allocateFrom("NSWorkspace"));
            if (workspaceClass.address() == 0) {
                return DisplayAccessibility.UNKNOWN;
            }
            var shared = Bindings.address(registerName, registerNameAt, arena.allocateFrom("sharedWorkspace"));
            var workspace = Bindings.address(send, sendAt, workspaceClass, shared);
            if (workspace.address() == 0) {
                return DisplayAccessibility.UNKNOWN;
            }
            var contrast = flag(
                    arena,
                    registerName,
                    registerNameAt,
                    sendBoolean,
                    sendAt,
                    workspace,
                    "accessibilityDisplayShouldIncreaseContrast");
            var transparency = flag(
                    arena,
                    registerName,
                    registerNameAt,
                    sendBoolean,
                    sendAt,
                    workspace,
                    "accessibilityDisplayShouldReduceTransparency");
            return new DisplayAccessibility(contrast, Setting.UNKNOWN, transparency);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return DisplayAccessibility.UNKNOWN;
        }
    }

    private static Setting flag(
            Arena arena,
            MethodHandle registerName,
            MemorySegment registerNameAt,
            MethodHandle sendBoolean,
            MemorySegment sendAt,
            MemorySegment workspace,
            String property) {
        var selector = Bindings.address(registerName, registerNameAt, arena.allocateFrom(property));
        try {
            var on = (byte) sendBoolean.invokeWithArguments(sendAt, workspace, selector);
            return on != 0 ? Setting.ON : Setting.OFF;
        } catch (Throwable e) {
            return Setting.UNKNOWN;
        }
    }
}
