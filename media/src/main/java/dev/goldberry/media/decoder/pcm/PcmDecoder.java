package dev.goldberry.media.decoder.pcm;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;

/// Raw PCM, in Java: a packet's bytes are already samples. 16-bit and 32-bit
/// float are handed over as they are; 24-bit is widened to 32-bit integers,
/// which is the nearest format the frame contract names.
///
/// The packet is copied: the SPI lends a frame until the next call and closes
/// the packet as soon as `send` returns.
public final class PcmDecoder implements Decoder {

    private final CodecId codec;
    private final int channels;
    private final int rate;
    private final Arena arena = Arena.ofShared();
    private MemorySegment buffer;
    private @Nullable AudioFrame pending;
    private boolean ended;
    private boolean closed;

    /// Opens a decoder for `request`, a PCM track whose parameters say its rate
    /// and channels.
    public PcmDecoder(DecoderRequest request) {
        Objects.requireNonNull(request, "request");
        this.codec = request.codec();
        if (!(request.params() instanceof TrackParams.Audio audio)
                || audio.sampleRate() <= 0
                || audio.channels() <= 0) {
            arena.close();
            throw new MediaException(new MediaError.InvalidData("a PCM track without a rate or a channel count"));
        }
        this.channels = audio.channels();
        this.rate = audio.sampleRate();
        this.buffer = arena.allocate(16 * 1024);
    }

    /// Whether `codec` is PCM this decodes.
    public static boolean decodes(CodecId codec) {
        return codec == CodecId.PCM_S16LE || codec == CodecId.PCM_S24LE || codec == CodecId.PCM_F32LE;
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
        var data = packet.data();
        var pts = packet.ptsNanos();
        pending = switch (codec) {
            case PCM_S16LE -> frame(SampleFormat.S16, copy(data), (int) (data.byteSize() / (2L * channels)), pts);
            case PCM_F32LE -> frame(SampleFormat.F32, copy(data), (int) (data.byteSize() / (4L * channels)), pts);
            case PCM_S24LE -> frame(SampleFormat.S32, widen(data), (int) (data.byteSize() / (3L * channels)), pts);
            default ->
                throw new MediaException(new MediaError.InvalidData(codec.ffmpegName() + " is not PCM this decodes"));
        };
        return true;
    }

    private @Nullable AudioFrame frame(SampleFormat format, MemorySegment plane, int samples, long pts) {
        return samples == 0 ? null : new AudioFrame(format, rate, channels, samples, List.of(plane), pts);
    }

    private MemorySegment copy(MemorySegment data) {
        ensure(data.byteSize());
        MemorySegment.copy(data, 0, buffer, 0, data.byteSize());
        return buffer.asSlice(0, data.byteSize());
    }

    /// 24-bit little-endian samples as 32-bit, the sample in the top three bytes.
    private MemorySegment widen(MemorySegment data) {
        var count = data.byteSize() / 3;
        ensure(count * 4);
        for (var i = 0L; i < count; i++) {
            var value = (data.get(JAVA_BYTE, i * 3) & 0xFF) << 8
                    | (data.get(JAVA_BYTE, i * 3 + 1) & 0xFF) << 16
                    | (data.get(JAVA_BYTE, i * 3 + 2) & 0xFF) << 24;
            buffer.set(JAVA_INT, i * 4, value);
        }
        return buffer.asSlice(0, count * 4);
    }

    private void ensure(long bytes) {
        if (buffer.byteSize() < bytes) {
            buffer = arena.allocate(Math.max(bytes, buffer.byteSize() * 2));
        }
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

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }
}
