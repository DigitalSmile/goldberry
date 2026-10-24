package dev.goldberry.media.container.flac;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.Chapter;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.Track;
import dev.goldberry.media.chapter.VorbisComments;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.Source;

/// A bare FLAC file: `fLaC`, the metadata blocks, then frames with nothing
/// between them, read in Java.
///
/// FLAC has no container framing: a frame's length is not written anywhere, so
/// the next frame is found by its sync code and a header that parses and
/// checks, and the bytes before it are this frame. The STREAMINFO block says
/// the rate, the channels, the depth and the total samples; a SEEKTABLE, where
/// there is one, says where some samples' frames start, and a seek without one
/// estimates the byte from the sample count and walks to the next frame. A
/// PICTURE block is the cover art, an attached-picture track handed over as
/// one packet, and the VORBIS_COMMENT block's `CHAPTERnnn` comments are the
/// chapters.
///
/// Timestamps are sample counts (time base `1 / rate`), from the frame header's
/// own number: the frame's index times the fixed block size, or the sample
/// number of a variable-blocksize stream. The decoder is handed `fLaC` and the
/// STREAMINFO block as the codec's record, as the other containers hand it.
public final class FlacContainer implements Container {

    private static final byte[] MARKER = "fLaC".getBytes(StandardCharsets.US_ASCII);
    private static final int BLOCK_STREAMINFO = 0;
    private static final int BLOCK_SEEKTABLE = 3;
    private static final int BLOCK_VORBIS_COMMENT = 4;
    /// A comment block longer than this is skipped unread: chapters and tags
    /// are a few kilobytes, and the block is held on the heap while it is read.
    private static final int LARGEST_COMMENTS = 1024 * 1024;
    private static final int BLOCK_PICTURE = 6;
    private static final int STREAMINFO_SIZE = 34;
    /// The most a frame can be, when STREAMINFO does not say: FLAC's own bound.
    private static final int MAX_FRAME = 16 * 1024 * 1024;

    /// A seek point: where the frame holding `sample` starts, from the first frame.
    private record SeekPoint(long sample, long offset) {}

    /// The cover art, handed over once.
    @SuppressWarnings("ArrayRecordComponent")
    private record Picture(int index, Track track, byte[] data) {}

    private final ByteReader in;
    private final FrameScanner frames;
    private final MediaInfo info;
    private final Rational timeBase;
    private final int rate;
    private final int channels;
    private final long totalSamples;
    private final long dataStart;
    private final byte[] record;
    private final List<SeekPoint> seekPoints;
    private final @Nullable Picture picture;
    private final TrackParams.Audio params;
    private boolean audioSelected = true;
    private boolean pictureSelected = true;
    private boolean pictureSent;
    private boolean closed;

