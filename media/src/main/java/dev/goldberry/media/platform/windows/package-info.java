/// Media Foundation behind the Decoder SPI: Windows's H.264, HEVC, AAC, AC-3 and
/// E-AC-3 decoders, as decoder MFTs.
///
/// Not exported. Three layers:
///
/// - **bindings**, hand-written FFM over `mfplat.dll` and `ole32.dll` and over
///   the COM interfaces they hand out, one class per library or interface
///   ([dev.goldberry.media.platform.windows.WindowsLibrary]
///   says how they are linked, [dev.goldberry.media.platform.windows.Com]
///   how a method is called through its vtable), bound once per process by
///   [dev.goldberry.media.platform.windows.MediaFoundation];
/// - **the decoders**, one for video and one for audio, over a shared
///   [dev.goldberry.media.platform.windows.Transform] that
///   drives one MFT: pictures lent straight from the MFT's output buffer,
///   samples as the MFT decodes them;
/// - **pure Java** the decoders need and a test can check without Windows: the
///   GUIDs in their Windows byte order, the picture's planes, crop and colour,
///   the AAC user data, and the sample clock.
///
/// Written without a Windows machine to run it on: the vtable slots, GUIDs and
/// struct layouts are from the Windows SDK headers, and CI on Windows is what
/// confirms them.
///
/// Null-marked.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.platform.windows;

import org.jspecify.annotations.NullMarked;
