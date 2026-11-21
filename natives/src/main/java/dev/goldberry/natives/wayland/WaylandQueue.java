package dev.goldberry.natives.wayland;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// An event queue of the toolkit's own on a connection it does not own:
/// SDL's.
///
/// SDL reads the socket and dispatches its default queue in its own pump.
/// Events for proxies made here land on this queue instead, and wait there
/// until [#dispatch] runs them, so nothing of the toolkit's runs inside SDL's
/// pump, and SDL never sees an event it did not ask for. The registry is this
/// queue's own too, which is what puts every global bound through it here.
///
/// On the UI thread only, like every other call into SDL's connection.
public final class WaylandQueue implements AutoCloseable {

    private static final Logger LOG = Logs.of(WaylandQueue.class);

    private static final int DISPLAY_GET_REGISTRY = 1;
    private static final int REGISTRY_BIND = 0;
    private static final int REGISTRY_GLOBAL = 0;
    private static final int REGISTRY_GLOBAL_REMOVE = 1;

    /// A global the compositor announced.
    ///
    /// @param name      its numeric name in the registry
    /// @param interfaceName the interface, `wl_data_device_manager` say
    /// @param version   the highest version the compositor speaks
    public record Global(int name, String interfaceName, int version) {

        public Global {
            Objects.requireNonNull(interfaceName, "interfaceName");
        }
    }

    private final WaylandClient client;
    private final MemorySegment display;
    private final MemorySegment queue;
    private final Map<String, Global> globals = new LinkedHashMap<>();
    private MemorySegment registry = MemorySegment.NULL;
    private boolean closed;

    private WaylandQueue(WaylandClient client, MemorySegment display, MemorySegment queue) {
        this.client = client;
        this.display = display;
        this.queue = queue;
    }

    /// A queue on `display`, its registry read to the end, or empty where the
    /// connection has failed.
    public static Optional<WaylandQueue> open(WaylandClient client, MemorySegment display) {
        Objects.requireNonNull(client, "client");
        if (display.address() == 0) {
            return Optional.empty();
        }
        var queue = client.createQueue(display);
        if (queue.address() == 0) {
            return Optional.empty();
        }
        var opened = new WaylandQueue(client, display, queue);
        try {
            opened.readRegistry();
        } catch (RuntimeException e) {
            opened.close();
            throw e;
        }
        if (client.error(display) != 0) {
            LOG.debug("the Wayland connection has failed (error {}); no queue on it", client.error(display));
            opened.close();
            return Optional.empty();
        }
        return Optional.of(opened);
    }

    /// The registry, made through a wrapper of the display that is on this
    /// queue, so that it and everything bound through it are this queue's.
    private void readRegistry() {
        var wrapper = client.createWrapper(display);
        try {
            client.setQueue(wrapper, queue);
            registry = create(wrapper, DISPLAY_GET_REGISTRY, "wl_registry_interface", 1, 1, WaylandArguments::newId);
        } finally {
            client.destroyWrapper(wrapper);
        }
        WaylandDispatcher.get().listen(client, registry, this::onRegistry);
        client.roundtrip(display, queue);
    }

    private void onRegistry(WaylandEvent event) {
        switch (event.opcode()) {
            case REGISTRY_GLOBAL -> {
                var name = event.string(1);
                if (name != null) {
                    globals.putIfAbsent(name, new Global(event.integer(0), name, event.integer(2)));
                }
            }
            case REGISTRY_GLOBAL_REMOVE -> {
                var name = event.integer(0);
                globals.values().removeIf(global -> global.name() == name);
            }
            default -> {
                // No other registry events exist.
            }
        }
    }

    /// The library this queue speaks through.
    public WaylandClient client() {
        return client;
    }

    /// The global announced for `interfaceName`, or empty.
    public Optional<Global> global(String interfaceName) {
        return Optional.ofNullable(globals.get(interfaceName));
    }

    /// Binds `global` at `version`, which the caller has already capped at the
    /// global's own.
    ///
    /// @param interfaceSymbol the exported table, `wl_shm_interface` say
    public MemorySegment bind(Global global, String interfaceSymbol, int version) {
        Objects.requireNonNull(global, "global");
        if (version < 1 || version > global.version()) {
            throw new IllegalArgumentException(
                    global.interfaceName() + " is at version " + global.version() + ", not " + version);
        }
        return create(
                registry,
                REGISTRY_BIND,
                interfaceSymbol,
                version,
                4,
                arguments -> arguments
                        .unsigned(global.name())
                        .string(global.interfaceName())
                        .unsigned(version)
                        .newId());
    }

    /// Sends request `opcode` on `proxy`, which makes a new object of
    /// `interfaceSymbol` at `proxy`'s own version.
    public MemorySegment create(
            MemorySegment proxy, int opcode, String interfaceSymbol, int count, Consumer<WaylandArguments> fill) {
        return create(proxy, opcode, interfaceSymbol, client.version(proxy), count, fill);
    }

    private MemorySegment create(
            MemorySegment proxy,
            int opcode,
            String interfaceSymbol,
            int version,
            int count,
            Consumer<WaylandArguments> fill) {
        try (var arena = Arena.ofConfined()) {
            var arguments = new WaylandArguments(arena, count);
            fill.accept(arguments);
            var made =
                    client.marshal(proxy, opcode, client.interfaceOf(interfaceSymbol), version, 0, arguments.segment());
            if (made.address() == 0) {
                throw new IllegalStateException("libwayland made no " + interfaceSymbol + " (opcode " + opcode + ")");
            }
            return made;
        }
    }

    /// Sends request `opcode` on `proxy`, which makes nothing.
    public void send(MemorySegment proxy, int opcode, int count, Consumer<WaylandArguments> fill) {
        try (var arena = Arena.ofConfined()) {
            var arguments = new WaylandArguments(arena, count);
            fill.accept(arguments);
            client.marshal(proxy, opcode, MemorySegment.NULL, client.version(proxy), 0, arguments.segment());
        }
    }

    /// Sends `proxy`'s destructor request `opcode`, which takes no arguments,
    /// and destroys the proxy with it.
    public void destroy(MemorySegment proxy, int opcode) {
        WaylandDispatcher.get().forget(proxy);
        try (var arena = Arena.ofConfined()) {
            var arguments = new WaylandArguments(arena, 0);
            client.marshal(
                    proxy,
                    opcode,
                    MemorySegment.NULL,
                    client.version(proxy),
                    WaylandClient.MARSHAL_DESTROY,
                    arguments.segment());
        }
    }

    /// Destroys `proxy` on this side only, for an interface with no destructor
    /// request.
    public void forget(MemorySegment proxy) {
        WaylandDispatcher.get().forget(proxy);
        client.destroy(proxy);
    }

    /// Runs the events already read for this queue.
    ///
    /// @return false once the connection has failed
    public boolean dispatch() {
        return !closed && client.dispatchPending(display, queue) >= 0;
    }

    /// Sends what is buffered.
    public void flush() {
        if (!closed) {
            client.flush(display);
        }
    }

    /// Blocks until the compositor has answered everything sent, running this
    /// queue's events meanwhile.
    public void roundtrip() {
        if (!closed) {
            client.roundtrip(display, queue);
        }
    }

    /// The connection's error, 0 while it works.
    public int error() {
        return client.error(display);
    }

    /// Destroys the registry and the queue. Every other proxy made on it must
    /// already be destroyed.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (registry.address() != 0) {
            WaylandDispatcher.get().forget(registry);
            client.destroy(registry);
            registry = MemorySegment.NULL;
        }
        client.destroyQueue(queue);
    }
}
