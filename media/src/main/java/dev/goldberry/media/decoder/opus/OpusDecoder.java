package dev.goldberry.media.decoder.opus;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.bitstream.OpusPackets;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.ffm.MediaLibrary;
import dev.goldberry.media.ffm.NativeConstants;
import dev.goldberry.media.ffm.calls.OpusCalls;

/// An Opus decoder over libopus: one packet in, one run of interleaved float
/// samples out, at 48 kHz.
///
/// The `OpusHead` record says how many channels, which decoder (the plain one
/// for mono and stereo, the multistream one for a channel mapping), what gain
/// to apply, and how many samples of the encoder's look-ahead to drop from the
/// start: the pre-skip, dropped when the stream starts over after a [#flush]
/// and not after a seek into it, which the Engine trims to its target itself. A packet's
/// samples are decoded into one buffer of the largest frame Opus makes, 120 ms,
/// and handed over as one borrowed frame.
public final class OpusDecoder implements Decoder {

    /// The most samples per channel an Opus packet decodes to: 120 ms at 48 kHz.
    static final int MAX_FRAME = 5760;

    private final OpusCalls opus;
    private final NativeConstants.Opus constants;
    private final OpusHead head;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment decoder;
    private final MemorySegment pcm;
    private MemorySegment input;
    private int toSkip;
    /// Whether the next packet is the first after the stream was opened or
    /// flushed, so the pre-skip is decided by it.
    private boolean atStart = true;
    private @Nullable AudioFrame pending;
    private boolean ended;
    private boolean closed;

