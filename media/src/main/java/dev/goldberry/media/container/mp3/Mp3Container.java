package dev.goldberry.media.container.mp3;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.Track;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.container.Container;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.Source;

/// A raw MPEG audio stream, the `.mp3` file, read in Java.
///
/// Opening skips the ID3v2 tags at the front, keeping their pictures as an
/// attached-picture track each, finds the first frame whose successor is a
/// frame too, and reads a Xing, Info or VBRI tag out of it when it is one:
/// the frame count, the byte count and the seek table, and from the LAME tag
/// behind it the encoder's delay and padding. That frame is silence and is
/// skipped, as FFmpeg skips it. Reading hands each frame over as one packet,
/// every one a keyframe, timed in samples of the stream's rate: the time base
/// is `1/sampleRate`, a packet's `pts` is the samples before it and its
/// duration the samples it decodes to. An ID3v1 tag in the last 128 bytes is
/// not audio and is stopped before.
///
/// Garbage between frames is passed over by scanning for the next header
/// whose successor is a header of the same stream, so a stray `FF Ex` inside
/// a frame's data is not taken for one. On a source that cannot seek, a false
/// sync costs the bytes up to the header that follows it.
///
/// **Gapless.** With a LAME tag, the packets are timed so that the music
/// starts at 0: the first packet is at minus the encoder's delay plus the
/// decoder's own 529 samples, and the Engine drops what is before zero, as it
/// does an MP4 track's priming before its edit list's start. The padding is
/// trimmed at the end, where the last packet's duration says how much of it
/// plays, as an Opus stream's last packet does. The duration is the frame
/// count's less both, which is what FFmpeg reports. Without a tag nothing is
/// known of either, and the first packet is at 0.
///
/// Seeking is an estimate: a byte position proportional to the target, by
/// the frame's arithmetic in a constant bit rate stream and by the Xing table
/// in a variable one, then a resync to the next frame header, whose packets
/// are timed from the frame index estimated the same way. In a constant bit
/// rate stream that is right to within a frame; by the table it is as good as
/// the table's hundred points, a frame or two; in a variable stream without a
/// table it is as wrong as the bit rate wanders, and FFmpeg's reader is no
/// better there. A frame header carries no time of its own.
public final class Mp3Container implements Container {

    private static final Logger LOG = Logs.of(Mp3Container.class);

    private static final int ID3V1_SIZE = 128;
    private static final int HEADER_SIZE = 4;

    /// The samples a layer III decoder's output lags its input by: the
    /// encoder's padding is counted against it at the end, as FFmpeg does.
    static final int DECODER_DELAY = 529;

    /// Whether `head`, a file's first bytes, begins an MP3: an ID3v2 tag, or a
    /// frame header with a sync and no reserved field.
    public static boolean isMagic(byte[] head) {
        if (head.length >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
            return true;
        }
        return head.length >= HEADER_SIZE && FrameHeader.parse(word(head)) != null;
    }

    /// One frame as read: where it started, its header, its bytes, and whether
    /// nothing follows it.
    @SuppressWarnings("ArrayRecordComponent")
    private record Frame(long at, FrameHeader header, byte[] bytes, boolean last) {}

    /// A picture of the ID3v2 tag, handed over as one packet.
    @SuppressWarnings("ArrayRecordComponent")
    private record Picture(int index, Track track, byte[] data) {}

    private final Source source;
    private final ByteReader in;
    private final List<Picture> pictures = new ArrayList<>();
    /// The version, layer and rate every frame of the stream has.
    private final FrameHeader shape;
    private final int samplesPerFrame;
    /// The first audio frame's bit rate, which a constant bit rate stream
    /// keeps: what a seek without a table counts bytes by.
    private final int frameBitRate;
    private final Rational timeBase;
    private final @Nullable VbrHeader vbr;
    /// The samples decoded before the music starts, which the first packet's
    /// time is moved back by: the encoder's delay and the decoder's, when a
    /// LAME tag states the delay, else 0.
    private final long priming;
    /// Where the stream's first frame starts, Xing frame or not: what the
    /// Xing table's positions count from.
    private final long firstFrameStart;
    /// Where the first audio frame starts.
    private final long audioStart;
    /// Where the audio ends, or -1 when nobody knows: the ID3v1 tag, or the
    /// end of the source.
    private final long audioEnd;
    private final CodecId codec;
    private final String codecName;
    private final TrackParams params;
    private final MediaInfo info;
    private final Deque<Packet> pending = new ArrayDeque<>();
    /// The first audio frame, read while opening and handed over first.
    private @Nullable Frame held;
    /// The header word of the frame after the last one read, when the
    /// successor check took it from the stream, else -1.
    private long ahead = -1;
    private long aheadAt;
    /// Whether [#ahead] followed a frame it belongs with: then it is a frame,
    /// whatever follows it.
    private boolean aheadConfirmed;
    private long nextPts;
    private @Nullable Set<Integer> selected;
    private boolean picturesSent;
    private boolean closed;

