package dev.goldberry.media.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalLong;

/// A local file as a [MediaIO], over a [FileChannel].
///
/// Local playback goes through the same callback path as the network, which
/// leaves one I/O path to test. The size is
/// read once at open. A file that grows while it plays, such as a recording still
/// being written, is read to the length it had then.
///
/// Closing the channel from another thread ends a read blocked in it with
/// [java.nio.channels.AsynchronousCloseException]. That is the abort
/// [MediaIO#close()] promises.
///
/// Read more:
/// [Tracks and the network](https://goldberry.dev/docs/components/media.html#tracks-subtitles-and-the-network).
public final class FileIO implements MediaIO {

    private final FileChannel channel;
    private final long size;

    private FileIO(FileChannel channel, long size) {
        this.channel = channel;
        this.size = size;
    }

    /// Opens `path` for reading.
    ///
    /// @throws IOException when the file cannot be opened
    public static FileIO open(Path path) throws IOException {
        var channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
            return new FileIO(channel, channel.size());
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    @Override
    public int read(ByteBuffer target) throws IOException {
        return channel.read(target);
    }

    @Override
    public void seek(long position) throws IOException {
        if (position < 0) {
            throw new IOException("negative position " + position);
        }
        channel.position(position);
    }

    @Override
    public long position() {
        try {
            return channel.position();
        } catch (IOException e) {
            // Only a closed channel answers this with an exception, and a closed
            // stream has no position worth reporting; the next read says why.
            return -1;
        }
    }

    @Override
    public OptionalLong size() {
        return OptionalLong.of(size);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
