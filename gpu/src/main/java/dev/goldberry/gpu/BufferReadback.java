package dev.goldberry.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/// Bytes of a buffer on their way back to the CPU, recorded by
/// [GpuFrame#readback(GpuBuffer, int, int)] and readable once that frame is
/// submitted: what a compute pass wrote, for the CPU to read.
///
/// [#await] blocks, as [Readback#await] does: it waits for the GPU to finish
/// the frame. Closing one that was never awaited gives its memory back without
/// waiting.
public final class BufferReadback implements AutoCloseable {

    private final TransferState state;
    private final int size;

    BufferReadback(TransferState state, int size) {
        this.state = state;
        this.size = size;
    }

    /// How many bytes it reads.
    public int size() {
        return size;
    }

    /// Whether the bytes have arrived and been copied out: [#await] would not
    /// block.
    public boolean isDone() {
        return state.isDone();
    }

    /// Waits for the GPU, then returns the bytes, little-endian as every GPU
    /// the toolkit runs on writes them. A read-only view of memory the Java
    /// heap owns, so it outlives the GPU's; each call returns a fresh view of
    /// the same bytes.
    ///
    /// @throws IllegalStateException when the frame was not submitted, was
    ///                               discarded, or this was closed
    /// @throws GpuException          when the wait fails: a lost device
    public ByteBuffer await() {
        return state.await().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    }

    /// Gives back its memory without waiting. Idempotent.
    @Override
    public void close() {
        state.close();
    }
}
