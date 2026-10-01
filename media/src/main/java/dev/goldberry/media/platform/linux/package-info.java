/// GStreamer behind the Decoder SPI: the H.264, HEVC, AAC, AC-3 and E-AC-3
/// decoders a Linux system has installed (ADR-0489).
///
/// Not exported. Three layers, as on macOS:
///
/// - **bindings**, hand-written FFM over `libgstreamer-1.0`, `libgstapp-1.0`,
///   `libgstvideo-1.0` and `libglib-2.0`
///   ([dev.goldberry.media.platform.linux.GstLibrary] says
///   how they are linked), bound once per process by
///   [dev.goldberry.media.platform.linux.GStreamer], which
///   checks the struct offsets against the loaded library first
///   ([dev.goldberry.media.platform.linux.GstLayout]);
/// - **the decoders**: a pipeline per track, `appsrc ! parser ! decoder !
///   converter ! appsink`, with the decoder GStreamer ranks highest, driven from
///   the Engine's decode thread with no callback;
/// - **pure Java** the decoders need and a test can check without GStreamer:
///   the caps and pipeline descriptions, the frame layouts and the colour.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.media.platform.linux;

import org.jspecify.annotations.NullMarked;
