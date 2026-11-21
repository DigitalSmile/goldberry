package dev.goldberry.natives.desktop.dragout;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.wayland.Descriptors;
import dev.goldberry.natives.wayland.WaylandArguments;
import dev.goldberry.natives.wayland.WaylandQueue;

/// The surface a Wayland drag shows under the pointer: a [DragIcon] in a
/// shared-memory buffer.
///
/// Made before `start_drag`, which names the surface and gives it its role,
/// and filled after it ([#show]), as the protocol's own example does. The
/// compositor bound at version 4 at most, so that `attach` may still move
/// the picture by its hotspot: from version 5 on, it must be told with
/// `offset` instead, and an `attach` with an offset is a protocol error that
/// would end SDL's connection with the toolkit's.
final class WaylandIcon implements AutoCloseable {

    private static final Logger LOG = Logs.of(WaylandIcon.class);

    private static final int COMPOSITOR_CREATE_SURFACE = 0;
    private static final int SHM_CREATE_POOL = 0;
    private static final int SHM_RELEASE = 1;
    private static final int POOL_CREATE_BUFFER = 0;
    private static final int POOL_DESTROY = 1;
    private static final int BUFFER_DESTROY = 0;
    private static final int SURFACE_DESTROY = 0;
    private static final int SURFACE_ATTACH = 1;
    private static final int SURFACE_DAMAGE = 2;
    private static final int SURFACE_COMMIT = 6;
    private static final int SURFACE_SET_BUFFER_SCALE = 8;

    /// `WL_SHM_FORMAT_ARGB8888`, which every compositor must take.
    private static final int FORMAT_ARGB8888 = 0;

    /// The highest compositor version whose `attach` takes an offset.
    static final int COMPOSITOR_VERSION = 4;

    /// The first compositor version with `set_buffer_scale`.
    private static final int SCALE_SINCE = 3;

    /// The first `wl_shm` version with `release`.
    private static final int SHM_RELEASE_SINCE = 2;

    private final WaylandQueue queue;
    private final DragIcon icon;
    private final MemorySegment compositor;
    private final MemorySegment shm;
    private final MemorySegment pool;
    private final MemorySegment buffer;
    private final MemorySegment surface;
    private final int scale;
    private boolean closed;

    private WaylandIcon(
            WaylandQueue queue,
            DragIcon icon,
            MemorySegment compositor,
            MemorySegment shm,
            MemorySegment pool,
            MemorySegment buffer,
            MemorySegment surface,
            int scale) {
        this.queue = queue;
        this.icon = icon;
        this.compositor = compositor;
        this.shm = shm;
        this.pool = pool;
        this.buffer = buffer;
        this.surface = surface;
        this.scale = scale;
    }

    /// The surface for `icon`, or empty where the compositor has no
    /// `wl_compositor` or `wl_shm`, or the file cannot be made.
    static Optional<WaylandIcon> make(WaylandQueue queue, DragIcon icon) {
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(icon, "icon");
        var compositorGlobal = queue.global("wl_compositor");
        var shmGlobal = queue.global("wl_shm");
        if (compositorGlobal.isEmpty() || shmGlobal.isEmpty()) {
            LOG.debug("no wl_compositor or wl_shm on this connection; the drag shows no picture");
            return Optional.empty();
        }
        var bytes = icon.bytes();
        var file = Descriptors.anonymousFile("goldberry-drag-icon", bytes);
        if (file.isEmpty()) {
            LOG.debug("no anonymous file for the drag's picture; the drag shows no picture");
            return Optional.empty();
        }
        var compositorVersion = Math.min(compositorGlobal.get().version(), COMPOSITOR_VERSION);
        var compositor = queue.bind(compositorGlobal.get(), "wl_compositor_interface", compositorVersion);
        var shm = queue.bind(
                shmGlobal.get(), "wl_shm_interface", Math.min(shmGlobal.get().version(), 2));
        MemorySegment pool;
        try {
            pool = queue.create(
                    shm,
                    SHM_CREATE_POOL,
                    "wl_shm_pool_interface",
                    3,
                    arguments -> arguments.newId().integer(file.get()).integer(bytes.length));
        } finally {
            // libwayland duplicates a descriptor as it marshals it.
            Descriptors.close(file.get());
        }
        var buffer = queue.create(
                pool,
                POOL_CREATE_BUFFER,
                "wl_buffer_interface",
                6,
                arguments -> arguments
                        .newId()
                        .integer(0)
                        .integer(icon.width())
                        .integer(icon.height())
                        .integer(icon.stride())
                        .unsigned(FORMAT_ARGB8888));
        var surface =
                queue.create(compositor, COMPOSITOR_CREATE_SURFACE, "wl_surface_interface", 1, WaylandArguments::newId);
        var scale = compositorVersion >= SCALE_SINCE && icon.scaleDividesSize() ? icon.scale() : 1;
        return Optional.of(new WaylandIcon(queue, icon, compositor, shm, pool, buffer, surface, scale));
    }

    /// The surface, for `start_drag` to name.
    MemorySegment surface() {
        return surface;
    }

    /// Puts the picture on the surface, the hotspot under the pointer.
    void show() {
        if (closed) {
            return;
        }
        if (scale != 1) {
            queue.send(surface, SURFACE_SET_BUFFER_SCALE, 1, arguments -> arguments.integer(scale));
        }
        queue.send(
                surface,
                SURFACE_ATTACH,
                3,
                arguments -> arguments
                        .object(buffer)
                        .integer(-icon.hotspotX() / scale)
                        .integer(-icon.hotspotY() / scale));
        queue.send(
                surface,
                SURFACE_DAMAGE,
                4,
                arguments -> arguments
                        .integer(0)
                        .integer(0)
                        .integer(icon.width() / scale)
                        .integer(icon.height() / scale));
        queue.send(surface, SURFACE_COMMIT, 0, arguments -> {});
    }

    /// The scale the buffer is given, for a test.
    int scale() {
        return scale;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        queue.destroy(surface, SURFACE_DESTROY);
        queue.destroy(buffer, BUFFER_DESTROY);
        queue.destroy(pool, POOL_DESTROY);
        if (queue.client().version(shm) >= SHM_RELEASE_SINCE) {
            queue.destroy(shm, SHM_RELEASE);
        } else {
            queue.forget(shm);
        }
        queue.forget(compositor);
    }
}
