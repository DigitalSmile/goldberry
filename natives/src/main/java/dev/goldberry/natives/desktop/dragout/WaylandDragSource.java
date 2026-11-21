package dev.goldberry.natives.desktop.dragout;

import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.sdl.window.WaylandDragOrigin;
import dev.goldberry.natives.wayland.Descriptors;
import dev.goldberry.natives.wayland.WaylandArguments;
import dev.goldberry.natives.wayland.WaylandClient;
import dev.goldberry.natives.wayland.WaylandDispatcher;
import dev.goldberry.natives.wayland.WaylandQueue;

/// A drag out on Wayland: a `wl_data_source` of the toolkit's own, dragged
/// with SDL's `wl_data_device` from SDL's surface.
///
/// The source, the icon and the globals they need are made on a
/// [WaylandQueue] of the drag's own on SDL's connection, so their events wait
/// until [#tick] dispatches them, on the UI thread, outside SDL's pump.
/// `start_drag` is sent on SDL's own data device, with the serial SDL kept
/// from the press (see [WaylandDragOrigin]): a second data device of the
/// toolkit's would take over the client's drops and clipboard on mutter.
///
/// A target's request for the data is answered on a thread of the drag's own,
/// since a pipe blocks until it is read: the bytes are made on the UI thread,
/// as [DragData] promises, and written there.
///
/// The compositor needs version 3 of `wl_data_device_manager`, which says
/// when a drop happened and when the target is done with it. An older one is
/// refused, and the drag stays inside the application.
public final class WaylandDragSource implements AutoCloseable {

    private static final Logger LOG = Logs.of(WaylandDragSource.class);

    /// How long a target has to finish with a drop before the drag is taken
    /// as delivered anyway.
    public static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(5);

    /// The data device manager's version this needs and binds.
    static final int MANAGER_VERSION = 3;

    private static final int MANAGER_CREATE_DATA_SOURCE = 0;
    private static final int SOURCE_OFFER = 0;
    private static final int SOURCE_DESTROY = 1;
    private static final int SOURCE_SET_ACTIONS = 2;
    private static final int DEVICE_START_DRAG = 0;

    private final WaylandQueue queue;
    private final MemorySegment manager;
    private final MemorySegment source;
    private final @Nullable WaylandIcon icon;
    private final DragData data;
    private final WaylandDragListener listener;
    private final WaylandSourceEvents events = new WaylandSourceEvents();
    private final ExecutorService writer;
    private long droppedAt;
    private boolean ended;
    private boolean closed;

