/// FFmpeg, bound by hand: the loader, the checked struct layouts, the views over
/// them, and the custom I/O context.
///
/// Not exported. This is the one package of this module that holds a
/// `MemorySegment`, which is the rule `:natives` keeps for `libgoldberry`,
/// applied to FFmpeg: a media engine binds its own libraries, and no type of
/// the binding appears in an exported signature. Null-marked.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.ffi;

import org.jspecify.annotations.NullMarked;
