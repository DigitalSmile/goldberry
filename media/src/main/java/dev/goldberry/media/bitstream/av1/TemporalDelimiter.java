package dev.goldberry.media.bitstream.av1;

/// A temporal delimiter OBU (§5.6): the start of a temporal unit. It carries
/// nothing.
public record TemporalDelimiter() implements ObuContent {}