    private WaylandDragSource(
            WaylandQueue queue,
            MemorySegment manager,
            MemorySegment source,
            @Nullable WaylandIcon icon,
            DragData data,
            WaylandDragListener listener) {
        this.queue = queue;
        this.manager = manager;
        this.source = source;
        this.icon = icon;
        this.data = data;
        this.listener = listener;
        this.writer = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon().name("goldberry-drag-send").factory());
        // First, so that the one thread every write runs on has SIGPIPE
        // blocked before it writes anything.
        writer.execute(Descriptors::ignoreBrokenPipes);
    }

    /// Starts a drag from `origin`, or answers empty where libwayland will not
    /// load or the compositor's data device manager is older than version 3.
    ///
    /// @param origin   SDL's display, surface, data device and serial
    /// @param data     what the drag carries
    /// @param actions  what a target may do with it
    /// @param picture  what follows the pointer, or null for the compositor's
    ///                 default
    /// @param listener told what the compositor says, once [#tick] reads it
    public static Optional<WaylandDragSource> start(
            WaylandDragOrigin origin,
            DragData data,
            Set<DragAction> actions,
            @Nullable DragIcon picture,
            WaylandDragListener listener) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(listener, "listener");
        var client = WaylandClient.get();
        if (client.isEmpty() || data.types().isEmpty()) {
            return Optional.empty();
        }
        var opened = WaylandQueue.open(client.get(), MemorySegment.ofAddress(origin.display()));
        if (opened.isEmpty()) {
            return Optional.empty();
        }
        var queue = opened.get();
        var global = queue.global("wl_data_device_manager");
        if (global.isEmpty() || global.get().version() < MANAGER_VERSION) {
            LOG.debug(
                    "the compositor's wl_data_device_manager is {}; a drag out needs version {}",
                    global.map(found -> "version " + found.version()).orElse("missing"),
                    MANAGER_VERSION);
            queue.close();
            return Optional.empty();
        }
        var manager = queue.bind(global.get(), "wl_data_device_manager_interface", MANAGER_VERSION);
        var source = queue.create(
                manager, MANAGER_CREATE_DATA_SOURCE, "wl_data_source_interface", 1, WaylandArguments::newId);
        var icon = picture == null ? null : WaylandIcon.make(queue, picture).orElse(null);
        var drag = new WaylandDragSource(queue, manager, source, icon, data, listener);
        try {
            drag.offer(actions);
            drag.startDrag(origin);
        } catch (RuntimeException e) {
            drag.close();
            throw e;
        }
        if (queue.error() != 0) {
            LOG.warn("the compositor refused a drag out (connection error {})", queue.error());
            drag.close();
            return Optional.empty();
        }
        return Optional.of(drag);
    }

    private void offer(Set<DragAction> actions) {
        WaylandDispatcher.get().listen(queue.client(), source, events::onEvent);
        for (var type : data.types()) {
            queue.send(source, SOURCE_OFFER, 1, arguments -> arguments.string(type));
        }
        var mask = WaylandSourceEvents.maskOf(actions);
        queue.send(source, SOURCE_SET_ACTIONS, 1, arguments -> arguments.unsigned(mask));
    }

    private void startDrag(WaylandDragOrigin origin) {
        var device = MemorySegment.ofAddress(origin.dataDevice());
        var surface = MemorySegment.ofAddress(origin.surface());
        var iconSurface = icon == null ? MemorySegment.NULL : icon.surface();
        queue.send(
                device,
                DEVICE_START_DRAG,
                4,
                arguments -> arguments
                        .object(source)
                        .object(surface)
                        .object(iconSurface)
                        .unsigned(origin.serial()));
        if (icon != null) {
            icon.show();
        }
        queue.flush();
    }

    /// Reads what the compositor has said since the last call and tells the
    /// listener, then ends a drop whose target has not finished within
    /// [#ANSWER_TIMEOUT]. Once a pump, on the UI thread.
    public void tick() {
        if (closed) {
            return;
        }
        if (!queue.dispatch()) {
            LOG.warn("the Wayland connection failed during a drag out (error {})", queue.error());
            end(listener::cancelled);
        }
        for (var report : events.drain()) {
            if (ended) {
                if (report instanceof WaylandSourceEvents.Report.Send(var mime, var fd)) {
                    Descriptors.close(fd);
                    LOG.debug("a request for {} after the drag ended was answered with nothing", mime);
                }
                continue;
            }
            hear(report);
        }
        if (!ended && droppedAt != 0 && System.nanoTime() - droppedAt > ANSWER_TIMEOUT.toNanos()) {
            LOG.debug("a Wayland target did not finish within {}; the drag is taken as delivered", ANSWER_TIMEOUT);
            var action = events.action();
            end(() -> listener.finished(action));
        }
        if (ended) {
            close();
        }
    }

    private void hear(WaylandSourceEvents.Report report) {
        switch (report) {
            case WaylandSourceEvents.Report.Target(var mime) -> listener.target(mime);
            case WaylandSourceEvents.Report.Send(var mime, var fd) -> send(mime, fd);
            case WaylandSourceEvents.Report.DropPerformed _ -> {
                droppedAt = System.nanoTime();
                listener.dropPerformed();
            }
            case WaylandSourceEvents.Report.Finished(var action) -> end(() -> listener.finished(action));
            case WaylandSourceEvents.Report.Cancelled _ -> end(listener::cancelled);
        }
    }

    /// Makes `mime`'s bytes here, on the UI thread, and writes them on the
    /// drag's own thread, which closes the descriptor.
    private void send(String mime, int fd) {
        byte[] bytes;
        try {
            bytes = mime.isEmpty() ? null : data.bytes(mime);
        } catch (RuntimeException e) {
            LOG.warn("a drag's data for {} failed; the target gets nothing", mime, e);
            bytes = null;
        }
        var answer = bytes == null ? new byte[0] : bytes;
        try {
            writer.execute(() -> {
                if (!Descriptors.writeAndClose(fd, answer)) {
                    LOG.debug("a drag target stopped reading {} before it had all {} bytes", mime, answer.length);
                }
            });
        } catch (RuntimeException e) {
            Descriptors.close(fd);
            throw e;
        }
    }

    private void end(Runnable telling) {
        if (ended) {
            return;
        }
        ended = true;
        telling.run();
    }

    /// Whether the drag is over and its objects gone.
    public boolean isClosed() {
        return closed;
    }

    /// Ends the drag now: destroying the source cancels it in the compositor.
    /// The writes already handed out finish on their own thread.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var report : events.drain()) {
            if (report instanceof WaylandSourceEvents.Report.Send(var _, var fd)) {
                Descriptors.close(fd);
            }
        }
        queue.destroy(source, SOURCE_DESTROY);
        if (icon != null) {
            icon.close();
        }
        // No destructor request before version 4 of the manager.
        queue.forget(manager);
        queue.flush();
        queue.close();
        writer.shutdown();
    }
}
