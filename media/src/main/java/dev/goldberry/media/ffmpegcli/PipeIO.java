package dev.goldberry.media.ffmpegcli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.OptionalLong;

import dev.goldberry.media.io.MediaIO;

/// A program's output as a [MediaIO]: read front to back, never sought, with no
/// size until it ends.
final class PipeIO implements MediaIO {

    private final InputStream in;
    private final byte[] chunk = new byte[64 * 1024];
    private long position;

    PipeIO(InputStream in) {
        this.in = Objects.requireNonNull(in, "in");
    }

    @Override
    public int read(ByteBuffer target) throws IOException {
        if (!target.hasRemaining()) {
            return 0;
        }
        int read;
        if (target.hasArray()) {
            read = in.read(target.array(), target.arrayOffset() + target.position(), target.remaining());
            if (read > 0) {
                target.position(target.position() + read);
            }
        } else {
            read = in.read(chunk, 0, Math.min(chunk.length, target.remaining()));
            if (read > 0) {
                target.put(chunk, 0, read);
            }
        }
        if (read > 0) {
            position += read;
        }
        return read;
    }

    @Override
    public void seek(long position) throws IOException {
        throw new IOException("a pipe cannot seek");
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public OptionalLong size() {
        return OptionalLong.empty();
    }

    @Override
    public boolean isSeekable() {
        return false;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
