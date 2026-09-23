package io.github.digitalsmile.goldberry.example.ui;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.Packet;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.SampleFormat;
import io.github.digitalsmile.goldberry.media.codec.TrackParams;

/// A [DecoderProvider] written by this application: 16-bit PCM, decoded in
/// Java.
///
/// It is a demonstration of the SPI rather than something worth doing: FFmpeg
/// decodes PCM perfectly well. It exists because the SPI's real use (an OS H.264
/// decoder, or a licensed AAC one) cannot be shown in a showcase that ships only
/// royalty-free natives. It shows that a provider is asked first, that it hands
/// the engine frames in native memory, and that the engine does the rest:
/// resampling, the clock, seeking.
///
/// It claims PCM only while [#enabled] is on, so the screen's switch decides
/// whether a WAV plays through here or through FFmpeg. The Status card names
/// whichever it was.
public final class JavaPcmDecoder implements DecoderProvider {

    /// What the Status card shows as the decoder's name.
    public static final String NAME = "showcase-java-pcm";

    private volatile boolean enabled = true;

    /// Whether this provider claims PCM. Read when a source is opened.
    public boolean enabled() {
        return enabled;
    }

    /// Turns the provider on or off, for the next source opened.
    public void enabled(boolean on) {
        enabled = on;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return enabled && request.codec() == CodecId.PCM_S16LE && request.params() instanceof TrackParams.Audio;
    }

    @Override
    public Decoder open(DecoderRequest request) {
        var audio = (TrackParams.Audio) request.params();
        return new PcmDecoder(audio.sampleRate(), audio.channels());
    }

    /// One packet in, one frame out: PCM needs no more than a copy.
    ///
    /// The copy is into memory this decoder owns, because a packet is closed as
    /// soon as `send` returns, and a frame has to outlive it until the next call.
    private static final class PcmDecoder implements Decoder {

        private final int sampleRate;
        private final int channels;
        private final Arena arena = Arena.ofConfined();
        private MemorySegment buffer = MemorySegment.NULL;
        private @Nullable AudioFrame pending;
        private boolean ending;

        PcmDecoder(int sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
        }

        @Override
        public boolean send(Packet packet) {
            if (pending != null) {
                return false;
            }
            var bytes = packet.data().byteSize();
            if (buffer.byteSize() < bytes) {
                buffer = arena.allocate(Math.max(bytes, 16 * 1024));
            }
            MemorySegment.copy(packet.data(), 0, buffer, 0, bytes);
            var samples = (int) (bytes / (2L * channels));
            pending = new AudioFrame(
                    SampleFormat.S16,
                    sampleRate,
                    channels,
                    samples,
                    List.of(buffer.asSlice(0, bytes)),
                    packet.ptsNanos());
            return true;
        }

        @Override
        public void sendEnd() {
            ending = true;
        }

        @Override
        public Received receive() {
            var frame = pending;
            if (frame != null) {
                pending = null;
                return new Received.Decoded(frame);
            }
            return ending ? Received.ENDED : Received.NEEDS_INPUT;
        }

        @Override
        public void flush() {
            pending = null;
            ending = false;
        }

        @Override
        public void close() {
            arena.close();
        }
    }
}
