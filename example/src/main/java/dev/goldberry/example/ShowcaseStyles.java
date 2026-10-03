package dev.goldberry.example;

import java.util.List;
import java.util.stream.Stream;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// The showcase's own stylesheets: `showcase.css`, which is the window and what
/// every screen shares, then one sheet per package of screens.
///
/// A sheet per package so a screen's rules sit beside nothing else's. They are
/// loaded after `showcase.css`, so a screen can override a shared rule the way the
/// application overrides a control's.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html).
public final class ShowcaseStyles {

    /// The packages under `dev.goldberry.example.ui` that have a sheet, as
    /// `chapter-<name>.css` beside this class.
    static final List<String> CHAPTERS = List.of(
            "layout",
            "text",
            "controls",
            "forms",
            "panels",
            "collections",
            "navigation",
            "menus",
            "overlays",
            "charts",
            "drawing",
            "content",
            "media",
            "gpu",
            "styling",
            "input",
            "windows",
            "diagnostics",
            "application",
            "guide");

    private ShowcaseStyles() {}

    /// Every sheet, in cascade order.
    public static List<Stylesheet> sheets() {
        return Stream.concat(Stream.of("showcase.css"), CHAPTERS.stream().map(name -> "chapter-" + name + ".css"))
                .map(file -> Stylesheet.resource(CascadeLayer.APPLICATION, ShowcaseStyles.class, file))
                .toList();
    }
}
