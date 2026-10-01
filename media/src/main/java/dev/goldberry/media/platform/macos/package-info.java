/// VideoToolbox and AudioToolbox behind the Decoder SPI: macOS's H.264, HEVC,
/// AAC, AC-3 and E-AC-3 decoders (ADR-0472).
///
/// Not exported. Three layers:
///
/// - **bindings**, hand-written FFM over the system frameworks, one class per
///   framework ([dev.goldberry.media.platform.macos.Framework]
///   says how they are linked), bound once per process by
///   [dev.goldberry.media.platform.macos.Frameworks];
/// - **the decoders**, one per framework, which turn packets into the frames the
///   Engine presents: pictures lent straight from VideoToolbox's pixel buffers,
///   samples as AudioToolbox decodes them;
/// - **pure Java** the decoders need and a test can check without a Mac: the
///   parameter-set reader that gives the reorder depth, the reorder buffer, the
///   AAC cookie and the channel order.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.media.platform.macos;

import org.jspecify.annotations.NullMarked;
