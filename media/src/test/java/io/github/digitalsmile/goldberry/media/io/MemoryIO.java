package io.github.digitalsmile.goldberry.media.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.OptionalLong;

/// An in-memory [MediaIO] with faults a test can switch on: the start of the
/// fault-injecting fake `docs/goldberry-media.md` §9 asks for.
///
/// Phase 6 grows it into stalls and drops. What it does now is enough for the
/// callback rules: short reads, reads that return nothing, failures, a missing
/// size, and being unseekable.
public final class MemoryIO implements MediaIO {

    private final byte[] data;
    private int position;
    private boolean closed;

    /// At most this many bytes per read; `Integer.MAX_VALUE` for no limit.
    public int chunk = Integer.MAX_VALUE;

    /// How many reads return 0 before the data flows.
    public int emptyReads;

    /// Thrown by the next read, if set.
    public IOException readFailure;

    /// Thrown by the next read, if set: a bug in a MediaIO, not an I/O failure.
    public RuntimeException readBug;

    /// Whether [#size()] answers.
    public boolean sizeKnown = true;

    /// Whether [#isSeekable()] answers true.
    public boolean seekable = true;

    /// How many times [#close()] was called.
    public int closes;

    public MemoryIO(byte[] data) {
        this.data = data.clone();
    }

    @Override
    public int read(ByteBuffer target) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        if (readBug != null) {
            throw readBug;
        }
        if (readFailure != null) {
            var failure = readFailure;
            readFailure = null;
            throw failure;
        }
        if (emptyReads > 0) {
            emptyReads--;
            return 0;
        }
        if (position >= data.length) {
            return -1;
        }
        var count = Math.min(Math.min(chunk, target.remaining()), data.length - position);
        target.put(data, position, count);
        position += count;
        return count;
    }

    @Override
    public void seek(long position) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        this.position = (int) Math.min(position, data.length);
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public OptionalLong size() {
        return sizeKnown ? OptionalLong.of(data.length) : OptionalLong.empty();
    }

    @Override
    public boolean isSeekable() {
        return seekable;
    }

    @Override
    public void close() {
        closed = true;
        closes++;
    }
}
