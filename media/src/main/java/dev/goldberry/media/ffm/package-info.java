/// The bindings to `libgoldberry-media`: the Foreign Function and Memory side of
/// the engine, and nothing above it.
///
/// `libgoldberry-media` is one shared library per target that links the
/// reference decoders — dav1d, libvpx, Opus, libogg and libvorbis, libtheora,
/// FLAC, minimp3 — and libyuv statically, and exports their own C APIs. This package
/// loads it ([dev.goldberry.media.ffm.MediaNatives]), checks the layout file the
/// superbuild wrote beside it against the struct layouts declared here
/// ([dev.goldberry.media.ffm.NativeStructs], [dev.goldberry.media.ffm.LayoutCheck]),
/// and binds every function the decoders call, one holder per symbol under
/// `…ffm.calls`. [dev.goldberry.media.ffm.MediaLibrary] is the result.
///
/// Not exported. No type of an upstream appears in anything that is: the
/// decoders under `…media.decoder` speak the Decoder SPI's vocabulary, and the
/// converter under `…media.picture` speaks pictures.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.ffm;

import org.jspecify.annotations.NullMarked;
