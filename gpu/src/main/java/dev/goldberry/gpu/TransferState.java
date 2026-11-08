package dev.goldberry.gpu;

import java.nio.ByteBuffer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.gpu.SdlGpuTransferBuffer;

/// The life of bytes on their way back to the CPU, which a [Readback] and a
/// [BufferReadback] share: recorded into a frame, submitted with it, copied
/// out of the transfer buffer once its fence has signalled, or discarded with
/// the frame, or closed first. The frame holds this; the readback the
/// application holds wraps it.
final class TransferState {

    private enum State {
        RECORDED,
        SUBMITTED,
        DONE,
        DISCARDED,
        CLOSED
    }

    private final GpuDevice device;
    private @Nullable SdlGpuTransferBuffer transfer;
    private @Nullable SharedFence fence;
    private @Nullable ByteBuffer bytes;
    private State state = State.RECORDED;

    TransferState(GpuDevice device, SdlGpuTransferBuffer transfer) {
        this.device = device;
        this.transfer = transfer;
    }

    /// Whether the bytes have arrived and been copied out.
    boolean isDone() {
        return state == State.DONE;
    }

    /// Waits for the GPU, then returns the bytes: a heap buffer this owns.
    ///
    /// @throws IllegalStateException when the frame was not submitted, was
    ///                               discarded, or this was closed
    /// @throws GpuException          when the wait fails: a lost device
    ByteBuffer await() {
        device.requireThread();
        switch (state) {
            case RECORDED -> throw new IllegalStateException("the frame this was read in has not been submitted");
            case DISCARDED -> throw new IllegalStateException("the frame this was read in was discarded");
            case CLOSED -> throw new IllegalStateException("this readback is closed");
            case SUBMITTED -> copyOut();
            case DONE -> {}
        }
        var done = bytes;
        if (done == null) {
            throw new IllegalStateException("no bytes");
        }
        return done;
    }

    /// Gives back its memory without waiting. Idempotent.
    void close() {
        device.requireThread();
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        bytes = null;
        releaseGpuSide();
    }

    /// Its frame was submitted, and `submitted` signals when the bytes are in.
    void submitted(SharedFence submitted) {
        if (state == State.CLOSED) {
            submitted.release();
            return;
        }
        fence = submitted;
        state = State.SUBMITTED;
    }

    /// Its frame was discarded, and the bytes will never come.
    void discarded() {
        if (state == State.RECORDED) {
            state = State.DISCARDED;
            releaseGpuSide();
        }
    }

    private void copyOut() {
        var buffer = transfer;
        var submitted = fence;
        if (buffer == null || submitted == null || device.isClosed()) {
            throw new IllegalStateException("the device this was read on is closed");
        }
        try {
            submitted.await();
            var mapped = buffer.map(false);
            try {
                var copy = ByteBuffer.allocate(mapped.remaining());
                copy.put(0, mapped, 0, mapped.remaining());
                bytes = copy;
            } finally {
                buffer.unmap();
            }
        } catch (SdlException e) {
            throw GpuException.of(e);
        }
        state = State.DONE;
        releaseGpuSide();
    }

    private void releaseGpuSide() {
        var buffer = transfer;
        transfer = null;
        if (buffer != null) {
            buffer.close();
        }
        var submitted = fence;
        fence = null;
        if (submitted != null) {
            submitted.release();
        }
    }
}
