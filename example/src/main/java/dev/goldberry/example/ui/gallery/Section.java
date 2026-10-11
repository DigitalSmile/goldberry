package dev.goldberry.example.ui.gallery;

/// A group of screens in the sidebar.
///
/// Seven of them, so the thirty screens read as a short list of headings rather
/// than as a strip that runs off the window. The order here is the sidebar's,
/// and [Gallery#TABS] lists the screens section by section in the same order,
/// so `Ctrl+1`… and the sidebar agree about which screen comes next.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html).
public enum Section {

    /// Laying out, scrolling, text and the stylesheet.
    BASICS("Basics", "blocks"),

    /// What a user presses, picks, slides and types into.
    CONTROLS("Controls", "toggle-left"),

    /// Containers, collections and the ways between screens.
    STRUCTURE("Structure", "layout-panel-left"),

    /// Charts, drawing, glyphs, the GPU and media.
    GRAPHICS("Graphics & media", "image"),

    /// Documents: Markdown, HTML and the web view.
    CONTENT("Content", "file-text"),

    /// The keyboard and pointer, diagnostics, and the application around it all.
    PLATFORM("Platform", "monitor"),

    /// Whole things built from the parts.
    DEMOS("Demos", "sparkles");

    private final String title;
    private final String icon;

    Section(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    /// The heading the sidebar shows.
    public String title() {
        return title;
    }

    /// The bundled icon beside the heading.
    public String icon() {
        return icon;
    }
}
