package dev.goldberry.camera;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.camera.backend.CameraFeed;
import dev.goldberry.camera.backend.CameraLostException;
import dev.goldberry.camera.backend.CameraStream;
import dev.goldberry.camera.backend.Nv12Frame;
import dev.goldberry.camera.backend.PlanePool;
import dev.goldberry.log.Logs;
import dev.goldberry.media.picture.Picture;

/// A camera the system lists, opened: a capture thread of its own takes each
/// new frame as the camera lends it, copies it into a [PlanePool] and gives it
/// back at once, so a slow view never holds a driver buffer.
///
/// The thread also asks the camera's permission while it is unanswered, so a
/// camera allowed or refused moves on whether or not a window loop delivers
/// the system's event.
final class DeviceCamera implements Camera {

    private static final Logger LOG = Logs.of(DeviceCamera.class);

    /// How often an unanswered permission is asked again.
    static final Duration ASKING_POLL = Duration.ofMillis(20);

    private final CameraInfo info;
    private final CameraFormat format;
    private final CameraStream stream;
    private final Runnable closed;
    private final CameraFeed feed;
    private final Thread thread;
    private final long idleNanos;
    private @Nullable PlanePool pool;
    private volatile @Nullable String lost;
    private volatile boolean stopping;
    private boolean streamClosed;

    DeviceCamera(CameraInfo info, CameraFormat format, CameraStream stream, Runnable closed) {
        this.info = Objects.requireNonNull(info, "info");
        this.format = Objects.requireNonNull(format, "format");
        this.stream = Objects.requireNonNull(stream, "stream");
        this.closed = Objects.requireNonNull(closed, "closed");
        this.feed = new CameraFeed(statusOf(stream.permission()));
        // A quarter of a frame between looks, and never more than 5 ms.
        var rate = format.framesPerSecond();
        var frameNanos = rate > 0 ? (long) (1e9 / rate) : TimeUnit.MILLISECONDS.toNanos(33);
        this.idleNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(5), frameNanos / 4);
        this.thread = Thread.ofPlatform()
                .daemon()
                .name("goldberry-camera " + info.name())
                .unstarted(this::capture);
        thread.start();
    }

    private static CameraStatus statusOf(CameraStream.Permission permission) {
        return switch (permission) {
            case PENDING -> new CameraStatus.Asking();
            case APPROVED -> new CameraStatus.Active();
            case DENIED -> new CameraStatus.Denied();
        };
    }

    @Override
    public CameraInfo info() {
        return info;
    }

    @Override
    public CameraFormat format() {
        return format;
    }

    @Override
    public CameraStatus status() {
        return feed.status();
    }

    @Override
    public Optional<Picture> shownPicture() {
        return feed.shown();
    }

    @Override
    public AutoCloseable onPicture(Runnable listener) {
        return feed.onPicture(listener);
    }

    @Override
    public AutoCloseable onStatus(Consumer<? super CameraStatus> listener) {
        return feed.onStatus(listener);
    }

    /// Wakes the thread: a permission was answered.
    void wake() {
        LockSupport.unpark(thread);
    }

    /// Marks the camera gone: the thread stops at its next turn.
    void lose(String reason) {
        lost = reason;
        LockSupport.unpark(thread);
    }

    @Override
    public void close() {
        stopping = true;
        LockSupport.unpark(thread);
        if (Thread.currentThread() != thread) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (this) {
            closeStream();
        }
        feed.change(new CameraStatus.Closed());
        closed.run();
    }

    private void capture() {
        while (!stopping) {
            var reason = lost;
            if (reason != null) {
                end(new CameraStatus.Lost(reason));
                return;
            }
            CameraStream.Permission permission;
            try {
                permission = stream.permission();
            } catch (RuntimeException e) {
                end(new CameraStatus.Lost("asking the camera failed: " + e));
                return;
            }
            switch (permission) {
                case PENDING -> {
                    feed.change(new CameraStatus.Asking());
                    LockSupport.parkNanos(ASKING_POLL.toNanos());
                    continue;
                }
                case DENIED -> {
                    end(new CameraStatus.Denied());
                    return;
                }
                case APPROVED -> feed.change(new CameraStatus.Active());
            }
            boolean took;
            try {
                took = stream.take(this::copy);
            } catch (CameraLostException e) {
                end(new CameraStatus.Lost(String.valueOf(e.getMessage())));
                return;
            } catch (RuntimeException e) {
                LOG.warn("reading the camera failed", e);
                end(new CameraStatus.Lost("reading the camera failed: " + e));
                return;
            }
            if (!took) {
                LockSupport.parkNanos(idleNanos);
            }
        }
    }

    /// Copies a lent frame and shows the copy.
    private void copy(Nv12Frame frame) {
        var current = pool;
        if (current == null || current.width() != frame.width() || current.height() != frame.height()) {
            // The size the camera sends, which SDL keeps to the mode asked; a
            // pool of another size would refuse it.
            current = new PlanePool(frame.width(), frame.height());
            pool = current;
        }
        var pts = frame.timestampNanos() != 0 ? frame.timestampNanos() : System.nanoTime();
        feed.show(current.copy(frame, pts));
    }

    private synchronized void end(CameraStatus status) {
        closeStream();
        if (feed.change(status)) {
            LOG.info("camera {} ended: {}", info.name(), status);
        }
    }

    private void closeStream() {
        if (!streamClosed) {
            streamClosed = true;
            stream.close();
        }
    }
}
