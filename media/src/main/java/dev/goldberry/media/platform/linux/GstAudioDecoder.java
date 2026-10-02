package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.MemorySegment;
import java.util.List;

import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;

/// One AAC, AC-3 or E-AC-3 track decoded by GStreamer.
///
/// Samples come out as interleaved 32-bit float, converted by `audioconvert`
/// from whatever the decoder produced. The channels are in GStreamer's order,
/// which is FFmpeg's: every GStreamer audio decoder hands its channels over in
/// the order of `GstAudioChannelPosition`, front left first, and that order is
/// the one WAVE and FFmpeg use.
///
/// **Timing:** the first frame after an open or a flush anchors the clock, and
/// each frame after it is timed by the samples before it, as the macOS provider
/// times them. A decoder hands on the container's time for each packet, and
/// Matroska's are whole milliseconds: 21 ms apart for a frame of 21.33. Timing
/// by sample count is exact. A frame whose own time strays from the count by
/// more than [#DISCONTINUITY_NANOS] re-anchors it: a gap in the stream.
final class GstAudioDecoder extends GstDecoder {

    /// How far a frame's time may be from the sample count before it is taken as
    /// a gap in the stream.
    static final long DISCONTINUITY_NANOS = 200_000_000L;

    private MemorySegment formatCaps = MemorySegment.NULL;
    private int sampleRate;
    private int channels;
    private long anchorNanos = Frame.NO_PTS;
    private long samplesSinceAnchor;

    GstAudioDecoder(GStreamer gs, GstCodec codec, DecoderRequest request, List<Gst.Candidate> candidates) {
        if (!(request.params() instanceof TrackParams.Audio)) {
            throw new IllegalArgumentException(request.codecName() + " is not an audio track");
        }
        super(gs, codec, request, candidates, MAX_QUEUED);
    }

    @Override
    Frame describe(MemorySegment sample, MemorySegment buffer, MemorySegment data) {
        var caps = gs.gst().sampleCaps(sample);
        if (!caps.equals(formatCaps)) {
            var gst = gs.gst();
            sampleRate = gst.capsInt(caps, "rate").orElse(0);
            channels = gst.capsInt(caps, "channels").orElse(0);
            if (sampleRate <= 0 || channels <= 0) {
                throw new GstException("GStreamer's " + decoder() + " decoded " + codecName + " at " + sampleRate
                        + " Hz with " + channels + " channels");
            }
            formatCaps = caps;
        }
        var samples = frames(data.byteSize(), channels);
        var pts = timed(ptsNanos(buffer));
        samplesSinceAnchor += samples;
        return new AudioFrame(
                SampleFormat.F32,
                sampleRate,
                channels,
                samples,
                List.of(data.asSlice(0, SampleFormat.F32.planeSize(channels, samples))),
                pts);
    }

    @Override
    void flushed() {
        anchorNanos = Frame.NO_PTS;
        samplesSinceAnchor = 0;
    }

    /// The time of the frame the decoder stamped `stamped`: counted from the
    /// anchor, which the first frame, or one that strays, sets.
    private long timed(long stamped) {
        if (stamped != Frame.NO_PTS
                && (anchorNanos == Frame.NO_PTS || Math.abs(stamped - counted()) > DISCONTINUITY_NANOS)) {
            anchorNanos = stamped;
            samplesSinceAnchor = 0;
        }
        return counted();
    }

    /// When the next sample plays, by the count.
    private long counted() {
        return anchorNanos == Frame.NO_PTS
                ? Frame.NO_PTS
                : anchorNanos + samplesSinceAnchor * 1_000_000_000L / sampleRate;
    }

    /// The whole frames `bytes` of interleaved f32 at `channels` channels hold.
    static int frames(long bytes, int channels) {
        return Math.toIntExact(bytes / (JAVA_FLOAT.byteSize() * channels));
    }
}
