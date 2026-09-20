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

    // The four Objective-C runtime shapes, declared here rather than inside
    // read() so that they are recorded whether or not this machine is a Mac
    // (ADR-0451). read() runs on macOS alone; an image is built wherever it is
    // built, and the metadata has to name every shape it might cross.

    /// `Class objc_getClass(const char *)` and `SEL sel_registerName(const char *)`.
    private static final FunctionDescriptor FD_PTR__PTR =
            Bindings.describe(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /// `id objc_msgSend(id, SEL)`.
    private static final FunctionDescriptor FD_PTR__PTR_PTR =
            Bindings.describe(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /// `BOOL objc_msgSend(id, SEL)`, where a `BOOL` is a signed char.
    private static final FunctionDescriptor FD_BYTE__PTR_PTR =
            Bindings.describe(FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    @SuppressWarnings("restricted")
    public static MotionPreference read() {
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
