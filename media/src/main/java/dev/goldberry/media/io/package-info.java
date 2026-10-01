/// Where the bytes come from (`docs/goldberry-media.md` §4).
///
/// FFmpeg is built with no network layer and no protocols. Every byte it demuxes
/// is read through a [dev.goldberry.media.io.MediaIO]. A
/// [dev.goldberry.media.io.Source] names what to open, and
/// [dev.goldberry.media.io.MediaIOs] picks the protocol that
/// opens it, by URI scheme. Built in are `file:` and, over the JDK's HTTP client,
/// `http:` and `https:` ([dev.goldberry.media.io.HttpIO]: Range
/// seeks, a read-ahead cache, reconnects, ICY radio metadata). Applications add
/// protocols as [dev.goldberry.media.io.MediaIOProvider]s.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.media.io;

import org.jspecify.annotations.NullMarked;
