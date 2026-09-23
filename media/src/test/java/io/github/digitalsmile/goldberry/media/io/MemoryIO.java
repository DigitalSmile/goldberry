package io.github.digitalsmile.goldberry.media.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.util.Optional;
import java.util.OptionalLong;

/// An in-memory [MediaIO] with faults a test can switch on: the start of the
/// fault-injecting fake `docs/goldberry-media.md` §9 asks for.
///
/// Short reads, reads that return nothing, failures, a missing size, being
/// unseekable, and, for the Engine's water marks (S3), a **stall**: reading on
/// to [#stallAt] blocks until [#release()], as a network that goes quiet, and a
/// [#close()] ends it as it would end one blocked on a socket. It can also say
/// it is [#live] and what is [#title]d as playing (S6), and [#holdSeeks()] until
/// released, for what happens while the demuxer is still seeking.
public final class MemoryIO implements MediaIO {

    private final byte[] data;
    private int position;
    private volatile boolean closed;

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

    /// The offset at which reads block until [#release()]; -1 for none. May be
    /// moved while a read is stalled, to stall again further on.
    public volatile long stallAt = -1;

    /// Whether [#isLive()] answers true.
    public boolean live;

    /// What [#nowPlaying()] answers.
    public volatile String title;

    private int releases;
    private boolean stalled;
    private boolean holdSeeks;
    private boolean seekHeld;

    public MemoryIO(byte[] data) {
        this.data = data.clone();
    }

    @Override
    public int read(ByteBuffer target) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        // Only a read that arrives at the stall in sequence waits: reads before
        // it stop short of it, so they land on it exactly. A demuxer that seeks
        // past it while probing (WAV looks for chunks after its data) is not
        // stalled, as a network would not stall a Range request elsewhere.
        var stall = stallAt;
        if (stall >= 0 && position == stall) {
            awaitRelease();
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
        stall = stallAt;
        if (stall >= 0 && position < stall) {
            // Up to the stall, and no further.
            count = (int) Math.min(count, stall - position);
        }
        target.put(data, position, count);
        position += count;
        return count;
    }

    @Override
    public void seek(long position) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        awaitSeekRelease();
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
    public boolean isLive() {
        return live;
    }

    @Override
    public Optional<String> nowPlaying() {
        return Optional.ofNullable(title);
    }

    /// Makes every seek from now on wait for [#releaseSeeks()]: a seek the
    /// demuxer is still carrying out.
    public synchronized void holdSeeks() {
        holdSeeks = true;
    }

    /// Lets held seeks, and every later one, through.
    public synchronized void releaseSeeks() {
        holdSeeks = false;
        notifyAll();
    }

    /// Whether a seek is held now.
    public synchronized boolean seekHeld() {
        return seekHeld;
    }

    private synchronized void awaitSeekRelease() throws IOException {
        seekHeld = holdSeeks;
        try {
            while (holdSeeks && !closed) {
                wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException();
        } finally {
            seekHeld = false;
        }
        if (closed) {
            throw new AsynchronousCloseException();
        }
    }

    /// Whether a read is blocked at the stall now.
    public synchronized boolean stalled() {
        return stalled;
    }

    /// Lets the stalled read go on, to the next stall if [#stallAt] was moved
    /// on, or to the end.
    public synchronized void release() {
        releases++;
        notifyAll();
    }

    @Override
    public synchronized void close() {
        closed = true;
        closes++;
        notifyAll();
    }

    private synchronized void awaitRelease() throws IOException {
        var entered = releases;
        stalled = true;
        try {
            while (releases == entered && !closed) {
                wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException();
        } finally {
            stalled = false;
        }
        if (closed) {
            throw new AsynchronousCloseException();
        }
    }
}
