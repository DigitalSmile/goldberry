package dev.goldberry.widgets.core;

import java.util.List;

/// The names of the structural widgets: `text`, `row`, `column`, `panel`,
/// `stack`, `spacer`, `scroll`, `affix`, `canvas`, `image`, `qr-code` and
/// `icon`.
///
/// These are ordinary widgets, registered for markup by their own `@Markup`
/// annotations like every other widget. This list is kept apart from
/// [dev.goldberry.widgets.Controls] so that the catalogue's tests can check the
/// structural names against the stylesheet and the markup on their own.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#the-layout-widgets).
public final class Primitives {

    private Primitives() {}

    /// The CSS type names of every structural widget, which is what the parity
    /// test checks the other two forms against.
    public static List<String> builtInTypes() {
        // `scroll` and not `scroll-content`: the parity test checks the names a
        // document may write, and the content node is one this widget builds
        // for itself. A list kept by hand beside a registry the build generates
        // falls behind it, so every `@Markup` name in this package belongs here.
        return List.of(
                "text", "link", "row", "column", "panel", "stack", "spacer", "scroll", "affix", "canvas", "image",
                "qr-code", "icon");
    }
}
