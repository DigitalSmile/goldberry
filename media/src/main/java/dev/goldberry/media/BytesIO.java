package dev.goldberry.media;

import java.nio.ByteBuffer;
import java.util.OptionalLong;

import dev.goldberry.media.io.MediaIO;

/// A [MediaIO] over bytes already in memory: what a [VideoAnimation] reads its
/// video from. Seekable, of a known size, and never blocked.
final class BytesIO implements MediaIO {

    private final byte[] bytes;
    private long position;

    BytesIO(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public int read(ByteBuffer target) {
        if (position >= bytes.length) {
            return -1;
        }
        var count = (int) Math.min(target.remaining(), bytes.length - position);
        target.put(bytes, (int) position, count);
        position += count;
        return count;
    }

    @Override
    public void seek(long position) {
        if (position < 0) {
            throw new IllegalArgumentException("a negative position: " + position);
        }
        this.position = position;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public OptionalLong size() {
        return OptionalLong.of(bytes.length);
    }

    /// Nothing to free: the bytes are the collector's.
    @Override
    public void close() {
        // Nothing is open.
    }
}
