package dev.goldberry.camera;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.camera.backend.CameraBackend;
import dev.goldberry.camera.backend.CameraLostException;
import dev.goldberry.camera.backend.CameraStream;
import dev.goldberry.camera.backend.DeviceEvent;
import dev.goldberry.camera.backend.JpegFrame;
import dev.goldberry.camera.backend.LentFrame;
import dev.goldberry.camera.backend.Nv12Frame;
import dev.goldberry.media.codec.VideoFrame;

/// A backend a test drives: the cameras it lists, the frames each stream
/// lends, the permission it answers, and the events it raises.
final class FakeCameraBackend implements CameraBackend {

    final List<CameraInfo> devices = new CopyOnWriteArrayList<>();
    final List<Stream> opened = new CopyOnWriteArrayList<>();
    final List<Consumer<DeviceEvent>> watchers = new CopyOnWriteArrayList<>();
    volatile CameraStream.Permission permission = CameraStream.Permission.APPROVED;
    volatile boolean refuses;
    boolean closed;

    @Override
    public List<CameraInfo> devices() {
        return List.copyOf(devices);
    }

    @Override
    public Optional<String> driver() {
        return Optional.of("fake");
    }

    @Override
    public CameraStream open(int id, CameraFormat mode) {
        if (refuses) {
            throw new CameraLostException("busy");
        }
        var stream = new Stream(id, mode);
        opened.add(stream);
        return stream;
    }

    @Override
    public AutoCloseable watch(Consumer<DeviceEvent> listener) {
        watchers.add(listener);
        return () -> watchers.remove(listener);
    }

    void raise(DeviceEvent event) {
        watchers.forEach(watcher -> watcher.accept(event));
    }

    @Override
    public void close() {
        closed = true;
    }

    /// A stream whose frames the test queues: an NV12 frame of one luma value,
    /// or the bytes of a Motion JPEG frame.
    final class Stream implements CameraStream {

        final int id;
        final CameraFormat mode;
        private final Deque<LentFrame> queued = new ArrayDeque<>();
        volatile boolean closed;
        volatile @Nullable String fails;

        Stream(int id, CameraFormat mode) {
            this.id = id;
            this.mode = mode;
        }

        /// Queues a frame whose every luma byte is `luma`.
        synchronized void queue(int luma) {
            queued.add(frame(mode.width(), mode.height(), luma));
        }

        /// Queues a Motion JPEG frame of `jpeg`'s bytes, lent read-only from a
        /// direct buffer as SDL lends one.
        synchronized void queueJpeg(byte[] jpeg) {
            var bytes = ByteBuffer.allocateDirect(jpeg.length).put(0, jpeg);
            queued.add(new JpegFrame(bytes.asReadOnlyBuffer(), mode.width(), mode.height(), 0));
        }

        @Override
        public Permission permission() {
            return permission;
        }

        @Override
        public boolean take(FrameSink sink) {
            var failure = fails;
            if (failure != null) {
                throw new CameraLostException(failure);
            }
            LentFrame frame;
            synchronized (this) {
                frame = queued.poll();
            }
            if (frame == null) {
                return false;
            }
            sink.accept(frame);
            return true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /// An NV12 frame laid out as SDL lays one out: a pitch wider than the
    /// picture, chroma after luma.
    static Nv12Frame frame(int width, int height, int luma) {
        var pitch = width + 16;
        var chromaRows = (height + 1) / 2;
        var bytes = ByteBuffer.allocateDirect(pitch * height + pitch * chromaRows);
        for (var index = 0; index < pitch * height; index++) {
            bytes.put(index, (byte) luma);
        }
        for (var index = pitch * height; index < bytes.capacity(); index++) {
            bytes.put(index, (byte) 128);
        }
        return new Nv12Frame(
                bytes.asReadOnlyBuffer(),
                width,
                height,
                0,
                pitch,
                pitch * height,
                pitch,
                VideoFrame.ColorMatrix.BT709,
                false,
                0);
    }

    static CameraInfo camera(int id, String name, CameraPosition position, CameraFormat... formats) {
        return new CameraInfo(id, name, position, new ArrayList<>(List.of(formats)));
    }
}
