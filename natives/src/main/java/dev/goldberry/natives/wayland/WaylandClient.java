package dev.goldberry.natives.wayland;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Downcalls;

/// libwayland-client, the part of it a client of SDL's own connection needs.
///
/// Opened by its soname, so that it is the instance SDL already loaded with
/// `dlopen`: two copies of libwayland in one process would each keep their own
/// idea of the connection. Every request goes through
/// `wl_proxy_marshal_array_flags` (libwayland 1.20 on), the one marshalling
/// call that is not variadic, with its arguments as a `union wl_argument`
/// array ([WaylandArguments]). The generated `wl_*_add_listener` and request
/// functions are `static inline` in the protocol headers and exist in no
/// library, which is why the opcodes are spelled out where they are used.
///
/// Events reach Java through `wl_proxy_add_dispatcher` and one upcall of one
/// shape for every interface ([WaylandDispatcher]).
public final class WaylandClient {

    private static final Logger LOG = Logs.of(WaylandClient.class);

    /// The soname SDL opens.
    public static final String SONAME = "libwayland-client.so.0";

    /// `WL_MARSHAL_FLAG_DESTROY`: the request destroys the proxy it is sent on.
    public static final int MARSHAL_DESTROY = 1;

    private static final FunctionDescriptor P_P = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final FunctionDescriptor V_P = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS));
    private static final FunctionDescriptor V_PP = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final FunctionDescriptor I_P = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final FunctionDescriptor I_PP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final FunctionDescriptor I_PPPP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor MARSHAL =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

    private static final class Holder {
        private static final @Nullable WaylandClient INSTANCE = find();
    }

    private final SymbolLookup lookup;
    private final MethodHandle displayConnect;
    private final MethodHandle displayDisconnect;
    private final MethodHandle displayCreateQueue;
    private final MethodHandle eventQueueDestroy;
    private final MethodHandle displayDispatchQueuePending;
    private final MethodHandle displayRoundtripQueue;
    private final MethodHandle displayFlush;
    private final MethodHandle displayGetError;
    private final MethodHandle proxyCreateWrapper;
    private final MethodHandle proxyWrapperDestroy;
    private final MethodHandle proxySetQueue;
    private final MethodHandle proxyMarshalArrayFlags;
    private final MethodHandle proxyAddDispatcher;
    private final MethodHandle proxyDestroy;
    private final MethodHandle proxyGetVersion;
    private final Map<String, MemorySegment> interfaces = new ConcurrentHashMap<>();

    private WaylandClient(SymbolLookup lookup) {
        this.lookup = lookup;
        this.displayConnect = bind(lookup, "wl_display_connect", P_P);
        this.displayDisconnect = bind(lookup, "wl_display_disconnect", V_P);
        this.displayCreateQueue = bind(lookup, "wl_display_create_queue", P_P);
        this.eventQueueDestroy = bind(lookup, "wl_event_queue_destroy", V_P);
        this.displayDispatchQueuePending = bind(lookup, "wl_display_dispatch_queue_pending", I_PP);
        this.displayRoundtripQueue = bind(lookup, "wl_display_roundtrip_queue", I_PP);
        this.displayFlush = bind(lookup, "wl_display_flush", I_P);
        this.displayGetError = bind(lookup, "wl_display_get_error", I_P);
        this.proxyCreateWrapper = bind(lookup, "wl_proxy_create_wrapper", P_P);
        this.proxyWrapperDestroy = bind(lookup, "wl_proxy_wrapper_destroy", V_P);
        this.proxySetQueue = bind(lookup, "wl_proxy_set_queue", V_PP);
        this.proxyMarshalArrayFlags = bind(lookup, "wl_proxy_marshal_array_flags", MARSHAL);
        this.proxyAddDispatcher = bind(lookup, "wl_proxy_add_dispatcher", I_PPPP);
        this.proxyDestroy = bind(lookup, "wl_proxy_destroy", V_P);
        this.proxyGetVersion = bind(lookup, "wl_proxy_get_version", I_P);
    }

    /// The library, or empty where it will not load or is older than 1.20.
    public static Optional<WaylandClient> get() {
        return Optional.ofNullable(Holder.INSTANCE);
    }

    // Restricted: opening a system library is what this module exists to do.
    @SuppressWarnings("restricted")
    private static @Nullable WaylandClient find() {
        try {
            return new WaylandClient(SymbolLookup.libraryLookup(SONAME, Arena.global()));
        } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
            LOG.debug("no usable {} ({}); no drag out on Wayland", SONAME, e.getMessage());
            return null;
        }
    }

    private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
        var address =
                lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError(SONAME + " does not export " + name));
        return Downcalls.link(shape).bindTo(address);
    }

    /// The `wl_interface` table libwayland-client exports as `name`, such as
    /// `wl_data_source_interface`.
    ///
    /// @throws IllegalArgumentException if the library has no such table
    public MemorySegment interfaceOf(String name) {
        return interfaces.computeIfAbsent(
                name,
                key -> lookup.find(key).orElseThrow(() -> new IllegalArgumentException(SONAME + " has no " + key)));
    }

    /// `wl_display_connect`: a connection of its own, for tests and nothing
    /// else; the toolkit speaks on SDL's.
    ///
    /// @param name the socket, or null for `$WAYLAND_DISPLAY`
    /// @return the display, or `NULL` where no compositor answers
    public MemorySegment connect(@Nullable String name) {
        try (var arena = Arena.ofConfined()) {
            var socket = name == null ? MemorySegment.NULL : arena.allocateFrom(name);
            return (MemorySegment) displayConnect.invokeExact(socket);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_display_connect", t);
        }
    }

    /// `wl_display_disconnect`.
    public void disconnect(MemorySegment display) {
        try {
            displayDisconnect.invokeExact(display);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_display_disconnect", t);
        }
    }

    /// `wl_display_create_queue`.
    public MemorySegment createQueue(MemorySegment display) {
        try {
            return (MemorySegment) displayCreateQueue.invokeExact(display);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_display_create_queue", t);
        }
    }

    /// `wl_event_queue_destroy`.
    public void destroyQueue(MemorySegment queue) {
        try {
            eventQueueDestroy.invokeExact(queue);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_event_queue_destroy", t);
        }
    }

    /// `wl_display_dispatch_queue_pending`: runs what is already read for
    /// `queue`, without reading or blocking.
    ///
    /// @return the number of events dispatched, or -1 on a connection error
    public int dispatchPending(MemorySegment display, MemorySegment queue) {
        try {
            return (int) displayDispatchQueuePending.invokeExact(display, queue);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_display_dispatch_queue_pending", t);
        }
    }

    /// `wl_display_roundtrip_queue`: blocks until the compositor has answered
    /// everything sent so far, dispatching `queue` meanwhile.
    ///
    /// @return the number of events dispatched, or -1 on a connection error
    public int roundtrip(MemorySegment display, MemorySegment queue) {
        try {
            return (int) displayRoundtripQueue.invokeExact(display, queue);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_display_roundtrip_queue", t);
        }
    }

    /// `wl_display_flush`.
    public int flush(MemorySegment display) {
        try {
            return (int) displayFlush.invokeExact(display);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_display_flush", t);
        }
    }

    /// `wl_display_get_error`: the last error on the connection, 0 for none.
    public int error(MemorySegment display) {
        try {
            return (int) displayGetError.invokeExact(display);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_display_get_error", t);
        }
    }

    /// `wl_proxy_create_wrapper`.
    public MemorySegment createWrapper(MemorySegment proxy) {
        try {
            return (MemorySegment) proxyCreateWrapper.invokeExact(proxy);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_proxy_create_wrapper", t);
        }
    }

    /// `wl_proxy_wrapper_destroy`.
    public void destroyWrapper(MemorySegment wrapper) {
        try {
            proxyWrapperDestroy.invokeExact(wrapper);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_proxy_wrapper_destroy", t);
        }
    }

    /// `wl_proxy_set_queue`.
    public void setQueue(MemorySegment proxy, MemorySegment queue) {
        try {
            proxySetQueue.invokeExact(proxy, queue);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_proxy_set_queue", t);
        }
    }

    /// `wl_proxy_marshal_array_flags`: sends request `opcode` on `proxy`.
    ///
    /// @param newInterface the interface of the object the request makes, or
    ///                     `NULL` for a request that makes none
    /// @param version      the version of the object it makes
    /// @param flags        0, or [#MARSHAL_DESTROY]
    /// @param arguments    the request's arguments, a `new_id` among them left
    ///                     `NULL`
    /// @return the new proxy, or `NULL` for a request that makes none
    public MemorySegment marshal(
            MemorySegment proxy,
            int opcode,
            MemorySegment newInterface,
            int version,
            int flags,
            MemorySegment arguments) {
        try {
            return (MemorySegment)
                    proxyMarshalArrayFlags.invokeExact(proxy, opcode, newInterface, version, flags, arguments);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_proxy_marshal_array_flags", t);
        }
    }

    /// `wl_proxy_add_dispatcher`.
    ///
    /// @return 0, or -1 where the proxy already has a listener
    public int addDispatcher(MemorySegment proxy, MemorySegment dispatcher) {
        try {
            return (int) proxyAddDispatcher.invokeExact(proxy, dispatcher, MemorySegment.NULL, MemorySegment.NULL);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_proxy_add_dispatcher", t);
        }
    }

    /// `wl_proxy_destroy`, for an object whose interface has no destructor
    /// request.
    public void destroy(MemorySegment proxy) {
        try {
            proxyDestroy.invokeExact(proxy);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_proxy_destroy", t);
        }
    }

    /// `wl_proxy_get_version`.
    public int version(MemorySegment proxy) {
        try {
            return (int) proxyGetVersion.invokeExact(proxy);
        } catch (Throwable t) {
            throw Downcalls.failure("wl_proxy_get_version", t);
        }
    }
}