    /// Opens a decoder for `request`, an Opus track.
    ///
    /// @throws MediaException when the record is not an `OpusHead`, or libopus
    ///                        refuses the stream
    public OpusDecoder(MediaLibrary library, DecoderRequest request) {
        Objects.requireNonNull(request, "request");
        this.opus = library.opus();
        this.constants = library.constants().opus();
        this.head = head(request);
        try {
            var error = arena.allocate(JAVA_INT);
            MemorySegment created;
            if (head.isSingleStream()) {
                created = opus.decoderCreate().call(OpusPackets.SAMPLE_RATE, head.channels(), error);
            } else {
                var mapping = arena.allocateFrom(JAVA_BYTE, head.mapping());
                created = opus.multistreamDecoderCreate()
                        .call(OpusPackets.SAMPLE_RATE, head.channels(), head.streams(), head.coupled(), mapping, error);
            }
            var code = error.get(JAVA_INT, 0);
            if (created.equals(MemorySegment.NULL) || code != constants.ok()) {
                throw new MediaException(new MediaError.InvalidData("opus_decoder_create: " + describe(code)));
            }
            this.decoder = created;
            if (head.gain() != 0) {
                check("OPUS_SET_GAIN", ctl(constants.setGainRequest(), head.gain()));
            }
            this.pcm = arena.allocate((long) MAX_FRAME * head.channels() * JAVA_FLOAT.byteSize(), 64);
            this.input = arena.allocate(4096);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// The record `request` carries, or a plain one for a stream that carries
    /// none and says its channels.
    private static OpusHead head(DecoderRequest request) {
        var extradata = request.extradata();
        if (extradata.byteSize() >= 19) {
            try {
                return OpusHead.parse(extradata.toArray(JAVA_BYTE));
            } catch (IllegalArgumentException e) {
                throw new MediaException(
                        new MediaError.InvalidData(Objects.requireNonNullElse(e.getMessage(), "not an OpusHead")), e);
            }
        }
        var channels = request.params() instanceof TrackParams.Audio(var _, var count, var _, var _, var _) ? count : 2;
        return OpusHead.plain(channels == 0 ? 2 : channels);
    }

    @Override
    public boolean send(Packet packet) {
        requireOpen();
        if (ended) {
            throw new IllegalStateException("a packet after the end");
        }
        if (pending != null) {
            return false;
        }
        var bytes = packet.data();
        var length = Math.toIntExact(bytes.byteSize());
        if (length == 0) {
            return true;
        }
        if (atStart) {
            // The pre-skip is the encoder's look-ahead at the start of the
            // stream, and only there. A seek lands on a later packet, which the
            // Engine decodes from and drops to the target itself; the stream's
            // first packet is told apart by the negative time the container gave
            // it for the codec delay.
            atStart = false;
            toSkip = packet.ptsNanos() != Frame.NO_PTS && packet.ptsNanos() < 0 ? head.preSkip() : 0;
        }
        if (input.byteSize() < length) {
            input = arena.allocate(Math.max(length, input.byteSize() * 2));
        }
        MemorySegment.copy(bytes, 0, input, 0, length);
        var decoded = head.isSingleStream()
                ? opus.decodeFloat().call(decoder, input, length, pcm, MAX_FRAME, 0)
                : opus.multistreamDecodeFloat().call(decoder, input, length, pcm, MAX_FRAME, 0);
        if (decoded < 0) {
            throw new MediaException(new MediaError.InvalidData("opus_decode_float: " + describe(decoded)));
        }
        var skipped = Math.min(toSkip, decoded);
        toSkip -= skipped;
        var samples = Math.min(decoded - skipped, stated(packet, decoded));
        if (samples == 0) {
            return true;
        }
        var frameBytes = (long) head.channels() * JAVA_FLOAT.byteSize();
        var plane = pcm.asSlice(skipped * frameBytes, samples * frameBytes);
        var pts = packet.ptsNanos();
        if (pts != Frame.NO_PTS) {
            pts += skipped * 1_000_000_000L / OpusPackets.SAMPLE_RATE;
        }
        pending = new AudioFrame(
                SampleFormat.F32, OpusPackets.SAMPLE_RATE, head.channels(), samples, List.of(plane), pts);
        return true;
    }

    @Override
    public void sendEnd() {
        requireOpen();
        ended = true;
    }

    @Override
    public Received receive() {
        requireOpen();
        var frame = pending;
        if (frame != null) {
            pending = null;
            return new Received.Decoded(frame);
        }
        return ended ? Received.ENDED : Received.NEEDS_INPUT;
    }

    @Override
    public void flush() {
        requireOpen();
        pending = null;
        ended = false;
        toSkip = 0;
        atStart = true;
        check(
                "OPUS_RESET_STATE",
                head.isSingleStream()
                        ? opus.decoderCtl().call(decoder, constants.resetState())
                        : opus.multistreamDecoderCtl().call(decoder, constants.resetState()));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending = null;
        if (head.isSingleStream()) {
            opus.decoderDestroy().call(decoder);
        } else {
            opus.multistreamDecoderDestroy().call(decoder);
        }
        arena.close();
    }

    /// How many of `decoded` samples the container says play: a packet whose
    /// stated duration is shorter than what it decodes to, by more than the
    /// container's rounding, is a stream's last, trimmed by Matroska's
    /// `DiscardPadding` or Ogg's final granule. Everything else plays whole.
    private static int stated(Packet packet, int decoded) {
        if (packet.duration() <= 0) {
            return decoded;
        }
        var nanos = packet.timeBase().toNanos(packet.duration());
        var samples = (nanos * OpusPackets.SAMPLE_RATE + 500_000_000L) / 1_000_000_000L;
        var slack = OpusPackets.SAMPLE_RATE / 1000;
        return samples + slack < decoded ? (int) Math.max(samples, 0) : decoded;
    }

    /// The channel count and skip the record states, for tests.
    OpusHead head() {
        return head;
    }

    private int ctl(int request, int value) {
        return head.isSingleStream()
                ? opus.decoderCtlInt().call(decoder, request, value)
                : opus.multistreamDecoderCtlInt().call(decoder, request, value);
    }

    private void check(String what, int code) {
        if (code != constants.ok()) {
            throw new MediaException(new MediaError.InvalidData(what + ": " + describe(code)));
        }
    }

    @SuppressWarnings("restricted")
    private String describe(int code) {
        var text = opus.strerror().call(code);
        return text.equals(MemorySegment.NULL)
                ? "error " + code
                : text.reinterpret(256).getString(0);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }
}
