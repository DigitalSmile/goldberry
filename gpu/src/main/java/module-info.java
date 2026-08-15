/// Goldberry GPU: the `canvas3d` widget and the GPU composition path.
///
/// `canvas3d` is a leaf render object -- Yoga sizes it like an image, but it
/// owns an SDL_GPU texture instead of pixels. `BackendWindow.gpuSurface()` is in
/// the SPI from day 1 precisely so this module is additive rather than a
/// rearchitecture. See `docs/ARCHITECTURE.md` §12. Populated in M4.
module io.github.digitalsmile.goldberry.gpu {
    requires transitive io.github.digitalsmile.goldberry.core;
}
