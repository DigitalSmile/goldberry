package dev.goldberry.media.bitstream;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.platform.fixtures.Fixtures;

/// The NAL units of a platform fixture's H.264 or HEVC track, as the
/// container hands them over: the parameter sets of the configuration record,
/// then each packet's units, in decoding order.
///
/// @param parameterSets the record's, as [ParameterSets] reads them
/// @param packets       each packet's units, without their length prefixes
public record NalUnits(List<byte[]> parameterSets, List<List<byte[]>> packets) {

    /// `name`'s video track, from `…/platform/fixtures/`.
    public static NalUnits of(String name) {
        try (var demuxer = Fixtures.demux(name)) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            var record = demuxer.request(track.index()).extradata().toArray(JAVA_BYTE);
            var configuration = track.codec() == CodecId.H264 ? ParameterSets.h264(record) : ParameterSets.hevc(record);
            var packets = new ArrayList<List<byte[]>>();
            for (var next = demuxer.read(); next != null; next = demuxer.read()) {
                try (var packet = next) {
                    packets.add(split(packet.data().toArray(JAVA_BYTE), configuration.nalLengthSize()));
                }
            }
            return new NalUnits(configuration.parameterSets(), packets);
        }
    }

    /// The units of a packet whose units are each prefixed by its length in
    /// `lengthSize` bytes.
    public static List<byte[]> split(byte[] data, int lengthSize) {
        var units = new ArrayList<byte[]>();
        var at = 0;
        while (at + lengthSize <= data.length) {
            var length = 0;
            for (var i = 0; i < lengthSize; i++) {
                length = (length << 8) | (data[at + i] & 0xFF);
            }
            at += lengthSize;
            units.add(Arrays.copyOfRange(data, at, at + length));
            at += length;
        }
        return units;
    }

    /// Every unit, the parameter sets first.
    public List<byte[]> all() {
        var all = new ArrayList<byte[]>(parameterSets);
        packets.forEach(all::addAll);
        return all;
    }
}