    private Mp3Container(Source source, MediaIO io) {
        this.source = source;
        this.in = new ByteReader(io);
        while (Id3v2.starts(in)) {
            var tag = Id3v2.read(in);
            for (var picture : tag.pictures()) {
                var index = 1 + pictures.size();
                var track = new Track(
                        index,
                        picture.codec(),
                        picture.codec().ffmpegName(),
                        new TrackParams.Video(
                                0, 0, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty()),
                        Optional.empty(),
                        false,
                        true,
                        Optional.empty(),
                        picture.description());
                pictures.add(new Picture(index, track, picture.data()));
            }
        }
        this.audioEnd = audioEnd();

        var first = nextFrame(null);
        if (first == null) {
            throw new MediaException(new MediaError.InvalidData("no MPEG audio frame in " + source.uri()));
        }
        this.shape = first.header();
        this.samplesPerFrame = shape.samples();
        this.timeBase = new Rational(1, shape.sampleRate());
        this.firstFrameStart = first.at();
        var tag = VbrHeader.parse(first.header(), first.bytes());
        this.vbr = tag.orElse(null);
        this.priming = vbr != null && (vbr.delay() > 0 || vbr.padding() > 0) ? vbr.delay() + DECODER_DELAY : 0;
        var audio = first;
        if (tag.isPresent()) {
            audio = nextFrame(shape);
        }
        this.audioStart = audio == null ? in.position() : audio.at();
        this.held = audio;
        this.nextPts = -priming;

        var header = audio == null ? first.header() : audio.header();
        this.frameBitRate = header.bitRate();
        this.codec = header.layer() == 3 ? CodecId.MP3 : CodecId.UNKNOWN;
        this.codecName = header.codecName();
        this.params = new TrackParams.Audio(
                header.sampleRate(),
                header.channels(),
                Optional.of("fltp"),
                OptionalInt.empty(),
                OptionalLong.of(bitRate(header)));
        var duration = duration(header);
        var tracks = new ArrayList<Track>();
        tracks.add(new Track(0, codec, codecName, params, duration, true, false, Optional.empty(), Optional.empty()));
        for (var picture : pictures) {
            tracks.add(picture.track());
        }
        this.info = new MediaInfo(source, duration, tracks, io.isSeekable(), io.isLive());
    }

    /// Opens an MP3 over `io`, positioned at its start.
    ///
    /// @throws MediaException [MediaError.InvalidData] when no frame is found,
    ///                        [MediaError.Io] when a read fails,
    ///                        [MediaError.Aborted] when `io` was closed during
    ///                        the open
    public static Mp3Container open(Source source, MediaIO io) {
        return new Mp3Container(source, io);
    }

    @Override
    public MediaInfo info() {
        return info;
    }

    @Override
    public void select(Set<Integer> streams) {
        selected = Set.copyOf(streams);
    }

    @Override
    public Rational timeBase(int stream) {
        return timeBase;
    }

    @Override
    public DecoderRequest request(int stream) {
        if (stream != 0) {
            throw new IllegalArgumentException("no decodable track " + stream + " in " + source.uri());
        }
        return new DecoderRequest(codec, codecName, params, MemorySegment.NULL, timeBase);
    }

    @Override
    public @Nullable Packet read() {
        requireOpen();
        if (!picturesSent) {
            picturesSent = true;
            for (var picture : pictures) {
                if (isSelected(picture.index())) {
                    pending.add(picturePacket(picture));
                }
            }
        }
        if (!pending.isEmpty()) {
            return pending.poll();
        }
        if (!isSelected(0)) {
            return null;
        }
        var frame = held;
        held = null;
        if (frame == null) {
            frame = nextFrame(shape);
        }
        return frame == null ? null : packet(frame);
    }

