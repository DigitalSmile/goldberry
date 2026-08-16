package io.github.digitalsmile.goldberry.natives.sdl;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.log.Logs;
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
import org.slf4j.Logger;

/// A callback SDL runs as each event arrives, before it is queued.
///
/// This exists for one reason: **Windows and macOS run a modal loop while a
/// window is being resized.** The platform takes the thread and does not give it
/// back until the drag ends, so `SDL_WaitEventTimeout` does not return, the frame
/// loop does not iterate, and the window shows stale or blank content for as long
/// as the user is dragging. Wayland and X11 have no such loop and are prompt
/// (ADR-0024).
///
/// SDL still pumps events inside that modal loop, and an event watch is called
/// from inside the pump. A watch is therefore the one place a frame can be drawn
/// while the platform is holding the thread — which is why the fix is a callback
/// rather than a change to the loop (ADR-0060).
///
/// ## What a handler may assume
///
/// **Almost nothing.** The callback is not a queued event; it runs on whichever
/// thread pushed the event, at whatever depth of platform code that thread is at:
/// inside `SDL_PushEvent` for a synthetic one, inside the platform's own resize
/// loop for the case this was built for. A handler must check that it is on the
/// thread it belongs to before it touches anything — [SdlVideo#pushWakeup()] is
/// called from background threads, and it produces an event like any other.
///
/// The [SdlEventBuffer] handed over is a **borrowed view of SDL's own memory**,
/// valid for the duration of the call and no longer. Read from it; do not keep it.
///
/// ## Failure
///
/// An exception escaping an upcall into native code takes the process with it, so
/// anything the handler throws is caught here and logged. The alternative is
/// losing the window because a repaint failed during a drag.
public final class SdlEventWatch implements AutoCloseable {

    private static final Logger LOG = Logs.of(SdlEventWatch.class);

    private static final Linker LINKER = Linker.nativeLinker();

    /// ```c
    /// typedef bool (SDLCALL *SDL_EventFilter)(void *userdata, SDL_Event *event);
    /// ```
    ///
    /// The return value is ignored for a watch — it is the *filter* API that uses
    /// it to drop events — so this always returns true.
    private static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final MethodHandle INVOKE = invokeHandle();

    /// What an event watch is handed.
    @FunctionalInterface
    public interface Handler {

        /// Called as `event` arrives, before it reaches the queue.
        ///
        /// @param event a borrowed view, valid only for this call
        void onEvent(SdlEventBuffer event);
    }

    private final Handler handler;
    private final MethodHandle addEventWatch;
    private final MethodHandle removeEventWatch;
    private final Arena arena;
    private final MemorySegment stub;

    private boolean closed;

    /// Installs a watch. Close it to remove it.
    ///
    /// @throws UnsatisfiedLinkError if `libgoldberry` was built without the event
    ///         watch symbols — an older artifact, which the backend treats as
    ///         "no live resize" rather than as a failure to open a window
    public static SdlEventWatch install(Handler handler) {
        return new SdlEventWatch(
                NativeLibrary.get().lookup(), Objects.requireNonNull(handler, "handler"));
    }

    SdlEventWatch(SymbolLookup lookup, Handler handler) {
        this.handler = handler;
        this.addEventWatch = downcall(lookup, "SDL_AddEventWatch",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.removeEventWatch = downcall(lookup, "SDL_RemoveEventWatch",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // Shared rather than confined, because the stub is not called on one
        // thread: SDL runs a watch on whichever thread pushed the event, and a
        // confined arena's stub invoked from another thread is a failed crossing
        // rather than a handler that declines.
        this.arena = Arena.ofShared();
        try {
            this.stub = upcallStub(INVOKE.bindTo(this), arena);
            if (!(boolean) invoke(addEventWatch, "SDL_AddEventWatch", stub, MemorySegment.NULL)) {
                throw new SdlException("SDL_AddEventWatch", Sdl.get().lastError());
            }
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// Removes the watch and releases the stub. Idempotent.
    ///
    /// The order matters and is the whole content of this method: SDL must be
    /// told to stop calling the stub *before* the memory holding it goes away.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        invoke(removeEventWatch, "SDL_RemoveEventWatch", stub, MemorySegment.NULL);
        arena.close();
    }

    /// The upcall target. Called by native code, so it must not throw.
    private boolean invoke(MemorySegment userdata, MemorySegment event) {
        try {
            handler.onEvent(SdlEventBuffer.borrowing(event));
        } catch (Throwable t) {
            // Logged rather than held for later, unlike MeasureCallback's
            // failures: there is no "after the call" for a watch — the next thing
            // to happen may be the platform's modal loop, not Java.
            LOG.warn("an event watch failed", t);
        }
        // A watch's return value is ignored, but returning false through the
        // filter API would drop the event, so this says yes deliberately.
        return true;
    }

    private static Object invoke(MethodHandle handle, String name, Object... args) {
        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    // Restricted: see MeasureCallback -- same obligation, and a far simpler
    // descriptor, since nothing here is passed or returned by value.
    @SuppressWarnings("restricted")
    private static MemorySegment upcallStub(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, DESCRIPTOR, arena);
    }

    // Restricted: see GoldberryShim.downcall -- same obligation, same reason.
    @SuppressWarnings("restricted")
    private static MethodHandle downcall(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        var address = lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(
                "libgoldberry does not export " + symbol
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
        return LINKER.downcallHandle(address, descriptor);
    }

    private static MethodHandle invokeHandle() {
        try {
            return MethodHandles.lookup().findVirtual(
                    SdlEventWatch.class,
                    "invoke",
                    MethodType.methodType(boolean.class, MemorySegment.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
