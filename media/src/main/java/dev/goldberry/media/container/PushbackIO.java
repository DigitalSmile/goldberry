package dev.goldberry.media.container;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import dev.goldberry.media.io.ByteRange;
import dev.goldberry.media.io.MediaIO;

/// A [MediaIO] whose first bytes were already read: it hands them out again
/// before reading on. For a source that cannot seek, which the sniffer read
/// the head of and cannot rewind.
///
/// Positions are the underlying stream's: the head was at 0, and reads carry
/// on from its length. Seeking is refused, as the underlying stream refuses it.
final class PushbackIO implements MediaIO {

    private final MediaIO io;
    private final byte[] head;
    private int served;

    PushbackIO(MediaIO io, byte[] head) {
        this.io = Objects.requireNonNull(io, "io");
        this.head = Objects.requireNonNull(head, "head");
    }

    @Override
    public int read(ByteBuffer target) throws IOException {
        if (served < head.length) {
            var count = Math.min(target.remaining(), head.length - served);
            target.put(head, served, count);
            served += count;
            return count;
        }
        return io.read(target);
    }

    @Override
    public void seek(long position) throws IOException {
        throw new IOException("the source cannot seek");
    }

    @Override
    public long position() {
        return served < head.length ? served : io.position();
    }

    @Override
    public OptionalLong size() {
        return io.size();
    }

    @Override
    public boolean isSeekable() {
        return false;
    }

    @Override
    public boolean isLive() {
        return io.isLive();
    }

    @Override
    public List<ByteRange> buffered() {
        return io.buffered();
    }

    @Override
    public Optional<String> nowPlaying() {
        return io.nowPlaying();
    }

    @Override
    public void readAhead(long bytes) {
        io.readAhead(bytes);
    }

    @Override
    public void close() throws IOException {
        io.close();
    }
}
