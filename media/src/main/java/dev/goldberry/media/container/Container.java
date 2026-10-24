package dev.goldberry.media.container;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;

/// An open container over a [dev.goldberry.media.io.MediaIO]: what the Engine's
/// demux thread holds. Packets out, in file order, of the streams it was told
/// to read; a seek lands on the keyframe at or before a time.
///
/// Every container is Java: Matroska and WebM, MP4, Ogg, and the raw audio
/// streams. One thread uses a container at a time, the demux thread; closing
/// the `MediaIO` under it from another thread ends a read with
/// [dev.goldberry.media.MediaError.Aborted], which is how the Engine stops a
/// read that is waiting on the network.
///
/// Opened by [Containers#open], which sniffs the first bytes.
public interface Container extends AutoCloseable {

    /// What the container holds.
    MediaInfo info();

    /// Reads only `streams` from here on. Packets of any other stream are not
    /// handed over, and where the container can, not read either.
    void select(Set<Integer> streams);

    /// The next packet of a selected stream, or null at the end of the source.
    ///
    /// The packet owns native memory and frees it when closed. It may be closed
    /// on any thread.
    ///
    /// @throws MediaException [dev.goldberry.media.MediaError.InvalidData] when the
    ///                        bytes are not what the container promised,
    ///                        [dev.goldberry.media.MediaError.Io] when a read fails,
    ///                        [dev.goldberry.media.MediaError.Aborted] when the
    ///                        `MediaIO` was closed during the read
    @Nullable
    Packet read();

    /// Moves to the keyframe at or before `positionNanos`, in every selected
    /// stream: the next [#read()] starts there. The Engine decodes from the
    /// keyframe and drops what is before the target itself.
    ///
    /// @throws MediaException when the source cannot seek, or the read it takes
    ///                        fails
    void seek(long positionNanos);

    /// The unit of `stream`'s packet timestamps.
    Rational timeBase(int stream);

    /// `stream` described for a [dev.goldberry.media.codec.DecoderProvider]. The
    /// extradata is valid while this container is open.
    DecoderRequest request(int stream);

    /// Frees what the container holds. Does not close the `MediaIO`, whose opener
    /// closes it after the container. Idempotent.
    @Override
    void close();
}
