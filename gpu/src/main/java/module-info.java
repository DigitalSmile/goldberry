/// Goldberry GPU: the `canvas3d` widget and the GPU composition path.
///
/// `canvas3d` is a leaf render object -- Yoga sizes it like an image, but it
/// will own an SDL_GPU texture instead of pixels. See `docs/ARCHITECTURE.md` §12.
///
/// **Being built** (`docs/gpu-plan.md`, M4). Exported: the GPU API in
/// `io.github.digitalsmile.goldberry.gpu` -- a device, its textures, buffers,
/// samplers, shaders and pipelines, frames of scoped copy and render passes,
/// and readback -- which `canvas3d` renderers and GPU layers are written
/// against (phase 2). A device reaches them from the backend; the composited
/// window and `BackendWindow.gpuSurface()` that hand it out are phase 3, and
/// `canvas3d` itself phase 5. The toolkit's own shaders and the quad and
/// Y'CbCr arithmetic they need stay in the unexported `…gpu.render`.
///
/// `BackendWindow.gpuSurface()` does not exist yet, and this comment claimed it
/// was "in the SPI from day 1" until the 2026-09-18 review read it against
/// `Backend`, whose own doc says the GPU surface "is absent from this cut and not
/// dropped: it needs a consumer before its shape can be decided, and an interface
/// designed against nothing is an interface that gets designed twice"
/// (ADR-0019). That is the position, and `canvas3d` is the consumer it is
/// waiting for.
module io.github.digitalsmile.goldberry.gpu {
    requires transitive io.github.digitalsmile.goldberry.core;

    /// The GPU API (`docs/gpu-plan.md`, phase 2). Its signatures name `:core`'s
    /// `PixelBuffer` and `PhysicalRect`, hence `requires transitive` above, and
    /// nothing of `:natives`.
    exports io.github.digitalsmile.goldberry.gpu;

    /// SDL_GPU's wrappers, which `:natives` exports to this module and to
    /// `:core` alone (ADR-0475). Not `transitive`: no type of `:natives` is in
    /// this module's public surface.
    requires io.github.digitalsmile.goldberry.natives;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    ///
    /// `static`, because they are compile-time only: a consumer's runtime module
    /// path does not need them. `transitive`, because `@Nullable` appears on
    /// exported signatures, and a consumer compiling against one has to be able
    /// to read it — `-Xlint:exports` says so in as many words
    /// (`docs/testing.md` §2).
    requires transitive static org.jspecify;
}
