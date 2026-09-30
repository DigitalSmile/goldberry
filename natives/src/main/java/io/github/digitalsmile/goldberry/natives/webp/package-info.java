/// libwebp: decoding a WebP still or animation into pixels Java owns, and encoding
/// one (`docs/gaps.md` G35a, ADR-0329).
///
/// Every call copies into Java arrays and frees libwebp's buffer before it returns,
/// so nothing here has a lifetime to hand over. Exported to `:core` alone: an
/// application calls `Image.decode`, which names no type of this module.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.webp;

import org.jspecify.annotations.NullMarked;