    @Override
    public void seek(long positionNanos) {
        requireOpen();
        if (!in.isSeekable()) {
            throw new MediaException(new MediaError.Io("the source cannot seek"));
        }
        pending.forEach(Packet::close);
        pending.clear();
        held = null;
        ahead = -1;
        if (positionNanos <= 0) {
            in.seek(audioStart);
            nextPts = -priming;
            picturesSent = false;
            return;
        }
        // In samples from the first frame's first, which is where the frame
        // arithmetic and the table count from.
        var target = timeBase.fromNanos(positionNanos) + priming;
        // A variable bit rate stream whose tag counted its frames and bytes.
        var tag = vbr != null && vbr.variable() && vbr.frames() > 0 && vbr.bytes() > 0 ? vbr : null;
        long frameIndex;
        double bytePosition;
        if (tag != null && tag.hasToc()) {
            // The table's word.
            var frames = tag.frames();
            var fraction = Math.min((double) target / (frames * samplesPerFrame), 1d);
            frameIndex = Math.min((long) (fraction * frames), frames - 1);
            bytePosition = firstFrameStart + tag.tocPosition(fraction) / 256 * tag.bytes();
        } else {
            // A constant bit rate stream is the frame's own arithmetic, as
            // FFmpeg seeks one, Info frame or not; a variable one without a
            // table has only its average.
            frameIndex = target / samplesPerFrame;
            var bytesPerFrame = tag != null
                    ? (double) (tag.bytes() - (audioStart - firstFrameStart)) / tag.frames()
                    : (double) samplesPerFrame * frameBitRate / 8 / shape.sampleRate();
            bytePosition = audioStart + frameIndex * bytesPerFrame;
        }
        // One frame earlier than the arithmetic says: a frame's main data may
        // begin in the frames before it (the bit reservoir), so the first frame
        // decoded after a seek is the one that primes the decoder and may yield
        // nothing. The Engine drops what is before the target anyway.
        if (frameIndex > 0 && !(tag != null && tag.hasToc())) {
            frameIndex--;
            bytePosition -= tag != null
                    ? (double) (tag.bytes() - (audioStart - firstFrameStart)) / tag.frames()
                    : (double) samplesPerFrame * frameBitRate / 8 / shape.sampleRate();
        }
        var end = audioEnd >= 0 ? audioEnd : in.size().orElse(Long.MAX_VALUE);
        in.seek(Math.clamp((long) bytePosition, audioStart, Math.max(audioStart, end)));
        nextPts = frameIndex * samplesPerFrame - priming;
    }

    /// The samples the first packet's time is moved back by, so that the music
    /// starts at 0.
    long priming() {
        return priming;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending.forEach(Packet::close);
        pending.clear();
    }

    /// The Xing, Info or VBRI tag the stream started with, for tests.
    Optional<VbrHeader> vbrHeader() {
        return Optional.ofNullable(vbr);
    }

    /// Where the first audio frame starts, for tests.
    long audioStart() {
        return audioStart;
    }

    // ---- frames ----------------------------------------------------------------

    /// The next frame of the stream, or null at its end. `shape` is the stream
    /// every header must belong to, or null before the first frame says.
    ///
    /// A header is believed when the header at the end of its frame belongs
    /// to the same stream, or when nothing follows it; one that came after a
    /// believed frame is believed too, and what follows it is for the next
    /// call to sort out. Anything else is a false sync, and the scan goes on
    /// from the byte after it.
    private @Nullable Frame nextFrame(@Nullable FrameHeader shape) {
        while (true) {
            long word;
            long at;
            boolean confirmed;
            if (ahead >= 0) {
                word = ahead;
                at = aheadAt;
                confirmed = aheadConfirmed;
                ahead = -1;
            } else {
                at = in.position();
                word = headerWord();
                confirmed = false;
                if (word < 0) {
                    return null;
                }
            }
            var header = FrameHeader.parse(word);
            if (header == null || !fits(header, shape)) {
                var from = at;
                do {
                    if ((audioEnd >= 0 && in.position() >= audioEnd) || in.atEnd()) {
                        return null;
                    }
                    word = ((word << 8) | in.u8()) & 0xFFFF_FFFFL;
                    at++;
                    header = FrameHeader.parse(word);
                } while (header == null || !fits(header, shape));
                LOG.debug("{}: {} bytes at byte {} are not MPEG audio", source.uri(), at - from, from);
                confirmed = false;
            }
            var body = body(header.bytes() - HEADER_SIZE);
            if (body == null) {
                return null;
            }
            var bytes = new byte[HEADER_SIZE + body.length];
            bytes[0] = (byte) (word >> 24);
            bytes[1] = (byte) (word >> 16);
            bytes[2] = (byte) (word >> 8);
            bytes[3] = (byte) word;
            System.arraycopy(body, 0, bytes, HEADER_SIZE, body.length);
            var nextAt = in.position();
            var next = headerWord();
            if (next < 0) {
                return new Frame(at, header, bytes, true);
            }
            var successor = FrameHeader.parse(next);
            if (confirmed || (successor != null && successor.sameStream(header))) {
                ahead = next;
                aheadAt = nextAt;
                aheadConfirmed = successor != null && successor.sameStream(header);
                return new Frame(at, header, bytes, false);
            }
            if (in.isSeekable()) {
                in.seek(at + 1);
                continue;
            }
            LOG.debug("{}: a false sync at byte {} on a source that cannot seek", source.uri(), at);
            ahead = next;
            aheadAt = nextAt;
            aheadConfirmed = false;
        }
    }

