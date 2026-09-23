package io.github.digitalsmile.goldberry.media.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.OptionalLong;

/// A readable, optionally seekable stream of bytes: everything FFmpeg ever reads.
///
/// FFmpeg is built without its network layer and without a single protocol
/// (`docs/goldberry-media.md` §4). The demuxer reads through a custom
/// `AVIOContext` whose two callbacks land here, so a file, an HTTP response and an
/// application's own encrypted container all look alike to it. Because of that,
/// the JDK supplies TLS, proxies and HTTP/2, and a network test needs no server.
///
/// ## Threading
///
/// An instance is read by one thread at a time: the thread that is demuxing.
/// [#close()] is the exception. It may be called from any thread **while a read
/// is blocked**, and it must make that read end. For a
/// [java.nio.channels.FileChannel] this is automatic: closing it throws
/// [java.nio.channels.AsynchronousCloseException] in the blocked reader. It is how
/// the Engine aborts an open or a read that is waiting on the network. The pending
/// callback then answers FFmpeg with `AVERROR_EXIT`.
///
/// ## Positions
///
/// Positions are byte offsets from the start of the resource. [#seek(long)] is
/// absolute. The relative seeks FFmpeg asks for are resolved against
/// [#position()] before they reach an implementation.
public interface MediaIO extends Closeable {

    /// Reads up to `target.remaining()` bytes into `target`, advancing its position.
    ///
    /// May read fewer bytes than asked for, which is not an end of stream: FFmpeg
    /// asks again. Blocks until at least one byte is available, the stream ends, or
    /// the stream is closed.
    ///
    /// @return the number of bytes read, or `-1` at the end of the stream
    /// @throws IOException when the read fails; the demuxer sees an I/O error
    int read(ByteBuffer target) throws IOException;

    /// Moves to an absolute byte offset.
    ///
    /// Only called when [#isSeekable()] is true. A position past the end is not an
    /// error here; the next read answers `-1`.
    ///
    /// @throws IOException when the move fails
    void seek(long position) throws IOException;

    /// The current byte offset: where the next [#read] starts.
    long position();

    /// The total size in bytes, when it is known.
    ///
    /// Empty for an endless stream or a response with no `Content-Length`. FFmpeg
    /// asks for it with `AVSEEK_SIZE` to find a container's tail, where Matroska
    /// and MP4 keep their indexes.
    OptionalLong size();

    /// Whether [#seek] may be called.
    ///
    /// A live radio stream, or an HTTP server that ignores `Range`, is not
    /// seekable. FFmpeg is then given no seek callback at all and plays the stream
    /// front to back.
    default boolean isSeekable() {
        return true;
    }
}
