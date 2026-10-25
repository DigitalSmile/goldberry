package dev.goldberry.media.bitstream.av1;

/// A metadata OBU (§5.8), as far as its type: HDR light levels and mastering
/// display, scalability, ITU-T T.35 or a timecode. Its payload is not read.
///
/// @param metadataType `metadata_type`
public record Metadata(long metadataType) implements ObuContent {

    public static final int HDR_CLL = 1;
    public static final int HDR_MDCV = 2;
    public static final int SCALABILITY = 3;
    public static final int ITUT_T35 = 4;
    public static final int TIMECODE = 5;
}
