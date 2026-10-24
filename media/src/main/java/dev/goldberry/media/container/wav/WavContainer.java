package dev.goldberry.media.container.wav;

import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.jspecify.annotations.Nullable;

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

/// WAV: RIFF with a `fmt ` chunk and a `data` chunk, read in Java.
///
/// One track of raw PCM, handed over in packets of [#FRAMES_PER_PACKET] frames
/// whose timestamps are frame counts (time base `1 / rate`). A seek lands on a
/// frame boundary. Integer PCM of 16 and 24 bits and 32-bit float are the
/// codecs the built-in PCM decoder plays; any other format tag or depth is
/// named as FFmpeg names it (`pcm_alaw`, `pcm_u8`) and is a track nothing here
/// decodes, which is how a provider for it is asked.
public final class WavContainer implements Container {

    /// How many frames one packet carries: a quarter second at 16 kHz, 85 ms
    /// at 48 kHz, which is the order FFmpeg's demuxer chose.
    static final int FRAMES_PER_PACKET = 4096;

    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_FLOAT = 3;
    private static final int FORMAT_ALAW = 6;
    private static final int FORMAT_MULAW = 7;
    private static final int FORMAT_EXTENSIBLE = 0xFFFE;

    private final ByteReader in;
    private final MediaInfo info;
    private final Rational timeBase;
    private final int blockAlign;
    private final long dataStart;
    private final long dataEnd;
    private final CodecId codec;
    private final String codecName;
    private final TrackParams.Audio params;
    private long nextFrame;
    private boolean selected = true;
    private boolean closed;

    private WavContainer(Source source, MediaIO io) {
        this.in = new ByteReader(io);
        if (!"RIFF".equals(fourcc())) {
            throw new MediaException(new MediaError.InvalidData("not a RIFF file"));
        }
        in.u32LittleEndian();
        if (!"WAVE".equals(fourcc())) {
            throw new MediaException(new MediaError.InvalidData("a RIFF file that is not WAVE"));
        }
        var formatTag = -1;
        var channels = 0;
        var rate = 0;
        var bits = 0;
        var align = 0;
        long start = -1;
        long end = -1;
        while (start < 0 && !in.atEnd()) {
            var id = fourcc();
            var size = in.u32LittleEndian();
            var chunkStart = in.position();
            if (id.equals("fmt ")) {
                formatTag = in.u16LittleEndian();
                channels = in.u16LittleEndian();
                rate = (int) in.u32LittleEndian();
                in.u32LittleEndian(); // byte rate
                align = in.u16LittleEndian();
                bits = in.u16LittleEndian();
                if (formatTag == FORMAT_EXTENSIBLE && size >= 26) {
                    in.u16LittleEndian(); // extension size
                    in.u16LittleEndian(); // valid bits
                    in.u32LittleEndian(); // channel mask
                    formatTag = in.u16LittleEndian(); // the sub-format GUID's first two bytes
                }
                in.seek(chunkStart + size + (size & 1));
            } else if (id.equals("data")) {
                start = chunkStart;
                // A size of 0 or all ones is a stream whose writer never came
                // back to fill it in: the data runs to the end.
                var known = size != 0 && size != 0xFFFFFFFFL;
                end = known ? start + size : in.size().orElse(-1);
            } else {
                in.seek(chunkStart + size + (size & 1));
            }
        }
        if (start < 0 || formatTag < 0) {
            throw new MediaException(new MediaError.InvalidData("a WAV with no fmt or data chunk"));
        }
        if (channels <= 0 || rate <= 0) {
            throw new MediaException(
                    new MediaError.InvalidData("a WAV with " + channels + " channels at " + rate + " Hz"));
        }
        this.codec = switch (formatTag) {
            case FORMAT_PCM ->
                switch (bits) {
                    case 16 -> CodecId.PCM_S16LE;
                    case 24 -> CodecId.PCM_S24LE;
                    default -> CodecId.UNKNOWN;
                };
            case FORMAT_FLOAT -> bits == 32 ? CodecId.PCM_F32LE : CodecId.UNKNOWN;
            default -> CodecId.UNKNOWN;
        };
        this.codecName = codec != CodecId.UNKNOWN
                ? codec.ffmpegName()
                : switch (formatTag) {
                    case FORMAT_PCM -> bits == 8 ? "pcm_u8" : bits == 32 ? "pcm_s32le" : "pcm_s" + bits + "le";
                    case FORMAT_FLOAT -> "pcm_f64le";
                    case FORMAT_ALAW -> "pcm_alaw";
                    case FORMAT_MULAW -> "pcm_mulaw";
                    default -> "wav_0x" + Integer.toHexString(formatTag);
                };
        this.blockAlign = align > 0 ? align : Math.max(1, channels * ((bits + 7) / 8));
        this.dataStart = start;
        this.dataEnd = end;
        this.timeBase = new Rational(1, rate);
        var frames = end >= 0 ? (end - start) / blockAlign : -1;
        var duration =
                frames >= 0 ? Optional.of(Duration.ofNanos(timeBase.toNanos(frames))) : Optional.<Duration>empty();
        var sampleFormat = switch (codec) {
            case PCM_S16LE -> Optional.of("s16");
            case PCM_S24LE -> Optional.of("s32");
            case PCM_F32LE -> Optional.of("flt");
            default -> Optional.<String>empty();
        };
        this.params = new TrackParams.Audio(
                rate, channels, sampleFormat, OptionalInt.empty(), OptionalLong.of((long) rate * blockAlign * 8));
        var track = new Track(0, codec, codecName, params, duration, true, false);
        this.info = new MediaInfo(source, duration, List.of(track), io.isSeekable(), io.isLive());
        in.seek(dataStart);
    }

