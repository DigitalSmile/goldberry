/// Where the bytes come from (`docs/goldberry-media.md` §4).
///
/// FFmpeg is built with no network layer and no protocols. Every byte it demuxes
/// is read through a [io.github.digitalsmile.goldberry.media.io.MediaIO]. A
/// [io.github.digitalsmile.goldberry.media.io.Source] names what to open, and
/// [io.github.digitalsmile.goldberry.media.io.MediaIOs] picks the protocol that
/// opens it, by URI scheme. Applications add protocols as
/// [io.github.digitalsmile.goldberry.media.io.MediaIOProvider]s.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media.io;

import org.jspecify.annotations.NullMarked;
