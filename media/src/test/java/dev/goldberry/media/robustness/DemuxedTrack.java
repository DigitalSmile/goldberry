package dev.goldberry.media.robustness;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.Containers;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// One track of a fixture, demuxed by the Java containers into bytes that
/// outlive them: the packets a decoder is fed, and the request it is opened
/// with, so that a test can damage either before handing it over.
///
/// @param name      the fixture, for messages
/// @param codec     the track's codec
/// @param codecName the container's name for it
/// @param params    the track's parameters
/// @param extradata the codec configuration the container held
/// @param timeBase  the unit of the packets' timestamps
/// @param samples   the packets, in file order
@SuppressWarnings("ArrayRecordComponent")
public record DemuxedTrack(
        String name,
        CodecId codec,
        String codecName,
        TrackParams params,
        byte[] extradata,
        Rational timeBase,
        List<Sample> samples) {

    /// One packet's bytes and timing.
    ///
    /// @param data     the compressed bytes
    /// @param alpha    the alpha stream's bytes beside them, empty for none
    /// @param pts      the presentation timestamp, in the track's time base
    /// @param dts      the decoding timestamp
    /// @param duration how long it plays, 0 when unknown
    /// @param keyframe whether decoding can start here
    @SuppressWarnings("ArrayRecordComponent")
    public record Sample(byte[] data, byte[] alpha, long pts, long dts, long duration, boolean keyframe) {

        public Sample {
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(alpha, "alpha");
        }

        /// This packet with other bytes.
        public Sample withData(byte[] bytes) {
            return new Sample(bytes, alpha, pts, dts, duration, keyframe);
        }

        /// This packet with other timing.
        public Sample withTiming(long newPts, long newDuration) {
            return new Sample(data, alpha, newPts, newPts, newDuration, keyframe);
        }

        /// The packet the SPI hands a decoder, over memory of `arena` exactly
        /// as long as the bytes, so that a read past the end is caught.
        public Packet packet(Arena arena, Rational timeBase) {
            var segment = data.length == 0 ? MemorySegment.NULL : arena.allocateFrom(JAVA_BYTE, data);
            var packet = Packet.of(segment, 0, pts, dts, duration, keyframe, timeBase);
            return alpha.length == 0 ? packet : packet.withAlpha(arena.allocateFrom(JAVA_BYTE, alpha));
        }
    }

    public DemuxedTrack {
        samples = List.copyOf(samples);
    }

    /// The first track of `type` in the fixture `name`, whose bytes are `file`.
    public static DemuxedTrack of(String name, byte[] file, MediaType type) {
        try (var container = Containers.open(Source.of(URI.create("mem:///" + name)), new MemoryIO(file))) {
            var track =
                    container.info().defaultTrack(type).orElseThrow(() -> new AssertionError(name + " has no " + type));
            container.select(Set.of(track.index()));
            var request = container.request(track.index());
            var samples = new ArrayList<Sample>();
            Packet packet;
            while ((packet = container.read()) != null) {
                try (var read = packet) {
                    samples.add(new Sample(
                            read.data().toArray(JAVA_BYTE),
                            read.alpha().toArray(JAVA_BYTE),
                            read.pts(),
                            read.dts(),
                            read.duration(),
                            read.keyframe()));
                }
            }
            return new DemuxedTrack(
                    name,
                    request.codec(),
                    request.codecName(),
                    request.params(),
                    request.extradata().toArray(JAVA_BYTE),
                    request.timeBase(),
                    samples);
        }
    }

    /// The request a decoder for this track is opened with, carrying
    /// `record` as its extradata.
    public DecoderRequest request(Arena arena, byte[] record) {
        var segment = record.length == 0 ? MemorySegment.NULL : arena.allocateFrom(JAVA_BYTE, record);
        return new DecoderRequest(codec, codecName, params, segment, timeBase);
    }

    /// This track with other packets.
    public DemuxedTrack withSamples(List<Sample> replaced) {
        return new DemuxedTrack(name, codec, codecName, params, extradata, timeBase, replaced);
    }

    /// This track with another codec configuration.
    public DemuxedTrack withExtradata(byte[] record) {
        return new DemuxedTrack(name, codec, codecName, params, record, timeBase, samples);
    }
}
