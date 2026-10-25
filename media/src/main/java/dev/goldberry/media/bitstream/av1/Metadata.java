package dev.goldberry.media.bitstream.av1;

/// A metadata OBU (§5.8), as far as its type: HDR light levels and mastering
/// display, scalability, ITU-T T.35 or a timecode. Its payload is not read.
///
/// @param metadataType `metadata_type`
public record Metadata(long metadataType) implements ObuContent {}
