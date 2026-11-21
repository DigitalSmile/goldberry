package dev.goldberry.mic;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.goldberry.mic.backend.CaptureBackend;
import dev.goldberry.mic.backend.CaptureDevice;
import dev.goldberry.mic.backend.CaptureLostException;
import dev.goldberry.mic.backend.CaptureStream;
import dev.goldberry.mic.backend.DeviceEvent;
import dev.goldberry.mic.source.SampleFormat;

/// A backend a test drives: devices it lists, streams fed the samples the test
/// hands them, device events it raises.
final class FakeBackend implements CaptureBackend {

    static final int DEFAULT = 0xFFFFFFFE;

    final List<CaptureDevice> devices = new CopyOnWriteArrayList<>();
    final List<FakeStream> opened = new CopyOnWriteArrayList<>();
    private final List<Consumer<DeviceEvent>> watchers = new CopyOnWriteArrayList<>();
    boolean closed;

    FakeBackend(CaptureDevice... devices) {
        this.devices.addAll(List.of(devices));
    }

    @Override
    public List<CaptureDevice> devices() {
        return List.copyOf(devices);
    }

    @Override
    public int systemDefault() {
        return DEFAULT;
    }

    @Override
    public Optional<String> name(int id) {
        if (id == DEFAULT) {
            return Optional.of("Default Recording Device");
        }
        return devices.stream()
                .filter(d -> d.id() == id)
                .map(CaptureDevice::name)
                .findFirst();
    }

    @Override
    public CaptureStream open(int id, SampleFormat format) {
        if (id != DEFAULT && devices.stream().noneMatch(d -> d.id() == id)) {
            throw new CaptureLostException("no device " + id, new IllegalStateException());
        }
        var stream = new FakeStream(id, format);
        opened.add(stream);
        return stream;
    }

    @Override
    public AutoCloseable watch(Consumer<DeviceEvent> listener) {
        watchers.add(listener);
        return () -> watchers.remove(listener);
    }

    /// What the system says, as the pump would hand it over.
    void raise(DeviceEvent event) {
        if (event instanceof DeviceEvent.Added(var id)) {
            devices.add(new CaptureDevice(id, "Headset " + id));
        } else if (event instanceof DeviceEvent.Removed(var id)) {
            devices.removeIf(d -> d.id() == id);
        }
        watchers.forEach(watcher -> watcher.accept(event));
    }

    int watchers() {
        return watchers.size();
    }

    @Override
    public void close() {
        closed = true;
    }

    /// A stream that hands out what the test feeds it, in reads of any size.
    static final class FakeStream implements CaptureStream {

        final int device;
        final SampleFormat format;
        private final ConcurrentLinkedQueue<Float> pending = new ConcurrentLinkedQueue<>();
        volatile boolean paused = true;
        volatile boolean closed;
        volatile boolean failing;
        final List<String> calls = new CopyOnWriteArrayList<>();

        FakeStream(int device, SampleFormat format) {
            this.device = device;
            this.format = format;
        }

        /// Samples the device "records", interleaved.
        void feed(float... samples) {
            for (var sample : samples) {
                pending.add(sample);
            }
        }

        @Override
        public int read(ByteBuffer into) {
            if (failing) {
                throw new CaptureLostException("the fake device failed", new IllegalStateException());
            }
            if (paused) {
                return 0;
            }
            var channels = format.channels();
            var frames = 0;
            var taken = new ArrayList<Float>();
            while (into.remaining() - taken.size() * Float.BYTES >= channels * Float.BYTES
                    && pending.size() >= channels) {
                for (var channel = 0; channel < channels; channel++) {
                    taken.add(pending.poll());
                }
                frames++;
            }
            taken.forEach(into::putFloat);
            return frames;
        }

        @Override
        public void pause() {
            calls.add("pause");
            paused = true;
        }

        @Override
        public void resume() {
            calls.add("resume");
            paused = false;
        }

        @Override
        public void close() {
            calls.add("close");
            closed = true;
        }
    }
}
