/// The backend SPI's window: what a window is to the platform, what to create one
/// with, and how it presents its frames.
///
/// `WindowSpec` says what to open; `BackendWindow` is the open window, which lends
/// a frame buffer, presents a painted frame, and reports its size, scale and
/// presentation path. `Presentation` says whether frames go through the GPU or
/// the CPU, `GpuSurface` how GPU layers are shown, `IconImage` is one size of the
/// window's icon, and `NativeHandle` is the platform's own handle where a page can
/// be embedded. Exported to every module.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#the-low-level-path).
@NullMarked
package dev.goldberry.render.window;

import org.jspecify.annotations.NullMarked;
