package dev.goldberry.mic.view;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// The microphone widgets' stylesheet, `mic.css`: their sizes, and the
/// `--gb-mic-*` component tokens they are drawn in.
///
/// An application adds it beside the controls' sheets, as it adds
/// `MediaStyles.stylesheet()` for the media widgets. It is written in the
/// theme's `var(--gb-*)` tokens, so it follows whichever theme is on.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class MicStyles {

    /// The sheet's resource name, beside this class.
    public static final String RESOURCE = "mic.css";

    private MicStyles() {}

    /// The sheet, in the toolkit's base layer so an application's rules win.
    public static Stylesheet stylesheet() {
        return Stylesheet.resource(CascadeLayer.TOOLKIT_BASE, MicStyles.class, RESOURCE);
    }
}
