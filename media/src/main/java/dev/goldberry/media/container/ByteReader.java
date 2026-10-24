package dev.goldberry.media.container;

import java.io.EOFException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalLong;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.io.MediaIO;

/// Buffered, positioned reads over a [MediaIO], for the demuxers.
///
/// A demuxer reads headers a few bytes at a time and payloads by the kilobyte;
/// the buffer makes the first cheap and the second a copy. The position is the
/// stream's, in bytes from its start, whatever the buffer holds. Seeks within
/// the buffer are free; others go to the `MediaIO`.
///
/// Every failure is the Engine's: an I/O error is [MediaError.Io], a `MediaIO`
/// closed under a read is [MediaError.Aborted], and bytes that run out where a
/// container promised more are [MediaError.InvalidData], unless the caller asks
/// [#atEnd()] first, which is how a demuxer tells a clean end from a cut file.
public final class ByteReader {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final MediaIO io;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    /// The stream position of `buffer[0]`.
    private long bufferStart;
    private int bufferLength;
    private int bufferPosition;
    private boolean ended;

    public ByteReader(MediaIO io) {
        this.io = Objects.requireNonNull(io, "io");
        this.bufferStart = io.position();
    }

    /// The stream the bytes come from.
    public MediaIO io() {
        return io;
    }

    /// Where the next read starts, in bytes from the stream's start.
    public long position() {
        return bufferStart + bufferPosition;
    }

    /// The stream's size, when the `MediaIO` knows it.
    public OptionalLong size() {
        return io.size();
    }

    /// Whether the stream can be positioned.
    public boolean isSeekable() {
        return io.isSeekable();
    }

    /// Whether the next read would find nothing: the stream's end.
    public boolean atEnd() {
        if (bufferPosition < bufferLength) {
            return false;
        }
        if (ended) {
            return true;
        }
        fill();
        return bufferPosition >= bufferLength;
    }

    /// Moves to `position`.
    ///
    /// @throws MediaException when the stream cannot seek, or the seek fails
    public void seek(long position) {
        if (position < 0) {
            throw new IllegalArgumentException("position " + position);
        }
        if (position >= bufferStart && position <= bufferStart + bufferLength) {
            bufferPosition = (int) (position - bufferStart);
            return;
        }
        if (!io.isSeekable()) {
            throw new MediaException(new MediaError.Io("the source cannot seek"));
        }
        try {
            io.seek(position);
        } catch (IOException e) {
            throw failure(e);
        }
        bufferStart = position;
        bufferLength = 0;
        bufferPosition = 0;
        ended = false;
    }

    /// Passes over `count` bytes: a seek where the stream seeks, a read and
    /// discard where it does not.
    public void skip(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count " + count);
        }
        var target = position() + count;
        if (io.isSeekable() || target <= bufferStart + bufferLength) {
            seek(target);
            return;
        }
        var left = count;
        while (left > 0) {
            require(1);
            var step = (int) Math.min(left, bufferLength - bufferPosition);
            bufferPosition += step;
            left -= step;
        }
    }

    /// The next byte, 0–255.
    public int u8() {
        require(1);
        return buffer[bufferPosition++] & 0xFF;
    }

    /// The next byte, without taking it.
    public int peek() {
        require(1);
        return buffer[bufferPosition] & 0xFF;
    }

    /// The next `count` bytes as a big-endian unsigned integer, `count` ≤ 8.
    public long unsignedBigEndian(int count) {
        if (count < 0 || count > 8) {
            throw new IllegalArgumentException("count " + count);
        }
        var value = 0L;
        for (var i = 0; i < count; i++) {
            value = (value << 8) | u8();
        }
        return value;
    }

    /// The next `count` bytes as a big-endian signed integer, `count` ≤ 8.
    public long signedBigEndian(int count) {
        if (count == 0) {
            return 0;
        }
        var value = unsignedBigEndian(count);
        var shift = 64 - 8 * count;
        return (value << shift) >> shift;
    }

    /// The next 4 bytes as a big-endian unsigned integer.
    public long u32() {
        return unsignedBigEndian(4);
    }

    /// The next 2 bytes as a big-endian unsigned integer.
    public int u16() {
        return (int) unsignedBigEndian(2);
    }

    /// The next 4 bytes as a little-endian unsigned integer.
    public long u32LittleEndian() {
        var bytes = bytes(4);
        return (bytes[0] & 0xFFL) | (bytes[1] & 0xFFL) << 8 | (bytes[2] & 0xFFL) << 16 | (bytes[3] & 0xFFL) << 24;
    }

    /// The next 2 bytes as a little-endian unsigned integer.
    public int u16LittleEndian() {
        var low = u8();
        return low | u8() << 8;
    }

    /// The next `count` bytes, copied to the heap.
    public byte[] bytes(int count) {
        var out = new byte[count];
        readFully(out, 0, count);
        return out;
    }

    /// The next `count` bytes as a UTF-8 string, without a trailing NUL.
    public String utf8(int count) {
        var bytes = bytes(count);
        var length = bytes.length;
        while (length > 0 && bytes[length - 1] == 0) {
            length--;
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }

    /// The next `count` bytes, copied into native memory of `arena`.
    public MemorySegment segment(Arena arena, long count) {
        var segment = arena.allocate(count);
        var left = count;
        var offset = 0L;
        while (left > 0) {
            require(1);
            var step = (int) Math.min(left, bufferLength - bufferPosition);
            MemorySegment.copy(buffer, bufferPosition, segment, ValueLayout.JAVA_BYTE, offset, step);
            bufferPosition += step;
            offset += step;
            left -= step;
        }
        return segment;
    }

    /// Fills `target[offset..offset+length)` with the next bytes.
    public void readFully(byte[] target, int offset, int length) {
        var left = length;
        var at = offset;
        while (left > 0) {
            require(1);
            var step = Math.min(left, bufferLength - bufferPosition);
            System.arraycopy(buffer, bufferPosition, target, at, step);
            bufferPosition += step;
            at += step;
            left -= step;
        }
    }

    /// Makes sure at least one byte is buffered, reading if it must.
    ///
    /// @throws MediaException [MediaError.InvalidData] at the end of the stream
    private void require(int count) {
        if (bufferPosition + count <= bufferLength) {
            return;
        }
        fill();
        if (bufferPosition >= bufferLength) {
            throw new MediaException(new MediaError.InvalidData(
                    "the stream ends at byte " + position() + ", inside a structure the container promised"));
        }
    }

    /// Reads more into the buffer, keeping what is unread.
    private void fill() {
        if (ended) {
            return;
        }
        var unread = bufferLength - bufferPosition;
        if (unread > 0 && bufferPosition > 0) {
            System.arraycopy(buffer, bufferPosition, buffer, 0, unread);
        }
        bufferStart += bufferPosition;
        bufferPosition = 0;
        bufferLength = unread;
        var target = ByteBuffer.wrap(buffer, bufferLength, buffer.length - bufferLength);
        try {
            var read = io.read(target);
            if (read < 0) {
                ended = true;
                return;
            }
            bufferLength += read;
        } catch (IOException e) {
            throw failure(e);
        }
    }

    /// What a failed `MediaIO` call is to the Engine.
    public static MediaException failure(IOException e) {
        if (e instanceof ClosedChannelException || e instanceof AsynchronousCloseException) {
            return new MediaException(new MediaError.Aborted(), e);
        }
        if (e instanceof EOFException) {
            return new MediaException(new MediaError.InvalidData("the stream ended early"), e);
        }
        return new MediaException(new MediaError.Io(Objects.requireNonNullElse(e.getMessage(), e.toString())), e);
    }
}
