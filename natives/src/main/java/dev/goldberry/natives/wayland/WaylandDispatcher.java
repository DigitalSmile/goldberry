package dev.goldberry.natives.wayland;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Upcalls;

/// Every event of every proxy the toolkit listens to, through one upcall.
///
/// `wl_proxy_add_listener` wants a table of function pointers, one per event
/// and each of its own C signature, which would be an upcall shape per event
/// of every interface. `wl_proxy_add_dispatcher` takes one function that hears
/// every event of a proxy as an opcode and an argument array instead, so the
/// whole of Wayland is one shape here. Proxies are told apart by their
/// address, which is what libwayland passes as the dispatcher's target.
///
/// The listener runs on the thread that dispatches the proxy's queue. It must
/// not throw into C, so a failure is logged and the event dropped.
public final class WaylandDispatcher {

    private static final Logger LOG = Logs.of(WaylandDispatcher.class);

    /// `int (const void *data, void *target, uint32_t opcode, const struct
    /// wl_message *message, union wl_argument *arguments)`.
    private static final FunctionDescriptor DESCRIPTOR =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    /// libwayland's `WL_CLOSURE_MAX_ARGS`: no event has more arguments.
    static final int MAX_ARGUMENTS = 20;

    private static final MethodHandle INVOKE = invokeHandle();

    private static final class Holder {
        private static final WaylandDispatcher INSTANCE = new WaylandDispatcher();
    }

    private final MemorySegment stub;
    private final Map<Long, WaylandListener> listeners = new ConcurrentHashMap<>();

    private WaylandDispatcher() {
        this.stub = upcallStub(INVOKE.bindTo(this));
    }

    /// The process's dispatcher, its stub made the first time it is asked for.
    public static WaylandDispatcher get() {
        return Holder.INSTANCE;
    }

    /// Sends `proxy`'s events to `listener` until [#forget].
    ///
    /// @throws IllegalStateException if the proxy already has a listener
    public void listen(WaylandClient client, MemorySegment proxy, WaylandListener listener) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(listener, "listener");
        if (proxy.address() == 0) {
            throw new IllegalArgumentException("a NULL proxy has no events");
        }
        listeners.put(proxy.address(), listener);
        if (client.addDispatcher(proxy, stub) != 0) {
            listeners.remove(proxy.address());
            throw new IllegalStateException("the proxy already has a listener");
        }
    }

    /// Stops listening to `proxy`, which is about to be destroyed. Its address
    /// may be a new proxy's tomorrow.
    public void forget(MemorySegment proxy) {
        listeners.remove(proxy.address());
    }

    /// How many proxies are listened to, for a test to see none is left.
    int listening() {
        return listeners.size();
    }

    /// Hands `opcode` and `arguments` to `proxy`'s listener. **Called from C;
    /// must not throw.**
    @SuppressWarnings("restricted")
    private int invoke(
            MemorySegment data, MemorySegment target, int opcode, MemorySegment message, MemorySegment arguments) {
        var listener = listeners.get(target.address());
        if (listener == null) {
            return 0;
        }
        try {
            var event = new WaylandEvent(
                    opcode,
                    arguments.address() == 0
                            ? MemorySegment.NULL
                            : arguments.reinterpret(MAX_ARGUMENTS * WaylandArguments.ARGUMENT_BYTES));
            listener.onEvent(event);
        } catch (Throwable t) {
            LOG.warn("a Wayland event listener failed on opcode {}; the event is dropped", opcode, t);
        }
        return 0;
    }

    // Restricted for SdlEventWatch's reason: the stub lives as long as the
    // process, since any proxy may still name it.
    @SuppressWarnings("restricted")
    private static MemorySegment upcallStub(MethodHandle target) {
        return Linker.nativeLinker().upcallStub(target, DESCRIPTOR, Arena.global());
    }

    private static MethodHandle invokeHandle() {
        try {
            return MethodHandles.lookup()
                    .findVirtual(
                            WaylandDispatcher.class,
                            "invoke",
                            MethodType.methodType(
                                    int.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    int.class,
                                    MemorySegment.class,
                                    MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