    private FlacContainer(Source source, MediaIO io) {
        this.in = new ByteReader(io);
        if (!Arrays.equals(in.bytes(4), MARKER)) {
            throw new MediaException(new MediaError.InvalidData("not a FLAC file"));
        }
        byte[] streamInfo = null;
        var points = new ArrayList<SeekPoint>();
        Picture cover = null;
        List<Chapter> chapters = List.of();
        var last = false;
        while (!last) {
            var header = in.u8();
            last = (header & 0x80) != 0;
            var type = header & 0x7F;
            var length = (int) in.unsignedBigEndian(3);
            switch (type) {
                case BLOCK_STREAMINFO -> {
                    if (length < STREAMINFO_SIZE) {
                        throw new MediaException(
                                new MediaError.InvalidData("a STREAMINFO block of " + length + " bytes"));
                    }
                    streamInfo = in.bytes(length);
                }
                case BLOCK_SEEKTABLE -> {
                    var count = length / 18;
                    for (var i = 0; i < count; i++) {
                        var sample = in.unsignedBigEndian(8);
                        var offset = in.unsignedBigEndian(8);
                        in.u16();
                        if (sample != -1L) {
                            points.add(new SeekPoint(sample, offset));
                        }
                    }
                    in.skip(length - count * 18L);
                }
                case BLOCK_VORBIS_COMMENT -> {
                    if (length <= LARGEST_COMMENTS) {
                        chapters = VorbisComments.chapters(VorbisComments.parse(in.bytes(length), 0));
                    } else {
                        in.skip(length);
                    }
                }
                case BLOCK_PICTURE -> {
                    var end = in.position() + length;
                    in.u32(); // picture type
                    var mediaType = new String(in.bytes((int) in.u32()), StandardCharsets.US_ASCII);
                    var description = new String(in.bytes((int) in.u32()), StandardCharsets.UTF_8);
                    in.u32(); // width
                    in.u32(); // height
                    in.u32(); // depth
                    in.u32(); // colours
                    var data = in.bytes((int) in.u32());
                    in.seek(end);
                    var codec = mediaType.equalsIgnoreCase("image/png")
                            ? CodecId.PNG
                            : mediaType.equalsIgnoreCase("image/jpeg") ? CodecId.MJPEG : null;
                    if (codec != null && cover == null && data.length > 0) {
                        var track = new Track(
                                1,
                                codec,
                                codec.ffmpegName(),
                                new TrackParams.Video(
                                        0,
                                        0,
                                        Optional.empty(),
                                        OptionalInt.empty(),
                                        OptionalInt.empty(),
                                        OptionalLong.empty()),
                                Optional.empty(),
                                false,
                                true,
                                Optional.empty(),
                                description.isEmpty() ? Optional.empty() : Optional.of(description));
                        cover = new Picture(1, track, data);
                    }
                }
                default -> in.skip(length);
            }
        }
        if (streamInfo == null) {
            throw new MediaException(new MediaError.InvalidData("a FLAC file with no STREAMINFO"));
        }
        var minBlock = (streamInfo[0] & 0xFF) << 8 | (streamInfo[1] & 0xFF);
        var maxBlock = (streamInfo[2] & 0xFF) << 8 | (streamInfo[3] & 0xFF);
        var maxFrame = (streamInfo[7] & 0xFF) << 16 | (streamInfo[8] & 0xFF) << 8 | (streamInfo[9] & 0xFF);
        this.rate = (streamInfo[10] & 0xFF) << 12 | (streamInfo[11] & 0xFF) << 4 | (streamInfo[12] & 0xFF) >>> 4;
        this.channels = ((streamInfo[12] & 0x0E) >>> 1) + 1;
        var bitsPerSample = ((streamInfo[12] & 0x01) << 4 | (streamInfo[13] & 0xFF) >>> 4) + 1;
        this.totalSamples = (long) (streamInfo[13] & 0x0F) << 32
                | (streamInfo[14] & 0xFFL) << 24
                | (streamInfo[15] & 0xFFL) << 16
                | (streamInfo[16] & 0xFFL) << 8
                | (streamInfo[17] & 0xFFL);
        if (rate <= 0) {
            throw new MediaException(new MediaError.InvalidData("a FLAC file whose STREAMINFO states no sample rate"));
        }
        this.timeBase = new Rational(1, rate);
        this.dataStart = in.position();
        this.frames = new FrameScanner(
                in, rate, channels, minBlock == maxBlock ? minBlock : 0, maxFrame > 0 ? maxFrame : MAX_FRAME);
        this.record = new byte[4 + 4 + STREAMINFO_SIZE];
        System.arraycopy(MARKER, 0, record, 0, 4);
        record[4] = (byte) 0x80;
        record[7] = (byte) STREAMINFO_SIZE;
        System.arraycopy(streamInfo, 0, record, 8, STREAMINFO_SIZE);
        this.seekPoints = List.copyOf(points);
        this.picture = cover;
        var duration = totalSamples > 0
                ? Optional.of(Duration.ofNanos(timeBase.toNanos(totalSamples)))
                : Optional.<Duration>empty();
        this.params = new TrackParams.Audio(
                rate,
                channels,
                Optional.of(bitsPerSample > 16 ? "s32" : "s16"),
                OptionalInt.empty(),
                OptionalLong.empty());
        var tracks = new ArrayList<Track>();
        tracks.add(new Track(0, CodecId.FLAC, CodecId.FLAC.ffmpegName(), params, duration, true, false));
        if (cover != null) {
            tracks.add(cover.track());
        }
        this.info = new MediaInfo(source, duration, tracks, io.isSeekable(), io.isLive(), chapters);
    }

