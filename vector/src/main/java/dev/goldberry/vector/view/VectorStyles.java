package dev.goldberry.vector.view;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// The vector widgets' stylesheet, `vector.css`: `svg-view`'s loading and
/// error looks, which are `image`'s.
///
/// An application adds it beside the controls' sheets, as it adds
/// `MediaStyles.stylesheet()` for the media widgets. It is written in the
/// theme's `var(--gb-*)` tokens, so it follows whichever theme is on.
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
public final class VectorStyles {

    /// The sheet's resource name, beside this class.
    public static final String RESOURCE = "vector.css";

    private VectorStyles() {}

    /// The sheet, in the toolkit's base layer so an application's rules win.
    public static Stylesheet stylesheet() {
        return Stylesheet.resource(CascadeLayer.TOOLKIT_BASE, VectorStyles.class, RESOURCE);
    }
}