    /// The next four bytes as a header word, or -1 when fewer than four are
    /// left before the audio ends.
    private long headerWord() {
        if (audioEnd >= 0 && in.position() + HEADER_SIZE > audioEnd) {
            return -1;
        }
        var word = 0L;
        for (var i = 0; i < HEADER_SIZE; i++) {
            if (in.atEnd()) {
                return -1;
            }
            word = (word << 8) | in.u8();
        }
        return word;
    }

    /// The next `count` bytes, or null when the audio ends before them: a cut
    /// last frame, which is dropped as FFmpeg's parser drops it.
    private byte @Nullable [] body(int count) {
        var end = audioEnd >= 0 ? OptionalLong.of(audioEnd) : in.size();
        if (end.isPresent() && in.position() + count > end.getAsLong()) {
            return null;
        }
        try {
            return in.bytes(count);
        } catch (MediaException e) {
            if (e.error() instanceof MediaError.InvalidData) {
                return null;
            }
            throw e;
        }
    }

    /// Whether `header` belongs to the stream `shape` describes. Before the
    /// first frame is known, any header does.
    private static boolean fits(FrameHeader header, @Nullable FrameHeader shape) {
        return shape == null || header.sameStream(shape);
    }

    private Packet packet(Frame frame) {
        var header = frame.header();
        var duration = (long) header.samples();
        if (frame.last() && vbr != null && vbr.padding() > DECODER_DELAY) {
            var plays = duration - (vbr.padding() - DECODER_DELAY);
            if (plays > 0) {
                duration = plays;
            }
        }
        var pts = nextPts;
        nextPts += header.samples();
        @SuppressWarnings("MustBeClosedChecker")
        var owned = Arena.ofShared();
        var data = owned.allocateFrom(ValueLayout.JAVA_BYTE, frame.bytes());
        return Packet.owning(data, 0, pts, pts, duration, true, timeBase, owned::close);
    }

    private Packet picturePacket(Picture picture) {
        @SuppressWarnings("MustBeClosedChecker")
        var owned = Arena.ofShared();
        var data = owned.allocateFrom(ValueLayout.JAVA_BYTE, picture.data());
        return Packet.owning(data, picture.index(), 0, 0, 0, true, timeBase, owned::close);
    }

    // ---- the header -----------------------------------------------------------

    /// Where the audio ends: before an ID3v1 tag when the source can be looked
    /// at from the end, else at the source's end, else unknown.
    private long audioEnd() {
        var size = in.size();
        if (size.isEmpty()) {
            return -1;
        }
        var end = size.getAsLong();
        if (!in.isSeekable() || end < in.position() + ID3V1_SIZE) {
            return end;
        }
        var back = in.position();
        try {
            in.seek(end - ID3V1_SIZE);
            var tag = in.bytes(3);
            if (tag[0] == 'T' && tag[1] == 'A' && tag[2] == 'G') {
                return end - ID3V1_SIZE;
            }
            return end;
        } finally {
            in.seek(back);
        }
    }

    /// The stream's bit rate: the average a Xing or VBRI tag lets one work
    /// out, else the first frame's, which a constant bit rate stream keeps.
    private long bitRate(FrameHeader header) {
        if (vbr != null && vbr.variable() && vbr.frames() > 0 && vbr.bytes() > 0) {
            return vbr.bytes() * 8 * header.sampleRate() / (vbr.frames() * header.samples());
        }
        return header.bitRate();
    }

    /// How long the stream plays: the frame count's samples less the encoder's
    /// delay and padding when a tag says, else the audio bytes over the bit
    /// rate of a constant bit rate stream, else unknown.
    private Optional<Duration> duration(FrameHeader header) {
        var rate = header.sampleRate();
        if (vbr != null && vbr.frames() > 0) {
            var samples = vbr.frames() * header.samples() - vbr.delay() - vbr.padding();
            return samples > 0 ? Optional.of(nanos(samples, rate)) : Optional.empty();
        }
        if (audioEnd >= 0 && header.bitRate() > 0 && audioEnd > audioStart) {
            var bits = (audioEnd - audioStart) * 8;
            var seconds = bits / header.bitRate();
            var nanos = (bits % header.bitRate()) * 1_000_000_000L / header.bitRate();
            return Optional.of(Duration.ofSeconds(seconds, nanos));
        }
        return Optional.empty();
    }

    private static Duration nanos(long samples, int rate) {
        return Duration.ofSeconds(samples / rate, (samples % rate) * 1_000_000_000L / rate);
    }

    private static long word(byte[] head) {
        return (head[0] & 0xFFL) << 24 | (head[1] & 0xFFL) << 16 | (head[2] & 0xFFL) << 8 | (head[3] & 0xFFL);
    }

    private boolean isSelected(int index) {
        var set = selected;
        return set == null || set.contains(index);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("container closed");
        }
    }
}
