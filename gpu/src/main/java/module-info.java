/// Goldberry GPU: the `canvas3d` widget and the GPU composition path.
///
/// `canvas3d` is a leaf render object: the layout sizes it like an image, and
/// it owns an SDL_GPU texture instead of pixels.
///
/// Exported: the GPU API in `dev.goldberry.gpu` -- a device, its textures,
/// buffers, samplers, shaders and pipelines, frames of scoped copy and render
/// passes, and readback -- and `GpuLayer`, what a painter places in a frame for
/// the GPU to draw. A layer renders with the device of the window it is shown
/// in: composited under the window's frame, or read back into it where the
/// window presents on the CPU. And the `canvas3d` widget in `…gpu.view`, with
/// the `Canvas3dRenderer` an application draws it with. And `…gpu.offscreen`,
/// a device and read-back surface for a picture with no window, which an
/// application's test hands to `Offscreen`. And `…gpu.video`, the layer video
/// is shown through, exported to `:media` alone. The toolkit's own
/// shaders and the quad and Y'CbCr arithmetic they need stay in the unexported
/// `…gpu.render`.
///
/// Putting this module on the module path is enough to make every window
/// present through the GPU; `-Dgoldberry.gpu=off` keeps them on the CPU.
///
/// `@SuppressWarnings("module")` for the one qualified export, to `:media`,
/// which is not on this module's compile path and cannot be: `:media` requires
/// this module (statically), so javac compiles this one first and warns that
/// the target is not found. `:natives` does the same for its readers.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
@SuppressWarnings("module")
module dev.goldberry.gpu {
    requires transitive dev.goldberry.core;

    /// `canvas3d` is a widget, built with `:widgets`' markup and `message`;
    /// `transitive` because [dev.goldberry.gpu.view.Canvas3d]
    /// is a widget and its markup signature names `Wiring`.
    requires transitive dev.goldberry.widgets;

    /// The GPU API. Its signatures name `:core`'s
    /// `PixelBuffer` and `PhysicalRect`, hence `requires transitive` above, and
    /// nothing of `:natives`.
    exports dev.goldberry.gpu;

    /// `canvas3d` and the renderer an application draws it with.
    exports dev.goldberry.gpu.view;

    /// A GPU for a picture with no window under it: the surface an
    /// application's test hands to `Offscreen.gpu`.
    exports dev.goldberry.gpu.offscreen;

    /// Video on the GPU: the layer `:media`'s `video-view` shows its pictures
    /// through when this module is present. To `:media`
    /// alone, which `requires static` this module: it is the one caller, and an
    /// application shows video with `video-view`, or draws it into a texture of
    /// its own with `:media`'s `PictureRenderer`.
    exports dev.goldberry.gpu.video to
            dev.goldberry.media;

    /// The catalog the weaver generates for `canvas3d`. The `provides` line is
    /// patched into the compiled descriptor by the build.
    uses dev.goldberry.widgets.markup.WidgetCatalog;

    /// The composited window: the sdl3
    /// backend finds this with `ServiceLoader`, and by default every window
    /// presents through it, falling back to the CPU where it cannot.
    /// Being on the module path is enough; an application need not require
    /// this module for its windows to be composited.
    provides dev.goldberry.render.composite.Compositor with
            dev.goldberry.gpu.composite.SdlCompositor;

    /// SDL_GPU's wrappers, which `:natives` exports to this module and to
    /// `:core` alone. Not `transitive`: no type of `:natives` is in
    /// this module's public surface.
    requires dev.goldberry.natives;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    ///
    /// `static`, because they are compile-time only: a consumer's runtime module
    /// path does not need them. `transitive`, because `@Nullable` appears on
    /// exported signatures, and a consumer compiling against one has to be able
    /// to read it — `-Xlint:exports` says so in as many words.
    requires transitive static org.jspecify;
}
