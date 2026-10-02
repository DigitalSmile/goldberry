/// The backend SPI: the one interface the platform is reached through, and the
/// values that cross it.
///
/// `Backend` opens windows and popups, pumps events and lends the clipboard,
/// the tray, the file dialogs and web pages; the sub-packages hold each of those.
/// `PixelBuffer` and `DamageRect` are what a painted frame crosses as, `Cursor`
/// is the pointer's shape, and `GpuContent` and `GpuPlacement` are how a GPU
/// layer is placed in a frame. Two backends implement it, `sdl3` and `headless`.
/// Exported to every module.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Architecture](https://goldberry.dev/docs/overview/architecture.html#the-backend-spi).
@NullMarked
package dev.goldberry.render;

import org.jspecify.annotations.NullMarked;
