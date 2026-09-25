/// Goldberry GPU: the `canvas3d` widget and the GPU composition path.
///
/// `canvas3d` is a leaf render object -- Yoga sizes it like an image, but it
/// will own an SDL_GPU texture instead of pixels. See `docs/ARCHITECTURE.md` §12.
///
/// **Being built** (`docs/gpu-plan.md`, M4). Exported: the GPU API in
/// `io.github.digitalsmile.goldberry.gpu` -- a device, its textures, buffers,
/// samplers, shaders and pipelines, frames of scoped copy and render passes,
/// and readback (phase 2) -- and `GpuLayer`, what a painter places in a frame
/// for the GPU to draw (phase 4). A layer renders with the device of the window
/// it is shown in: composited under the window's frame, or read back into it
/// where the window presents on the CPU (ADR-0479, ADR-0481). And the
/// `canvas3d` widget in `…gpu.view`, with the `Canvas3dRenderer` an application
/// draws it with (phase 5, ADR-0482). The toolkit's own shaders and the quad and
/// Y'CbCr arithmetic they need stay in the unexported `…gpu.render`.
///
/// `BackendWindow.gpuSurface()` came with its consumer, the GPU layer
/// (ADR-0481), and not "from day 1" as this comment once said: the GPU surface
/// "needs a consumer before its shape can be decided, and an interface designed
/// against nothing is an interface that gets designed twice" (ADR-0019).
module io.github.digitalsmile.goldberry.gpu {
    requires transitive io.github.digitalsmile.goldberry.core;

    /// `canvas3d` is a widget, built with `:widgets`' markup and `message`;
    /// `transitive` because [io.github.digitalsmile.goldberry.gpu.view.Canvas3d]
    /// is a widget and its markup signature names `Wiring`.
    requires transitive io.github.digitalsmile.goldberry.widgets;

    /// The GPU API (`docs/gpu-plan.md`, phase 2). Its signatures name `:core`'s
    /// `PixelBuffer` and `PhysicalRect`, hence `requires transitive` above, and
    /// nothing of `:natives`.
    exports io.github.digitalsmile.goldberry.gpu;

    /// `canvas3d` and the renderer an application draws it with (phase 5,
    /// ADR-0482).
    exports io.github.digitalsmile.goldberry.gpu.view;

    /// The catalog the weaver generates for `canvas3d`. The `provides` line is
    /// patched into the compiled descriptor by the build (ADR-0131).
    uses io.github.digitalsmile.goldberry.widgets.markup.WidgetCatalog;

    /// The composited window (`docs/gpu-plan.md`, phase 3; ADR-0479): the sdl3
    /// backend finds this with `ServiceLoader`, and by default every window
    /// presents through it, falling back to the CPU where it cannot (ADR-0480).
    /// Being on the module path is enough; an application need not require
    /// this module for its windows to be composited.
    provides io.github.digitalsmile.goldberry.render.composite.Compositor with
            io.github.digitalsmile.goldberry.gpu.render.SdlCompositor;

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
