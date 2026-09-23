/// What a track is, in Goldberry's words (`docs/goldberry-media.md` §5).
///
/// [io.github.digitalsmile.goldberry.media.codec.CodecId] names the codec,
/// [io.github.digitalsmile.goldberry.media.codec.MediaType] the kind of stream, and
/// [io.github.digitalsmile.goldberry.media.codec.TrackParams] what a decoder needs
/// before its first packet. No FFmpeg type appears in this package. That is the
/// promise the Decoder SPI makes to a provider, and it arrives here in phase 2.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media.codec;

import org.jspecify.annotations.NullMarked;
