package dev.goldberry.camera.view;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// `camera-view`'s stylesheet, `camera.css`: its size, its backdrop, and the
/// `--gb-camera-*` component tokens.
///
/// An application adds it beside the controls' sheets and `MediaStyles`'. It
/// is written in the theme's `var(--gb-*)` tokens, so it follows whichever
/// theme is on.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class CameraStyles {

    /// The sheet's resource name, beside this class.
    public static final String RESOURCE = "camera.css";

    private CameraStyles() {}

    /// The sheet, in the toolkit's base layer so an application's rules win.
    public static Stylesheet stylesheet() {
        return Stylesheet.resource(CascadeLayer.TOOLKIT_BASE, CameraStyles.class, RESOURCE);
    }
}
