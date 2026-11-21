package dev.goldberry.natives.desktop.dragout;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.NativeLibrary;
import dev.goldberry.natives.Upcalls;
import dev.goldberry.natives.sdl.calls.SdlX11Calls;

/// Every X11 event SDL reads, shown to a drag source before SDL handles it.
///
/// `SDL_SetX11EventHook` holds one hook for the process, so this is one object
/// with a handler that can be swapped: [X11DragSource] installs one for the
/// length of a drag and takes it away after. Public for the native image's
/// upcall registry alone; nothing outside this package can use it. The handler runs on
/// the thread that pumps SDL's events, which is the UI thread, and answers
/// whether SDL should go on handling the event.
///
/// The shape is `bool (void*, XEvent*)`, the same as an event watch's, so it
/// adds no upcall shape of its own.
public final class X11EventHook {

    private static final Logger LOG = Logs.of(X11EventHook.class);

    private static final FunctionDescriptor DESCRIPTOR =
            Upcalls.describe(FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle INVOKE = invokeHandle();

    /// What sees an event.
    @FunctionalInterface
    interface Handler {

        /// `event` is the `XEvent` union, valid for this call only.
        ///
        /// @return false to keep SDL from handling the event, true to let it
        boolean onEvent(MemorySegment event);
    }

    private static @Nullable X11EventHook instance;

    private final SdlX11Calls calls;
    private final MemorySegment stub;
    private @Nullable Handler handler;

    private X11EventHook(SymbolLookup lookup) {
        this.calls = SdlX11Calls.bind(lookup);
        this.stub = upcallStub(INVOKE.bindTo(this));
    }

    /// The process's hook, made the first time it is asked for. It is not
    /// installed until a handler is set.
    static synchronized X11EventHook get() {
        if (instance == null) {
            instance = new X11EventHook(NativeLibrary.get().lookup());
        }
        return instance;
    }

    /// Installs `value` as the handler, replacing any other.
    void handle(Handler value) {
        handler = Objects.requireNonNull(value, "value");
        calls.setX11EventHook().call(stub, MemorySegment.NULL);
    }

    /// Takes `value` away if it is the handler, and the hook with it.
    void stop(Handler value) {
        if (handler != value) {
            return;
        }
        handler = null;
        calls.setX11EventHook().call(MemorySegment.NULL, MemorySegment.NULL);
    }

    /// `bool (void* userdata, XEvent* event)`. **Called from C; must not throw.**
    private boolean invoke(MemorySegment userdata, MemorySegment event) {
        var current = handler;
        if (current == null) {
            return true;
        }
        try {
            return current.onEvent(event);
        } catch (Throwable t) {
            LOG.warn("an X11 event hook failed; SDL handles the event", t);
            return true;
        }
    }

    // Restricted for SdlEventWatch's reason: a callback C calls is an upcall
    // stub, and the stub lives as long as the process because SDL keeps the
    // pointer until it is replaced.
    @SuppressWarnings("restricted")
    private static MemorySegment upcallStub(MethodHandle target) {
        return Linker.nativeLinker().upcallStub(target, DESCRIPTOR, Arena.global());
    }

    private static MethodHandle invokeHandle() {
        try {
            return MethodHandles.lookup()
                    .findVirtual(
                            X11EventHook.class,
                            "invoke",
                            MethodType.methodType(boolean.class, MemorySegment.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
