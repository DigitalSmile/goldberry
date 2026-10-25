package dev.goldberry.media.bitstream.av1;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/// Reads an AV1 stream OBU by OBU, in decoding order, keeping the state the
/// headers are read against: the sequence header in force, the reference
/// slots, and the frame whose tile groups are being read.
///
/// A decoder handed the headers and not the pictures, as Vulkan Video's is,
/// drives one of these per stream: [#configure] with the track's `av1C`
/// record, then [#parse] with each temporal unit as the container hands it
/// over. Each OBU comes back with the reference slots it was read against;
/// a frame's are the ones its `ref_frame_idx` name, and the slots are updated
/// (§7.20) as the frame's last tile group is read, or as a frame header shows
/// an existing frame. The pieces are public too, for a caller that keeps the
/// state itself: [Obu#split], [SequenceHeader#parse], [UncompressedHeader#parse],
/// [TileGroup#parse], [Frame#parse] and [References#updated].
///
/// Operating point 0 is decoded: an OBU of a layer it leaves out is
/// [Unparsed]. Not thread-safe.
public final class Av1Parser {

    /// One OBU read.
    ///
    /// @param obu        where it lies in the buffer read
    /// @param content    what it carries
    /// @param references the reference slots it was read against: for a
    ///                   frame header, frame or tile group, the frame's own,
    ///                   before the frame is stored
    public record Parsed(Obu obu, ObuContent content, References references) {}

    private final Syntax.@Nullable Trace trace;
    private @Nullable SequenceHeader sequenceHeader;
    private References references = References.NONE;

    /// The frame whose tile groups are being read: `SeenFrameHeader`.
    private @Nullable FrameHeader frame;

    /// The slots that frame was read against.
    private References frameReferences = References.NONE;

    /// The tile the frame's next tile group starts at.
    private int nextTile;

    /// A parser before the first OBU of a stream.
    public Av1Parser() {
        this(null);
    }

    /// A parser that hands every syntax element it reads to `trace`.
    Av1Parser(Syntax.@Nullable Trace trace) {
        this.trace = trace;
    }

    /// The sequence header in force, once one was read.
    public Optional<SequenceHeader> sequenceHeader() {
        return Optional.ofNullable(sequenceHeader);
    }

    /// The reference slots the next frame is read against.
    public References references() {
        return references;
    }

    /// The `configOBUs` of the `av1C` record `record`: the sequence header the
    /// stream starts with, and any metadata.
    ///
    /// @throws IllegalArgumentException when the record or an OBU is malformed
    public List<Parsed> configure(byte[] record) {
        return read(record, SequenceHeader.configObus(record));
    }

    /// The OBUs of the temporal unit `temporalUnit`, in the low overhead
    /// bitstream format, read in order.
    ///
    /// @throws IllegalArgumentException when an OBU is malformed, or out of
    ///                                  place; the state is as the OBUs before
    ///                                  it left it
    public List<Parsed> parse(byte[] temporalUnit) {
        // A temporal unit starts a frame afresh, with or without the temporal
        // delimiter MP4 leaves out.
        frame = null;
        return read(temporalUnit, Obu.split(temporalUnit));
    }

    private List<Parsed> read(byte[] data, List<Obu> obus) {
        var parsed = new ArrayList<Parsed>(obus.size());
        for (var obu : obus) {
            parsed.add(read(data, obu));
        }
        return parsed;
    }

    /// The OBU `obu` of `data`, the next in decoding order.
    ///
    /// @throws IllegalArgumentException when it is malformed, or out of place;
    ///                                  the state is unchanged
    public Parsed read(byte[] data, Obu obu) {
        var s = obu.payload(data, trace);
        var type = obu.header().type();
        if (dropped(obu.header())) {
            return new Parsed(obu, new Unparsed(type), references);
        }
        return switch (type) {
            case ObuHeader.SEQUENCE_HEADER -> {
                var read = SequenceHeader.read(s);
                sequenceHeader = read;
                yield new Parsed(obu, read, references);
            }
            case ObuHeader.TEMPORAL_DELIMITER -> {
                s.heading("Temporal Delimiter");
                frame = null;
                yield new Parsed(obu, new TemporalDelimiter(), references);
            }
            case ObuHeader.FRAME_HEADER, ObuHeader.REDUNDANT_FRAME_HEADER -> frameHeader(obu, s);
            case ObuHeader.FRAME -> {
                if (frame != null) {
                    throw new IllegalArgumentException("a frame OBU before the last frame's tiles are all read");
                }
                var read = Frame.read(s, data, obu, sequence(), references);
                yield tileGroup(obu, read, read.header(), read.tileGroup(), references, 0);
            }
            case ObuHeader.TILE_GROUP -> {
                var current = frame;
                if (current == null) {
                    throw new IllegalArgumentException("a tile group with no frame header before it");
                }
                var read = TileGroup.read(s, data, obu, current);
                yield tileGroup(obu, read, current, read, frameReferences, nextTile);
            }
            case ObuHeader.METADATA -> new Parsed(obu, new Metadata(s.leb128("metadata_type")), references);
            default -> new Parsed(obu, new Unparsed(type), references);
        };
    }

    private Parsed frameHeader(Obu obu, Syntax s) {
        if (frame != null) {
            // frame_header_copy(): the same header again, for resilience.
            return new Parsed(obu, new Unparsed(obu.header().type()), frameReferences);
        }
        var read = FrameHeaderReader.read(s, obu.header(), sequence(), references);
        var before = references;
        switch (read) {
            case ShowExistingFrame shown -> references = references.updated(shown);
            case FrameHeader header -> {
                frame = header;
                frameReferences = references;
                nextTile = 0;
            }
        }
        return new Parsed(obu, read, before);
    }

    /// `group`, of the frame `header`, which should start at tile `expected`:
    /// the frame is stored once its last tile is read.
    private Parsed tileGroup(
            Obu obu, ObuContent content, FrameHeader header, TileGroup group, References before, int expected) {
        if (group.tgStart() != expected) {
            throw new IllegalArgumentException("a tile group from tile " + group.tgStart() + ", not " + expected);
        }
        if (group.last()) {
            references = before.updated(header);
            frame = null;
        } else {
            frame = header;
            frameReferences = before;
            nextTile = group.tgEnd() + 1;
        }
        return new Parsed(obu, content, before);
    }

    private SequenceHeader sequence() {
        var current = sequenceHeader;
        if (current == null) {
            throw new IllegalArgumentException("a frame with no sequence header before it");
        }
        return current;
    }

    /// `drop_obu()`: whether the OBU is of a layer operating point 0 leaves
    /// out.
    private boolean dropped(ObuHeader header) {
        var current = sequenceHeader;
        if (current == null
                || !header.extension()
                || header.type() == ObuHeader.SEQUENCE_HEADER
                || header.type() == ObuHeader.TEMPORAL_DELIMITER) {
            return false;
        }
        var idc = current.operatingPoints().getFirst().idc();
        if (idc == 0) {
            return false;
        }
        var inTemporalLayer = ((idc >> header.temporalId()) & 1) != 0;
        var inSpatialLayer = ((idc >> (header.spatialId() + 8)) & 1) != 0;
        return !inTemporalLayer || !inSpatialLayer;
    }
}
