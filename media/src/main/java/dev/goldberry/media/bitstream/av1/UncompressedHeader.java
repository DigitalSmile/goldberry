package dev.goldberry.media.bitstream.av1;

/// What a frame header OBU says (§5.9.2): a frame to decode, or one decoded
/// before to show again.
public sealed interface UncompressedHeader extends ObuContent permits FrameHeader, ShowExistingFrame {

    /// `frame_type`.
    int frameType();

    /// `refresh_frame_flags`: a bit per reference slot the frame is stored in
    /// once it is done.
    int refreshFrameFlags();

    /// The frame header OBU `obu` of `data`, read against the sequence header
    /// and the reference slots in force. `references` is what this frame reads;
    /// [References#updated] with the result is what the next frame reads, once
    /// this one is decoded.
    ///
    /// @throws IllegalArgumentException when it is malformed, or needs a
    ///                                  reference the slots do not hold
    static UncompressedHeader parse(byte[] data, Obu obu, SequenceHeader sequenceHeader, References references) {
        var type = obu.header().type();
        if (type != ObuHeader.FRAME_HEADER && type != ObuHeader.REDUNDANT_FRAME_HEADER) {
            throw new IllegalArgumentException("OBU type " + type + " is not a frame header");
        }
        return FrameHeaderReader.read(obu.payload(data, null), obu.header(), sequenceHeader, references);
    }
}
