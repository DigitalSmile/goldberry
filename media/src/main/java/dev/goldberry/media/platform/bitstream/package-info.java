/// The H.264 and HEVC bitstream the platform decoders read and rewrite: the
/// decoder configuration records (`avcC`, `hvcC`) and the packets they
/// describe, shared by every system's provider.
///
/// Pure Java, so every rule here is tested on any system. Not exported.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.media.platform.bitstream;

import org.jspecify.annotations.NullMarked;
