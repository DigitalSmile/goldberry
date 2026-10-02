/// Where the showcase's Audio and Video screens get their media: the samples,
/// the loopback HTTP server that plays them as a network source, and the
/// pure-Java PCM decoder that shows the Decoder SPI from an application's side.
///
/// Not UI. The screens that use them are in `…example.ui`.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
@NullMarked
package dev.goldberry.example.media;

import org.jspecify.annotations.NullMarked;
