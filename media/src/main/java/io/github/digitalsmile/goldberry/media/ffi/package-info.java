/// FFmpeg, bound by hand: the loader, the checked struct layouts, the views over
/// them, and the custom I/O context (`docs/goldberry-media.md` §2 and §3).
///
/// Not exported. This is the one package of this module that holds a
/// `MemorySegment`, which is the rule `:natives` keeps for `libgoldberry`,
/// applied to FFmpeg ([ADR-0461](../book/src/adr/0461-a-media-engine-binds-its-own-libraries.md)).
/// No type of it appears in an exported signature.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media.ffi;

import org.jspecify.annotations.NullMarked;
