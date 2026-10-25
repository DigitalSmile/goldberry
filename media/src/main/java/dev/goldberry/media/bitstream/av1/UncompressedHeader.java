package dev.goldberry.media.bitstream.av1;

/// What a frame header OBU says (§5.9.2): a frame to decode, or one decoded
/// before to show again.
public sealed interface UncompressedHeader extends ObuContent permits FrameHeader, ShowExistingFrame {}
