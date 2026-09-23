/// Audio and video over FFmpeg, driven from Java (`docs/goldberry-media.md`).
///
/// Phase 1 of `docs/media-plan.md`: [io.github.digitalsmile.goldberry.media.MediaProbe]
/// opens a source through the I/O SPI and lists its tracks, and every failure is
/// a [io.github.digitalsmile.goldberry.media.MediaError]. The Engine, the Decoder
/// SPI and the widgets are built on top of this in the phases after it.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media;

import org.jspecify.annotations.NullMarked;
