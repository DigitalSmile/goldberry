package dev.goldberry.media.bitstream.av1;

import java.util.Objects;

/// A frame OBU (§5.10): a frame header and its first tile group in one unit.
///
/// @param header    the frame header, never one that shows an existing frame
/// @param tileGroup the tile group after it
public record Frame(FrameHeader header, TileGroup tileGroup) implements ObuContent {

    public Frame {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(tileGroup, "tileGroup");
    }

    /// The frame `s` is at, in the OBU `obu` of `data`.
    static Frame read(Syntax s, byte[] data, Obu obu, SequenceHeader sequenceHeader, References references) {
        if (!(FrameHeaderReader.read(s, obu.header(), sequenceHeader, references) instanceof FrameHeader header)) {
            throw new IllegalArgumentException("a frame OBU that shows an existing frame");
        }
        s.byteAlignment();
        return new Frame(header, TileGroup.read(s, data, obu, header));
    }
}
