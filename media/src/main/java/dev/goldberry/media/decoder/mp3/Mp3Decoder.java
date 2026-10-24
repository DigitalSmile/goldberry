package dev.goldberry.media.decoder.mp3;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.ffm.MediaLibrary;
import dev.goldberry.media.ffm.NativeConstants;
import dev.goldberry.media.ffm.NativeStructs;
import dev.goldberry.media.ffm.calls.Mp3Calls;

/// An MPEG audio decoder over minimp3: one packet in, one run of interleaved
/// float samples out, at the rate and channel count the frame's own header
/// states.
///
/// minimp3 decodes a frame at a time from a buffer, telling how many bytes it
/// took; a packet that holds several frames, as an AVI chunk may, is decoded
/// frame after frame into one growing buffer and handed over as one frame. A
/// frame minimp3 passes over, the first after a seek whose bit reservoir
/// reaches back into frames it never saw, gives no samples and no error. The
/// encoder's padding is trimmed where the container says how much of the
/// last packet plays, as FFmpeg's decoder trims it; a [#flush] starts the
/// decoder over, since the reservoir is from before the seek.
public final class Mp3Decoder implements Decoder {

    private static final long INFO_FRAME_BYTES =
            NativeStructs.MP3DEC_FRAME_INFO.byteOffset(groupElement("frame_bytes"));
    private static final long INFO_CHANNELS = NativeStructs.MP3DEC_FRAME_INFO.byteOffset(groupElement("channels"));
    private static final long INFO_HZ = NativeStructs.MP3DEC_FRAME_INFO.byteOffset(groupElement("hz"));

    private final Mp3Calls mp3;
    private final NativeConstants.Mp3 constants;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment decoder;
    private final MemorySegment info;
    private MemorySegment pcm;
    private MemorySegment input;
    private @Nullable AudioFrame pending;
    private boolean ended;
    private boolean closed;

    /// Opens a decoder for `request`, an MPEG audio track. Nothing of the
    /// request is needed: every frame says what it is.
    public Mp3Decoder(MediaLibrary library, DecoderRequest request) {
        Objects.requireNonNull(request, "request");
        this.mp3 = library.mp3();
        this.constants = library.constants().mp3();
        try {
            this.decoder = arena.allocate(NativeStructs.MP3DEC);
            this.info = arena.allocate(NativeStructs.MP3DEC_FRAME_INFO);
            this.pcm = arena.allocate(frameBytes(), 64);
            this.input = arena.allocate(4096);
            mp3.init().call(decoder);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// The most bytes one frame decodes to: the library's bound on samples,
    /// all channels counted, times its sample size.
    private long frameBytes() {
        return (long) constants.maxSamplesPerFrame() * constants.sampleBytes();
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
        if (input.byteSize() < length) {
            input = arena.allocate(Math.max(length, input.byteSize() * 2));
        }
        MemorySegment.copy(bytes, 0, input, 0, length);
        var offset = 0;
        var total = 0;
        var rate = 0;
        var channels = 0;
        var sampleBytes = constants.sampleBytes();
        while (offset < length) {
            var filled = (long) total * channels * sampleBytes;
            if (pcm.byteSize() < filled + frameBytes()) {
                var grown = arena.allocate(pcm.byteSize() * 2, 64);
                MemorySegment.copy(pcm, 0, grown, 0, filled);
                pcm = grown;
            }
            var samples =
                    mp3.decodeFrame().call(decoder, input.asSlice(offset), length - offset, pcm.asSlice(filled), info);
            var taken = info.get(JAVA_INT, INFO_FRAME_BYTES);
            if (taken <= 0) {
                // minimp3 wants more bytes than are left: the tail is no frame.
                break;
            }
            offset += taken;
            if (samples <= 0) {
                continue;
            }
            var hz = info.get(JAVA_INT, INFO_HZ);
            var count = info.get(JAVA_INT, INFO_CHANNELS);
            if (total == 0) {
                rate = hz;
                channels = count;
            } else if (hz != rate || count != channels) {
                // A stream that changes shape inside one packet: what came
                // before the change is handed over, the rest is not.
                break;
            }
            total += samples;
        }
        if (total == 0) {
            return true;
        }
        var plays = Math.min(total, stated(packet, total, rate));
        var plane = pcm.asSlice(0, (long) plays * channels * sampleBytes);
        pending = new AudioFrame(SampleFormat.F32, rate, channels, plays, List.of(plane), packet.ptsNanos());
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
        mp3.init().call(decoder);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending = null;
        arena.close();
    }

    /// How many of `decoded` samples the container says play: a packet whose
    /// stated duration is shorter than what it decodes to, by more than a
    /// millisecond of rounding, is a stream's last, with the encoder's
    /// padding after the music. Everything else plays whole.
    private static int stated(Packet packet, int decoded, int rate) {
        if (packet.duration() <= 0) {
            return decoded;
        }
        var nanos = packet.timeBase().toNanos(packet.duration());
        var samples = (nanos * rate + 500_000_000L) / 1_000_000_000L;
        var slack = rate / 1000;
        return samples + slack < decoded ? (int) Math.max(samples, 0) : decoded;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }
}
