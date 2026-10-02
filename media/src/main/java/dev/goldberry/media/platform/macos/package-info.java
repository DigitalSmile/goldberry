/// VideoToolbox and AudioToolbox behind the Decoder SPI: macOS's H.264, HEVC,
/// AAC, AC-3 and E-AC-3 decoders.
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
/// Null-marked.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.platform.macos;

import org.jspecify.annotations.NullMarked;
