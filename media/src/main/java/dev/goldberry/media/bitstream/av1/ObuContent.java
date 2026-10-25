package dev.goldberry.media.bitstream.av1;

/// What an OBU carries, read: one record per kind of OBU the decoding
/// process reads, and [Unparsed] for the others.
public sealed interface ObuContent
        permits SequenceHeader, TemporalDelimiter, UncompressedHeader, TileGroup, Frame, Metadata, Unparsed {}
