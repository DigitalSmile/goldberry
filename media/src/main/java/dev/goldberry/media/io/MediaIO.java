package dev.goldberry.media.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/// A readable, optionally seekable stream of bytes: everything a container
/// ever reads.
///
/// No native code reads a byte. The demuxers are Java and read through this
/// interface, so a file, an HTTP response and an application's own encrypted
/// container all look alike to them. Because of that, the JDK supplies TLS,
/// proxies and HTTP/2, and a network test needs no server.
///
/// ## Threading
///
/// An instance is read by one thread at a time: the thread that is demuxing.
/// [#close()] is the exception. It may be called from any thread **while a read
/// is blocked**, and it must make that read end. For a
/// [java.nio.channels.FileChannel] this is automatic: closing it throws
/// [java.nio.channels.AsynchronousCloseException] in the blocked reader. It is how
/// the Engine aborts an open or a read that is waiting on the network. The
/// container then reports [dev.goldberry.media.MediaError.Aborted].
///
/// ## Positions
///
/// Positions are byte offsets from the start of the resource. [#seek(long)] is
/// absolute; a demuxer that skips ahead seeks to where it worked out it wants
/// to be.
///
/// Read more:
/// [Tracks and the network](https://goldberry.dev/docs/components/media.html#tracks-subtitles-and-the-network).
public interface MediaIO extends Closeable {

    /// Reads up to `target.remaining()` bytes into `target`, advancing its position.
    ///
    /// May read fewer bytes than asked for, which is not an end of stream: the
    /// reader asks again. Blocks until at least one byte is available, the stream ends, or
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
    /// Empty for an endless stream or a response with no `Content-Length`. A
    /// container asks for it to find its tail, where Matroska and MP4 may keep
    /// their indexes.
    OptionalLong size();

    /// Whether [#seek] may be called.
    ///
    /// A live radio stream, or an HTTP server that ignores `Range`, is not
    /// seekable. A container then reads the stream front to back and refuses a
    /// seek.
    default boolean isSeekable() {
        return true;
    }

    /// Whether the stream is live: it has no end to seek to and no length, only a
    /// "now", as an internet radio station has. A player shows `LIVE` for one.
    ///
    /// Not the same as unseekable. An HTTP server that ignores `Range` serves a
    /// file that cannot be sought but still ends.
    default boolean isLive() {
        return false;
    }

    /// The parts of the resource at hand without another request, in order and
    /// disjoint: what a network stream has fetched ahead and not yet thrown away.
    ///
    /// Empty by default, and for a local file, where every byte is at hand and a
    /// list saying so would say nothing. `HttpIO` reports its read-ahead cache,
    /// which a player's seek bar shows as `bufferedRanges`.
    ///
    /// Called from any thread, while another reads.
    default List<ByteRange> buffered() {
        return List.of();
    }

    /// How far past the reader to fetch, in bytes: a hint the Engine gives once
    /// the source is open and its bitrate known, so that a fast stream has as
    /// many seconds in hand as a slow one.
    ///
    /// Ignored by default, and by a local file, where there is nothing to
    /// fetch. `HttpIO` keeps it between its [HttpIO.Options#readAhead()] and
    /// what its cache leaves room for. Called from any thread, while another
    /// reads.
    default void readAhead(long bytes) {}

    /// What the stream says is playing now, when it says: the `StreamTitle` of an
    /// ICY radio stream, for instance. Empty for a stream that carries no such
    /// line.
    ///
    /// Called from any thread, while another reads. The Engine reads it after
    /// each packet, and publishes a change as a new status.
    default Optional<String> nowPlaying() {
        return Optional.empty();
    }
}
