package io.github.digitalsmile.goldberry.natives.desktop.calls;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import io.github.digitalsmile.goldberry.natives.desktop.MotionPreference;

/// macOS' own answer: `NSWorkspace.accessibilityDisplayShouldReduceMotion`.
///
/// The setting is **Reduce motion** under Accessibility → Display, and this
/// property is the documented way to read it. There is no C function for it, so
/// the call goes through the Objective-C runtime — `objc_getClass`,
/// `sel_registerName`, `objc_msgSend` — which is three symbols out of
/// `libobjc.A.dylib` and no framework linkage at all ([ADR-0383]).
///
/// `objc_msgSend` is variadic in its declaration and is **not** called
/// variadically here: each of the two messages this sends takes no arguments
/// beyond the receiver and the selector, which is the fixed part of the
/// signature. A message with arguments would need a descriptor per shape, which
/// is what an Objective-C bridge is and is not what this is.
public final class MacMotion {

    private MacMotion() {}

    @SuppressWarnings("restricted")
    public static MotionPreference read() {
        try (var arena = Arena.ofConfined()) {
            var objc = SymbolLookup.libraryLookup("libobjc.A.dylib", arena);
            var getClass = Bindings.link(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            var registerName = Bindings.link(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            var send =
                    Bindings.link(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            var sendBoolean = Bindings.link(
                    FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            var getClassAt = Bindings.symbol(objc, "objc_getClass");
            var registerNameAt = Bindings.symbol(objc, "sel_registerName");
            var sendAt = Bindings.symbol(objc, "objc_msgSend");

            var workspaceClass = Bindings.address(getClass, getClassAt, arena.allocateFrom("NSWorkspace"));
            if (workspaceClass.address() == 0) {
                return MotionPreference.UNKNOWN;
            }
            var shared = Bindings.address(registerName, registerNameAt, arena.allocateFrom("sharedWorkspace"));
            var workspace = Bindings.address(send, sendAt, workspaceClass, shared);
            if (workspace.address() == 0) {
                return MotionPreference.UNKNOWN;
            }
            var property = Bindings.address(
                    registerName, registerNameAt, arena.allocateFrom("accessibilityDisplayShouldReduceMotion"));
            var reduce = (byte) callBoolean(sendBoolean, sendAt, workspace, property);
            return reduce != 0 ? MotionPreference.REDUCED : MotionPreference.FULL;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return MotionPreference.UNKNOWN;
        }
    }

    /// `objc_msgSend` returning a `BOOL`, which is a signed char.
    private static int callBoolean(
            java.lang.invoke.MethodHandle handle,
            java.lang.foreign.MemorySegment function,
            java.lang.foreign.MemorySegment receiver,
            java.lang.foreign.MemorySegment selector) {
        try {
            return (byte) handle.invokeWithArguments(function, receiver, selector);
        } catch (Throwable e) {
            throw new IllegalStateException("a desktop call failed", e);
        }
    }
}
