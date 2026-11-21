package dev.goldberry.mic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.mic.backend.CaptureLostException;
import dev.goldberry.mic.backend.CaptureStream;
import dev.goldberry.mic.source.SampleBlock;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.SampleListener;
import dev.goldberry.mic.source.SampleSource;

/// A microphone being recorded: a [SampleSource] whose blocks arrive on a
/// capture thread of its own.
///
/// The thread takes what the device has recorded every half block, and hands
/// each whole block to the [#onSamples] listeners, in order, borrowed. The
/// same frames also go into a buffer [#read] pulls from, for a caller with a
/// thread of its own; what it does not read in [RecordingSpec#buffer] is
/// dropped and counted ([#droppedFrames()]).
///
/// It starts recording when made, by [Microphones#record]. [#pause()] and
/// [#resume()] stop and start the device; [#close()] ends it. A recording
/// pinned to one device is [RecordingStatus.Lost] when that device goes away,
/// which is heard while the window loop runs; one from the system's choice
/// follows that choice instead.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class Recording implements SampleSource, AutoCloseable {

    private static final Logger LOG = Logs.of(Recording.class);

    private final Microphone microphone;
    private final RecordingSpec spec;
    private final CaptureStream stream;
    private final SampleRing ring;
    private final ByteBuffer incoming;
    private final FloatBuffer block;
    private final long blockNanos;
    private final List<SampleListener> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<? super RecordingStatus>> statusListeners = new CopyOnWriteArrayList<>();
    private final Thread thread;
    private final Runnable closed;
    private volatile RecordingStatus status = new RecordingStatus.Active();
    private volatile @Nullable String lost;
    private volatile boolean stopping;
    private boolean streamClosed;
    private long position;

    Recording(Microphone microphone, RecordingSpec spec, CaptureStream stream, Runnable closed) {
        this.microphone = Objects.requireNonNull(microphone, "microphone");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.stream = Objects.requireNonNull(stream, "stream");
        this.closed = Objects.requireNonNull(closed, "closed");
        var channels = spec.format().channels();
        this.ring = new SampleRing(spec.bufferFrames(), channels);
        this.incoming = ByteBuffer.allocateDirect(spec.blockFrames() * channels * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        this.block = incoming.duplicate()
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .asReadOnlyBuffer();
        this.blockNanos = spec.format().nanosOf(spec.blockFrames());
        this.thread = Thread.ofPlatform()
                .daemon()
                .name("goldberry-mic " + microphone.name())
                .unstarted(this::capture);
        stream.resume();
        thread.start();
    }

    /// The microphone this records.
    public Microphone microphone() {
        return microphone;
    }

    /// What it was asked for.
    public RecordingSpec spec() {
        return spec;
    }

    @Override
    public SampleFormat format() {
        return spec.format();
    }

    /// Where it is now.
    public RecordingStatus status() {
        return status;
    }

    /// Frames recorded so far.
    public synchronized long position() {
        return position;
    }

    @Override
    public AutoCloseable onSamples(SampleListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /// Tells `listener` each time the status changes, on whichever thread
    /// changed it, until the subscription is closed.
    ///
    /// @return what removes the listener
    public AutoCloseable onStatus(Consumer<? super RecordingStatus> listener) {
        Objects.requireNonNull(listener, "listener");
        statusListeners.add(listener);
        return () -> statusListeners.remove(listener);
    }

    /// Copies as many whole frames as fit in `into`, oldest first, out of what
    /// has been recorded and not yet read. Never waits.
    ///
    /// @return the frames read
    public int read(FloatBuffer into) {
        Objects.requireNonNull(into, "into");
        return ring.read(into);
    }

    /// Frames recorded and not yet [#read].
    public int availableFrames() {
        return ring.available();
    }

    /// Frames dropped because [#read] fell more than [RecordingSpec#buffer]
    /// behind.
    public long droppedFrames() {
        return ring.dropped();
    }

    /// Stops the device, keeping what was recorded. Does nothing unless the
    /// recording is active.
    public synchronized void pause() {
        if (status instanceof RecordingStatus.Active) {
            stream.pause();
            change(new RecordingStatus.Paused());
        }
    }

    /// Starts the device again. Does nothing unless the recording is paused.
    public synchronized void resume() {
        if (status instanceof RecordingStatus.Paused) {
            stream.resume();
            change(new RecordingStatus.Active());
        }
    }

    /// Ends the recording and closes its device. Idempotent; a recording that
    /// was lost stays [RecordingStatus.Lost].
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
            if (!status.isFinal()) {
                change(new RecordingStatus.Closed());
            }
        }
        closed.run();
    }

    /// Marks the device gone: the capture thread stops at its next turn.
    void lose(String reason) {
        lost = reason;
        LockSupport.unpark(thread);
    }

    private void capture() {
        while (!stopping) {
            var reason = lost;
            if (reason != null) {
                end(reason);
                return;
            }
            if (status instanceof RecordingStatus.Paused) {
                LockSupport.parkNanos(blockNanos);
                continue;
            }
            int frames;
            try {
                frames = stream.read(incoming);
            } catch (CaptureLostException e) {
                end(String.valueOf(e.getMessage()));
                return;
            } catch (RuntimeException e) {
                LOG.warn("reading the microphone failed", e);
                end("reading the microphone failed: " + e);
                return;
            }
            if (!incoming.hasRemaining()) {
                deliver();
                incoming.clear();
            } else if (frames == 0) {
                LockSupport.parkNanos(blockNanos / 2);
            }
        }
    }

    private void deliver() {
        var frames = spec.blockFrames();
        long at;
        synchronized (this) {
            at = position;
            position += frames;
        }
        ring.write(block, frames);
        var sample = new SampleBlock(block, spec.format(), frames, at, System.nanoTime());
        for (var listener : listeners) {
            try {
                listener.onSamples(sample);
            } catch (RuntimeException e) {
                LOG.warn("a sample listener failed", e);
            }
        }
    }

    private synchronized void end(String reason) {
        closeStream();
        if (!status.isFinal()) {
            LOG.info("recording from {} lost: {}", microphone.name(), reason);
            change(new RecordingStatus.Lost(reason));
        }
    }

    private void closeStream() {
        if (!streamClosed) {
            streamClosed = true;
            stream.close();
        }
    }

    private void change(RecordingStatus next) {
        status = next;
        for (var listener : statusListeners) {
            try {
                listener.accept(next);
            } catch (RuntimeException e) {
                LOG.warn("a status listener failed", e);
            }
        }
    }
}
