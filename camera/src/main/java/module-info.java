/// Goldberry's camera module: the cameras SDL lists, each opened as a
/// picture source, and `camera-view`, which draws one.
///
/// `dev.goldberry.camera.backend` is not exported: SDL's vocabulary stops
/// there.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
module dev.goldberry.camera {

    requires dev.goldberry.common;

    requires dev.goldberry.natives;

    /// Pictures and the view that draws them. `transitive` because a
    /// [dev.goldberry.camera.Camera] is a `PictureSource` and a
    /// [dev.goldberry.camera.view.CameraView] is a widget.
    requires transitive dev.goldberry.media;

    requires transitive static org.jspecify;

    /// The cameras, opening one, and what one says about itself.
    exports dev.goldberry.camera;

    /// A camera that paints a test pattern, for tests and pictures.
    exports dev.goldberry.camera.synthetic;

    /// The widget: `camera-view`, and the stylesheet that sizes it.
    exports dev.goldberry.camera.view;

    /// Opened to `:core`, which reads `camera.css` out of this package.
    opens dev.goldberry.camera.view to
            dev.goldberry.core;

    /// The catalog the weaver generates. The `provides` line is patched into the
    /// compiled descriptor by the build.
    uses dev.goldberry.widgets.markup.WidgetCatalog;
}
