/// The built-in decoders: the reference codecs in `libgoldberry-media`, each
/// behind the Decoder SPI like a decoder an application brings.
///
/// [dev.goldberry.media.decoder.BuiltInDecoders] lists them, one provider per
/// codec family, in the packages under this one: `av1` (dav1d), `vpx` (VP9 and
/// VP8), `opus`, `vorbis`, `flac` and `mp3`. The Engine consults an
/// application's providers first and these last, whatever anyone's priority
/// says, so an application that brings its own AV1 decoder has it used.
///
/// Not exported: the providers reach an application through
/// [dev.goldberry.media.MediaCapabilities] and the player's status, by name.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.decoder;

import org.jspecify.annotations.NullMarked;
