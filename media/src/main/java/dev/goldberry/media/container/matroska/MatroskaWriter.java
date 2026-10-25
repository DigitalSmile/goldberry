package dev.goldberry.media.container.matroska;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// A Matroska stream of one track, written front to back: what a pipe into
/// another program carries, so nothing is ever gone back to.
///
/// The stream is the EBML header, a Segment of unknown size, Info, Tracks with
/// the one TrackEntry, and then Clusters of unknown size holding one
/// SimpleBlock per frame, unlaced. There are no Cues, no SeekHead and no
/// Duration: a reader learns where the stream ends by reaching it.
///
/// **Timing.** The `TimestampScale` is a microsecond, so a time keeps every
/// digit a microsecond holds. A block's time is relative to its Cluster's in a
/// signed 16-bit field, ±32 ms at this scale, so a frame whose time is further
/// from the open Cluster's than that starts a new Cluster at its own time. A
/// stream in decoding order whose pictures are reordered starts Clusters at
/// times that go back as well as forward, which Matroska's readers take: a
/// block's time is its Cluster's plus its own, wherever it is.
///
/// Not thread-safe: one thread writes a stream.
public final class MatroskaWriter {

    /// The stream's `TimestampScale`, in nanoseconds: a microsecond.
    public static final long TIMESTAMP_SCALE = 1_000;

    /// EBML's "unknown size", in its eight-byte form.
    private static final long UNKNOWN_SIZE = 0x01FF_FFFF_FFFF_FFFFL;

    private static final int TRACK_NUMBER = 1;

    /// The track a stream carries.
    public sealed interface Track {

        /// The `CodecID`, such as `V_MPEG4/ISO/AVC`.
        String codecId();

        /// The `CodecPrivate`, or empty for none.
        byte[] codecPrivate();
    }

    /// A video track.
    ///
    /// @param codecId      the `CodecID`
    /// @param codecPrivate the `CodecPrivate`, empty for none
    /// @param width        `PixelWidth`, or 0 to leave it out
    /// @param height       `PixelHeight`, or 0 to leave it out
    @SuppressWarnings("ArrayRecordComponent")
    public record Video(String codecId, byte[] codecPrivate, int width, int height) implements Track {

        public Video {
            Objects.requireNonNull(codecId, "codecId");
            codecPrivate = codecPrivate.clone();
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("a negative size: " + width + "×" + height);
            }
        }

