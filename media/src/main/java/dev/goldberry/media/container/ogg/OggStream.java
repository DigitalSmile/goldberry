package dev.goldberry.media.container.ogg;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.bitstream.OpusPackets;
import dev.goldberry.media.bitstream.XiphLacing;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ogg.VorbisHeaders.Identification;

/// One logical stream of an Ogg file: what its first packet said it is, the
/// headers that follow before the audio, and the reading state a seek
/// resets.
///
/// The first packet names the codec (RFC 7845 §5.1 `OpusHead`, Vorbis I §4.2
/// `\x01vorbis`, the FLAC-to-Ogg mapping's `\x7FFLAC`) and how many header
/// packets come after it: Opus one, the tags; Vorbis two, the comment and
/// the setup; FLAC as many as its mapping header counts, or until the first
/// frame when it counts none. What a decoder is handed as extradata is what
/// Matroska hands it for the same codec, so one decoder reads both: the
/// `OpusHead` packet whole, the three Vorbis headers Xiph-laced, and a FLAC
/// stream header of `fLaC` and the `STREAMINFO` block.
final class OggStream {

    private static final byte[] OPUS_HEAD = "OpusHead".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FLAC_MAPPING = {0x7F, 'F', 'L', 'A', 'C'};
    private static final byte[] SKELETON = "fishead".getBytes(StandardCharsets.US_ASCII);

    /// The sample position of a packet nobody has timed yet.
    static final long UNKNOWN = Long.MIN_VALUE;

    final long serial;
    final int index;
    final CodecId codec;
    final String name;
    final TrackParams params;
    /// The granule rate: samples per second, Opus's 48 000 whatever the input.
    final int rate;
    final Rational timeBase;
    /// Samples at the start the granule position counts and the decoder drops.
    final int preSkip;
    /// Whether the file's own metadata says to leave the stream alone: a
    /// Skeleton stream.
    final boolean ignored;

    private @Nullable Identification vorbis;
    private final List<byte[]> headers = new ArrayList<>();
    /// Header packets still to come after the first, or -1 while they are told
    /// apart from frames by their first byte.
    private int headersLeft;
    private PacketTiming timing;
    private byte[] extradata;

    /// The head of a packet that continues on the next page.
    byte @Nullable [] fragment;
    /// Where the next packet starts, in samples of the granule's count, or
    /// [#UNKNOWN] until a page's granule position says.
    long next = UNKNOWN;

    private OggStream(
            long serial,
            int index,
            CodecId codec,
            String name,
            TrackParams params,
            int rate,
            int preSkip,
            int headersLeft,
            PacketTiming timing,
            byte[] extradata,
            boolean ignored) {
        this.serial = serial;
        this.index = index;
        this.codec = codec;
        this.name = name;
        this.params = params;
        this.rate = rate;
        this.timeBase = new Rational(1, rate);
        this.preSkip = preSkip;
        this.headersLeft = headersLeft;
        this.timing = timing;
        this.extradata = extradata;
        this.ignored = ignored;
    }

    /// The stream `first`, the packet on its BOS page, begins.
    ///
    /// @throws MediaException [MediaError.InvalidData] for a header of a known
    ///                        codec that does not parse
    static OggStream of(long serial, int index, byte[] first) {
        if (startsWith(first, OPUS_HEAD)) {
            return opus(serial, index, first);
        }
        if (VorbisHeaders.isHeader(first, VorbisHeaders.IDENTIFICATION)) {
            return vorbis(serial, index, first);
        }
        if (startsWith(first, FLAC_MAPPING)) {
            return flac(serial, index, first);
        }
        return new OggStream(
                serial,
                index,
                CodecId.UNKNOWN,
                nameOf(first),
                new TrackParams.Other(MediaType.DATA),
                1_000_000,
                0,
                0,
                new PacketTiming.None(),
                new byte[0],
                startsWith(first, SKELETON));
    }

    private static OggStream opus(long serial, int index, byte[] head) {
        if (head.length < 19) {
            throw invalid("an OpusHead of " + head.length + " bytes");
        }
        var channels = head[9] & 0xFF;
        var preSkip = (head[10] & 0xFF) | (head[11] & 0xFF) << 8;
        if (channels == 0) {
            throw invalid("an OpusHead with no channels");
        }
        var params = new TrackParams.Audio(
                OpusPackets.SAMPLE_RATE, channels, Optional.of("fltp"), OptionalInt.empty(), OptionalLong.empty());
        return new OggStream(
                serial,
                index,
                CodecId.OPUS,
                CodecId.OPUS.ffmpegName(),
                params,
                OpusPackets.SAMPLE_RATE,
                preSkip,
                1,
                new PacketTiming.Opus(),
                head,
                false);
    }

