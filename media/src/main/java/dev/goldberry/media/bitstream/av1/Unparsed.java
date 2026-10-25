package dev.goldberry.media.bitstream.av1;

/// An OBU that is not read: padding, a tile list, a reserved type, a
/// redundant copy of a frame header already read, or a layer the operating
/// point does not decode.
///
/// @param obuType its `obu_type`
public record Unparsed(int obuType) implements ObuContent {}