        @Override
        public byte[] codecPrivate() {
            return codecPrivate.clone();
        }
    }

    /// An audio track.
    ///
    /// @param codecId           the `CodecID`
    /// @param codecPrivate      the `CodecPrivate`, empty for none
    /// @param samplingFrequency `SamplingFrequency` in hertz, or 0 to leave it out
    /// @param channels          `Channels`, or 0 to leave it out
    /// @param bitDepth          `BitDepth`, or 0 to leave it out
    @SuppressWarnings("ArrayRecordComponent")
    public record Audio(String codecId, byte[] codecPrivate, double samplingFrequency, int channels, int bitDepth)
            implements Track {

        public Audio {
            Objects.requireNonNull(codecId, "codecId");
            codecPrivate = codecPrivate.clone();
            if (!(samplingFrequency >= 0) || channels < 0 || bitDepth < 0) {
                throw new IllegalArgumentException(
                        "rate " + samplingFrequency + ", channels " + channels + ", depth " + bitDepth);
            }
        }

        @Override
        public byte[] codecPrivate() {
            return codecPrivate.clone();
        }
    }

    private final OutputStream out;
    private final byte[] blockHeader = new byte[4];
    private boolean headerWritten;
    /// The open Cluster's time in ticks, or -1 before the first.
    private long clusterTicks = -1;

    /// A writer onto `out`, which it neither buffers nor closes.
    public MatroskaWriter(OutputStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    /// Writes the EBML header, the Segment's start, Info and Tracks: everything
    /// before the first frame.
    ///
    /// @throws IllegalStateException when the header was written already
    /// @throws IOException           when `out` fails
    public void writeHeader(Track track) throws IOException {
        if (headerWritten) {
            throw new IllegalStateException("the header is written once");
        }
        headerWritten = true;
        var ebml = new Children()
                .unsigned(MatroskaIds.EBML_VERSION, 1)
                .unsigned(MatroskaIds.EBML_READ_VERSION, 1)
                .unsigned(MatroskaIds.EBML_MAX_ID_LENGTH, 4)
                .unsigned(MatroskaIds.EBML_MAX_SIZE_LENGTH, 8)
                .string(MatroskaIds.DOC_TYPE, "matroska")
                .unsigned(MatroskaIds.DOC_TYPE_VERSION, 4)
                .unsigned(MatroskaIds.DOC_TYPE_READ_VERSION, 2);
        var head = new Children().master(MatroskaIds.EBML, ebml);
        writeId(head.bytes, MatroskaIds.SEGMENT);
        writeSize(head.bytes, UNKNOWN_SIZE, 8);
        var info = new Children()
                .unsigned(MatroskaIds.TIMESTAMP_SCALE, TIMESTAMP_SCALE)
                .string(MatroskaIds.MUXING_APP, "goldberry")
                .string(MatroskaIds.WRITING_APP, "goldberry");
        head.master(MatroskaIds.INFO, info);
        head.master(MatroskaIds.TRACKS, new Children().master(MatroskaIds.TRACK_ENTRY, entry(track)));
        head.bytes.writeTo(out);
    }

    /// Writes one frame as a SimpleBlock, opening a Cluster first when the frame's
    /// time is too far from the open one's.
    ///
    /// @param data          the frame
    /// @param timestampNanos its time, from zero on, in nanoseconds; rounded to
    ///                       the nearest microsecond
    /// @param keyframe      whether decoding can start at it
    /// @throws IllegalArgumentException for a time before zero, which Matroska's
    ///                                  unsigned Cluster times cannot hold
    /// @throws IllegalStateException    before [#writeHeader]
    /// @throws IOException              when `out` fails
    public void writeFrame(byte[] data, long timestampNanos, boolean keyframe) throws IOException {
        if (!headerWritten) {
            throw new IllegalStateException("the header comes before the first frame");
        }
        if (timestampNanos < 0) {
            throw new IllegalArgumentException("a frame at " + timestampNanos + " ns, before zero");
        }
        var ticks = (timestampNanos + TIMESTAMP_SCALE / 2) / TIMESTAMP_SCALE;
        if (clusterTicks < 0 || ticks - clusterTicks > Short.MAX_VALUE || ticks - clusterTicks < Short.MIN_VALUE) {
            var cluster = new Children();
            writeId(cluster.bytes, MatroskaIds.CLUSTER);
            writeSize(cluster.bytes, UNKNOWN_SIZE, 8);
            cluster.unsigned(MatroskaIds.TIMESTAMP, ticks);
            cluster.bytes.writeTo(out);
            clusterTicks = ticks;
        }
        var relative = (int) (ticks - clusterTicks);
        // The track number as a one-byte size, the relative time, the flags.
        blockHeader[0] = (byte) (0x80 | TRACK_NUMBER);
        blockHeader[1] = (byte) (relative >> 8);
        blockHeader[2] = (byte) relative;
        blockHeader[3] = (byte) (keyframe ? 0x80 : 0x00);
        var element = new ByteArrayOutputStream(12);
        writeId(element, MatroskaIds.SIMPLE_BLOCK);
        writeSize(element, (long) blockHeader.length + data.length, 0);
        element.writeTo(out);
        out.write(blockHeader);
        out.write(data);
    }

    private static Children entry(Track track) {
        var entry = new Children()
                .unsigned(MatroskaIds.TRACK_NUMBER, TRACK_NUMBER)
                .unsigned(MatroskaIds.TRACK_UID, TRACK_NUMBER)
                .unsigned(MatroskaIds.FLAG_LACING, 0)
                .string(MatroskaIds.CODEC_ID, track.codecId());
        var codecPrivate = track.codecPrivate();
        if (codecPrivate.length > 0) {
            entry.binary(MatroskaIds.CODEC_PRIVATE, codecPrivate);
        }
        switch (track) {
            case Video video -> {
                entry.unsigned(MatroskaIds.TRACK_TYPE, MatroskaIds.TRACK_TYPE_VIDEO);
                var settings = new Children();
                if (video.width() > 0) {
                    settings.unsigned(MatroskaIds.PIXEL_WIDTH, video.width());
                }
                if (video.height() > 0) {
                    settings.unsigned(MatroskaIds.PIXEL_HEIGHT, video.height());
                }
                entry.master(MatroskaIds.VIDEO, settings);
            }
            case Audio audio -> {
                entry.unsigned(MatroskaIds.TRACK_TYPE, MatroskaIds.TRACK_TYPE_AUDIO);
                var settings = new Children();
                if (audio.samplingFrequency() > 0) {
                    settings.floating(MatroskaIds.SAMPLING_FREQUENCY, audio.samplingFrequency());
                }
                if (audio.channels() > 0) {
                    settings.unsigned(MatroskaIds.CHANNELS, audio.channels());
                }
                if (audio.bitDepth() > 0) {
                    settings.unsigned(MatroskaIds.BIT_DEPTH, audio.bitDepth());
                }
                entry.master(MatroskaIds.AUDIO, settings);
            }
        }
        return entry;
    }

    /// The children of a master element, written as they are added.
    private static final class Children {

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Children unsigned(long id, long value) {
            var length = 1;
            while (length < 8 && (value >>> (8 * length)) != 0) {
                length++;
            }
            var data = new byte[length];
            for (var i = 0; i < length; i++) {
                data[i] = (byte) (value >>> (8 * (length - 1 - i)));
            }
            return binary(id, data);
        }

        Children floating(long id, double value) {
            var bits = Double.doubleToLongBits(value);
            var data = new byte[8];
            for (var i = 0; i < 8; i++) {
                data[i] = (byte) (bits >>> (8 * (7 - i)));
            }
            return binary(id, data);
        }

        Children string(long id, String value) {
            return binary(id, value.getBytes(StandardCharsets.UTF_8));
        }

        Children master(long id, Children children) {
            return binary(id, children.bytes.toByteArray());
        }

        Children binary(long id, byte[] data) {
            writeId(bytes, id);
            writeSize(bytes, data.length, 0);
            bytes.write(data, 0, data.length);
            return this;
        }
    }

    /// `id` as EBML writes it: its bytes, length marker included, big-endian.
    private static void writeId(ByteArrayOutputStream to, long id) {
        var length = 1;
        while (length < 4 && (id >>> (8 * length)) != 0) {
            length++;
        }
        for (var i = length - 1; i >= 0; i--) {
            to.write((int) (id >>> (8 * i)));
        }
    }

    /// `size` as an EBML variable-length integer, in the fewest bytes that hold
    /// it, or in `length` bytes when that is not 0.
    private static void writeSize(ByteArrayOutputStream to, long size, int length) {
        var bytes = length;
        if (bytes == 0) {
            bytes = 1;
            // All ones in the value bits is reserved for "unknown".
            while (bytes < 8 && size >= (1L << (7 * bytes)) - 1) {
                bytes++;
            }
        }
        var marked = size | (1L << (7 * bytes));
        for (var i = bytes - 1; i >= 0; i--) {
            to.write((int) (marked >>> (8 * i)));
        }
    }
}