    /// Opens a WAV over `io`, positioned at its start.
    public static WavContainer open(Source source, MediaIO io) {
        return new WavContainer(source, io);
    }

    /// Whether `head` begins a WAV: `RIFF`, four bytes, `WAVE`.
    public static boolean isMagic(byte[] head) {
        return head.length >= 12
                && new String(head, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(head, 8, 4, StandardCharsets.US_ASCII).equals("WAVE");
    }

    @Override
    public MediaInfo info() {
        return info;
    }

    @Override
    public void select(Set<Integer> streams) {
        selected = streams.contains(0);
    }

    @Override
    public @Nullable Packet read() {
        if (closed) {
            throw new IllegalStateException("container closed");
        }
        if (!selected) {
            return null;
        }
        var position = in.position();
        var left = dataEnd >= 0 ? dataEnd - position : Long.MAX_VALUE;
        if (left < blockAlign || in.atEnd()) {
            return null;
        }
        var frames = (int) Math.min(FRAMES_PER_PACKET, left / blockAlign);
        var bytes = (long) frames * blockAlign;
        if (dataEnd < 0) {
            // An unsized stream: take what is there, a whole number of frames.
            bytes = available(bytes);
            frames = (int) (bytes / blockAlign);
            if (frames == 0) {
                return null;
            }
            bytes = (long) frames * blockAlign;
        }
        @SuppressWarnings("MustBeClosedChecker")
        var arena = Arena.ofShared();
        var data = in.segment(arena, bytes);
        var pts = nextFrame;
        nextFrame += frames;
        return Packet.owning(data, 0, pts, pts, frames, true, timeBase, arena::close);
    }

    /// How many of `wanted` bytes can be read before the stream ends, for a
    /// stream whose length nobody stated: read ahead one byte at a time is too
    /// slow, so this asks for the whole packet and trims to what arrived.
    private long available(long wanted) {
        var count = 0L;
        while (count < wanted && !in.atEnd()) {
            // ByteReader buffers 64 KB; atEnd() fills it, so stepping by one is
            // a field read after the first.
            in.peek();
            count++;
            in.skip(1);
        }
        in.seek(in.position() - count);
        return count;
    }

    @Override
    public void seek(long positionNanos) {
        if (closed) {
            throw new IllegalStateException("container closed");
        }
        var frame = Math.max(0, timeBase.fromNanos(positionNanos));
        var frames = dataEnd >= 0 ? (dataEnd - dataStart) / blockAlign : Long.MAX_VALUE;
        frame = Math.min(frame, frames);
        in.seek(dataStart + frame * blockAlign);
        nextFrame = frame;
    }

    @Override
    public Rational timeBase(int stream) {
        return timeBase;
    }

    @Override
    public DecoderRequest request(int stream) {
        if (stream != 0) {
            throw new IllegalArgumentException("no track " + stream);
        }
        return new DecoderRequest(codec, codecName, params, java.lang.foreign.MemorySegment.NULL, timeBase);
    }

    @Override
    public void close() {
        closed = true;
    }

    private String fourcc() {
        return new String(in.bytes(4), StandardCharsets.US_ASCII);
    }
}
