package dev.goldberry.media.bitstream.av1;

/// An OBU's header (AV1 §5.3.2, §5.3.3): the kind of unit, and the layer it
/// belongs to when it has an extension.
///
/// @param type         `obu_type`, one of the constants here
/// @param extension    `obu_extension_flag`
/// @param hasSizeField `obu_has_size_field`
/// @param temporalId   `temporal_id`, 0 without an extension
/// @param spatialId    `spatial_id`, 0 without an extension
public record ObuHeader(int type, boolean extension, boolean hasSizeField, int temporalId, int spatialId) {

    public static final int SEQUENCE_HEADER = 1;
    public static final int TEMPORAL_DELIMITER = 2;
    public static final int FRAME_HEADER = 3;
    public static final int TILE_GROUP = 4;
    public static final int METADATA = 5;
    public static final int FRAME = 6;
    public static final int REDUNDANT_FRAME_HEADER = 7;
    public static final int TILE_LIST = 8;
    public static final int PADDING = 15;

    /// Its length in bytes: one, or two with the extension.
    public int length() {
        return extension ? 2 : 1;
    }

    /// The header `s` is at, under FFmpeg's heading `OBU header`.
    ///
    /// @throws IllegalArgumentException when a bit that must be zero is not
    static ObuHeader read(Syntax s) {
        s.heading("OBU header");
        if (s.f(1, "obu_forbidden_bit") != 0) {
            throw new IllegalArgumentException("an OBU with obu_forbidden_bit set");
        }
        var type = s.f(4, "obu_type");
        var extension = s.flag("obu_extension_flag");
        var hasSize = s.flag("obu_has_size_field");
        s.f(1, "obu_reserved_1bit");
        var temporal = 0;
        var spatial = 0;
        if (extension) {
            temporal = s.f(3, "temporal_id");
            spatial = s.f(2, "spatial_id");
            s.f(3, "extension_header_reserved_3bits");
        }
        return new ObuHeader(type, extension, hasSize, temporal, spatial);
    }
}