    /// Opens a FLAC file over `io`, positioned at its start.
    public static FlacContainer open(Source source, MediaIO io) {
        return new FlacContainer(source, io);
    }

    /// Whether `head` begins a FLAC file: `fLaC`.
    public static boolean isMagic(byte[] head) {
        return head.length >= 4 && Arrays.equals(head, 0, 4, MARKER, 0, 4);
    }

    @Override
    public MediaInfo info() {
        return info;
    }

    @Override
    public void select(Set<Integer> streams) {
        audioSelected = streams.contains(0);
        pictureSelected = streams.contains(1);
    }

    @Override
    public @Nullable Packet read() {
        requireOpen();
        if (picture != null && pictureSelected && !pictureSent) {
            pictureSent = true;
            @SuppressWarnings("MustBeClosedChecker")
            var owned = Arena.ofShared();
            var data = owned.allocateFrom(ValueLayout.JAVA_BYTE, picture.data());
            return Packet.owning(data, picture.index(), 0, 0, 0, true, timeBase, owned::close);
        }
        if (!audioSelected) {
            return null;
        }
        var frame = frames.next();
        if (frame == null) {
            return null;
        }
        @SuppressWarnings("MustBeClosedChecker")
        var owned = Arena.ofShared();
        var data = owned.allocateFrom(ValueLayout.JAVA_BYTE, frame.bytes());
        return Packet.owning(
                data, 0, frame.firstSample(), frame.firstSample(), frame.blockSize(), true, timeBase, owned::close);
    }

    @Override
    public void seek(long positionNanos) {
        requireOpen();
        if (!in.isSeekable()) {
            throw new MediaException(new MediaError.Io("the source cannot seek"));
        }
        var target = Math.max(0, timeBase.fromNanos(positionNanos));
        long offset = 0;
        SeekPoint best = null;
        for (var point : seekPoints) {
            if (point.sample() <= target && (best == null || point.sample() > best.sample())) {
                best = point;
            }
        }
        if (best != null) {
            offset = best.offset();
        } else if (totalSamples > 0 && in.size().isPresent()) {
            // No table: estimate the byte from the sample, then back off until
            // the frame found there starts at or before the target. A constant
            // bit rate puts the estimate within a frame; a variable one takes
            // another round or two.
            var bytes = in.size().getAsLong() - dataStart;
            var estimate = Math.max(0, Math.min(bytes - 1, bytes * target / totalSamples));
            var back = Math.max(frames.maxFrame(), 4096L);
            while (true) {
                offset = Math.max(0, estimate - back);
                frames.restart(dataStart + offset);
                var found = frames.next();
                if (found == null || found.firstSample() <= target || offset == 0) {
                    break;
                }
                back *= 2;
            }
        }
        frames.restart(dataStart + offset);
        if (positionNanos <= 0) {
            pictureSent = false;
        }
    }

    @Override
    public Rational timeBase(int stream) {
        return timeBase;
    }

    @Override
    public DecoderRequest request(int stream) {
        if (stream != 0) {
            throw new IllegalArgumentException("no decodable track " + stream);
        }
        return new DecoderRequest(
                CodecId.FLAC, CodecId.FLAC.ffmpegName(), params, MemorySegment.ofArray(record), timeBase);
    }

    @Override
    public void close() {
        closed = true;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("container closed");
        }
    }
}
