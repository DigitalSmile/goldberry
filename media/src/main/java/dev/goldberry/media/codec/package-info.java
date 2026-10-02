/// What a track is, in Goldberry's words: the vocabulary of the Decoder SPI.
///
/// [dev.goldberry.media.codec.CodecId] names the codec,
/// [dev.goldberry.media.codec.MediaType] the kind of stream, and
/// [dev.goldberry.media.codec.TrackParams] what a decoder needs
/// before its first packet. No FFmpeg type appears in this package. That is the
/// promise the Decoder SPI makes to a provider.
///
/// Exported to every module, for an application that brings a codec of its
/// own. Null-marked.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.codec;

import org.jspecify.annotations.NullMarked;
