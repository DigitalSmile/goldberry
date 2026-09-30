/// Where the showcase's Audio and Video screens get their media: the samples,
/// the loopback HTTP server that plays them as a network source, and the
/// pure-Java PCM decoder that shows the Decoder SPI from an application's side
/// (ADR-0496).
///
/// Not UI. The screens that use them are in `…example.ui`.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.example.media;

import org.jspecify.annotations.NullMarked;
