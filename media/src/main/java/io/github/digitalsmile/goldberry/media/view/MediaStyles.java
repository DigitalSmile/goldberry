package io.github.digitalsmile.goldberry.media.view;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;

/// The media widgets' stylesheet, `media.css`: layout for `audio-player` and the
/// `--gb-media-*` component tokens (`docs/goldberry-media.md` §6).
///
/// An application adds it beside the controls' sheets, as it adds
/// `MarkdownStyles.stylesheet()` for `markdown-view`. It is written in the theme's
/// `var(--gb-*)` tokens, so it follows whichever theme is on.
public final class MediaStyles {

    /// The sheet's resource name, beside this class.
    public static final String RESOURCE = "media.css";

    private MediaStyles() {}

    /// The sheet, in the toolkit's base layer so an application's rules win.
    public static Stylesheet stylesheet() {
        return Stylesheet.resource(CascadeLayer.TOOLKIT_BASE, MediaStyles.class, RESOURCE);
    }
}
