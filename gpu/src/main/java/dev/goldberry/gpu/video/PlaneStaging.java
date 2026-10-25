package dev.goldberry.gpu.video;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuException;

/// A [VideoLayer]'s upload memory, handed to whoever decodes its pictures:
/// planes written into it are uploaded with no copy on the layer's thread.
///
/// [#obtain] is any thread's: it hands out a [StagedPlanes] that is mapped and
/// free to write, or nothing when there is none of that shape. Nothing is made
/// there, since the device belongs to the layer's thread; a shape asked for is
/// remembered, and the layer makes buffers of it as it next renders, as many as
/// a player keeps pictures. Until then, and whenever the staging has none to
/// give, the decoder keeps the planes itself and the layer uploads them as it
/// always has.
///
/// A picture [StagedPlanes#release]d comes back retired: the GPU may still be
/// reading it. The layer maps it again with cycling as it renders, which gives
/// fresh memory where the old is still in use, and only then is it handed out
/// again. A new device, or a closed layer, closes every buffer; a picture still
/// held then is no longer alive, and nothing of it is drawn.
public final class PlaneStaging {

    /// The most buffers kept of one shape: the pictures a player keeps
    /// queued, handed out and shown, with one to spare.
    static final int PER_SHAPE = 8;

    /// The most shapes kept at once: a stream that changes size keeps its
    /// last two.
    static final int SHAPES = 2;

    private record Shape(PlaneLayout layout, int width, int height) {}

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Shape, Deque<StagedPlanes>> free = new HashMap<>();
    private final Set<StagedPlanes> held = new HashSet<>();
    private final List<StagedPlanes> retired = new ArrayList<>();
    private final Map<Shape, Integer> made = new HashMap<>();
    private final Set<Shape> wanted = new LinkedHashSet<>();
    private @Nullable GpuDevice device;
    private boolean closed;

    PlaneStaging() {}

    /// Planes of `layout`, `width` by `height`, mapped and free to write, or
    /// empty when there are none: the next render of the layer makes some.
    /// Any thread.
    public Optional<StagedPlanes> obtain(PlaneLayout layout, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
        var shape = new Shape(layout, width, height);
        lock.lock();
        try {
            if (closed) {
                return Optional.empty();
            }
            var queue = free.get(shape);
            var planes = queue == null ? null : queue.pollFirst();
            if (planes == null) {
                if (made.getOrDefault(shape, 0) < PER_SHAPE) {
                    wanted.add(shape);
                }
                return Optional.empty();
            }
            held.add(planes);
            return Optional.of(planes);
        } finally {
            lock.unlock();
        }
    }

    /// Takes `planes` back. Any thread.
    void release(StagedPlanes planes) {
        lock.lock();
        try {
            if (held.remove(planes)) {
                retired.add(planes);
            }
        } finally {
            lock.unlock();
        }
    }

    /// Whether `planes` is still handed out, on this staging's device.
    boolean holds(StagedPlanes planes) {
        return held.contains(planes);
    }

    ReentrantLock lock() {
        return lock;
    }

    /// The layer's turn, on its thread as it renders on `on`: a new device
    /// closes everything made on the last; retired buffers are mapped afresh
    /// and freed; a shape asked for gets buffers.
    void maintain(GpuDevice on) {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            if (device != on) {
                drop();
                device = on;
            }
            for (var planes : retired) {
                var buffer = planes.buffer();
                if (buffer.isClosed()) {
                    continue;
                }
                if (!made.containsKey(shapeOf(planes))) {
                    // A shape forgotten while this was held.
                    planes.mapping(null);
                    buffer.close();
                    continue;
                }
                planes.mapping(null);
                buffer.unmap();
                ByteBuffer mapped;
                try {
                    mapped = buffer.map(true);
                } catch (GpuException e) {
                    buffer.close();
                    continue;
                }
                planes.mapping(mapped);
                free.computeIfAbsent(shapeOf(planes), _ -> new ArrayDeque<>()).addLast(planes);
            }
            retired.clear();
            for (var shape : wanted) {
                if (!made.containsKey(shape) && made.size() >= SHAPES) {
                    forget(made.keySet().iterator().next());
                }
                var count = made.getOrDefault(shape, 0);
                var size = StagedPlanes.size(shape.layout(), shape.width(), shape.height());
                var strides = new ArrayList<Integer>();
                for (var plane = 0; plane < shape.layout().planes(); plane++) {
                    strides.add(StagedPlanes.stride(shape.layout(), plane, shape.width()));
                }
                var offsets = StagedPlanes.offsets(shape.layout(), shape.width(), shape.height());
                for (; count < PER_SHAPE; count++) {
                    var buffer = on.createUploadBuffer(size);
                    var planes = new StagedPlanes(
                            this, buffer, shape.layout(), shape.width(), shape.height(), offsets, strides);
                    planes.mapping(buffer.map(false));
                    free.computeIfAbsent(shape, _ -> new ArrayDeque<>()).addLast(planes);
                }
                made.put(shape, count);
            }
            wanted.clear();
        } finally {
            lock.unlock();
        }
    }

    /// Unmaps `planes` for the instant the layer records its upload. On the
    /// layer's thread; `remap` after.
    void unmapForUpload(StagedPlanes planes) {
        lock.lock();
        try {
            planes.mapping(null);
            planes.buffer().unmap();
        } finally {
            lock.unlock();
        }
    }

    /// Maps the same memory again after the upload was recorded, so the
    /// picture's holder still reads its planes.
    void remap(StagedPlanes planes) {
        lock.lock();
        try {
            if (!planes.buffer().isClosed()) {
                planes.mapping(planes.buffer().map(false));
            }
        } finally {
            lock.unlock();
        }
    }

    /// Closes every buffer: a closed layer's. Idempotent.
    void close() {
        lock.lock();
        try {
            closed = true;
            drop();
            wanted.clear();
        } finally {
            lock.unlock();
        }
    }

    /// Everything made on the device in hand, closed. Under the lock, on the
    /// device's thread (or with the device closed already).
    private void drop() {
        var all = new ArrayList<StagedPlanes>(held);
        all.addAll(retired);
        free.values().forEach(all::addAll);
        for (var planes : all) {
            planes.mapping(null);
            var buffer = planes.buffer();
            if (!buffer.isClosed() && device != null && !device.isClosed()) {
                buffer.close();
            }
        }
        free.clear();
        held.clear();
        retired.clear();
        // The shapes asked for stay: they are the stream's, not the device's.
        for (var shape : made.keySet()) {
            wanted.add(shape);
        }
        made.clear();
    }

    /// Closes the free buffers of `shape` and forgets it. Its held ones close
    /// as they come back.
    private void forget(Shape shape) {
        var queue = free.remove(shape);
        if (queue != null) {
            for (var planes : queue) {
                planes.mapping(null);
                planes.buffer().close();
            }
        }
        made.remove(shape);
    }

    private static Shape shapeOf(StagedPlanes planes) {
        return new Shape(planes.layout(), planes.width(), planes.height());
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            var free = this.free.values().stream().mapToInt(Deque::size).sum();
            return "PlaneStaging["
                    + (closed ? "closed" : free + " free, " + held.size() + " held, " + retired.size() + " retired")
                    + "]";
        } finally {
            lock.unlock();
        }
    }
}