    private static OggStream vorbis(long serial, int index, byte[] identification) {
        Identification parsed;
        try {
            parsed = VorbisHeaders.identification(identification);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
        var params = new TrackParams.Audio(
                parsed.rate(), parsed.channels(), Optional.of("fltp"), OptionalInt.empty(), OptionalLong.empty());
        var stream = new OggStream(
                serial,
                index,
                CodecId.VORBIS,
                CodecId.VORBIS.ffmpegName(),
                params,
                parsed.rate(),
                0,
                2,
                new PacketTiming.None(),
                new byte[0],
                false);
        stream.vorbis = parsed;
        stream.headers.add(identification);
        return stream;
    }

    private static OggStream flac(long serial, int index, byte[] mapping) {
        // 0x7F FLAC, major, minor, the header count (big-endian), fLaC, then a
        // metadata block header and STREAMINFO.
        var prefix = FLAC_MAPPING.length + 1 + 1 + 2;
        var blockHeader = prefix + FlacHeaders.MARKER.length;
        var info = blockHeader + 4;
        if (mapping.length < info + FlacHeaders.STREAM_INFO_SIZE || !FlacHeaders.isMarker(mapping, prefix)) {
            throw invalid("a FLAC mapping header of " + mapping.length + " bytes");
        }
        var count = (mapping[7] & 0xFF) << 8 | (mapping[8] & 0xFF);
        FlacHeaders.StreamInfo parsed;
        try {
            parsed = FlacHeaders.streamInfo(mapping, info);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
        var params = new TrackParams.Audio(
                parsed.rate(),
                parsed.channels(),
                Optional.of(parsed.bitsPerSample() > 16 ? "s32" : "s16"),
                OptionalInt.empty(),
                OptionalLong.empty());
        // A stream header on its own: fLaC, STREAMINFO marked as the last block,
        // since the comment blocks that followed it here are not handed over.
        var extradata = Arrays.copyOfRange(mapping, prefix, info + FlacHeaders.STREAM_INFO_SIZE);
        extradata[FlacHeaders.MARKER.length] |= (byte) 0x80;
        return new OggStream(
                serial,
                index,
                CodecId.FLAC,
                CodecId.FLAC.ffmpegName(),
                params,
                parsed.rate(),
                0,
                count == 0 ? -1 : count,
                new PacketTiming.Flac(),
                extradata,
                false);
    }

    /// Whether `packet`, the next of this stream, is a header rather than
    /// audio, and takes it when it is.
    boolean header(byte[] packet) {
        if (headersLeft == 0) {
            return false;
        }
        if (headersLeft < 0) {
            // FLAC with an unknown count: a frame starts with its sync code, a
            // metadata block with its type.
            if (packet.length > 0 && (packet[0] & 0xFF) == 0xFF) {
                headersLeft = 0;
                return false;
            }
            return true;
        }
        headersLeft--;
        if (vorbis != null) {
            headers.add(packet);
            if (headersLeft == 0) {
                VorbisHeaders.Modes modes;
                try {
                    modes = VorbisHeaders.modes(headers.get(2));
                } catch (IllegalArgumentException e) {
                    throw invalid(e);
                }
                timing = new PacketTiming.Vorbis(vorbis, modes);
                extradata = XiphLacing.lace(headers);
            }
        }
        return true;
    }

    /// Whether every header has been read: the packets from here on are audio.
    boolean isReady() {
        return headersLeft == 0;
    }

    /// How a packet's length is known.
    PacketTiming timing() {
        return timing;
    }

    /// What the decoder is handed.
    byte[] extradata() {
        return extradata;
    }

    /// Forgets the packet in progress and the position: after a seek.
    void reset() {
        fragment = null;
        next = UNKNOWN;
        timing.reset();
    }

    /// `position` in the granule's count as a packet timestamp: the pre-skip
    /// taken off, so that the first packet starts at `-preSkip` and the sound
    /// at 0, as FFmpeg times Ogg Opus.
    long pts(long position) {
        return position - preSkip;
    }

    /// A name for a stream nobody here decodes: the letters its first packet
    /// starts with, `theora` or `Speex`.
    private static String nameOf(byte[] first) {
        var out = new StringBuilder();
        for (var i = 0; i < Math.min(first.length, 8); i++) {
            var c = first[i] & 0xFF;
            if (c >= 0x20 && c < 0x7F) {
                out.append((char) c);
            }
        }
        var name = out.toString().strip();
        return name.isEmpty() ? "unknown" : name;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static MediaException invalid(String what) {
        return new MediaException(new MediaError.InvalidData(what));
    }

    private static MediaException invalid(IllegalArgumentException e) {
        return new MediaException(new MediaError.InvalidData(String.valueOf(e.getMessage())), e);
    }
}
