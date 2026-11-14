package dev.goldberry.i18n;

/// A word the toolkit itself shows or speaks: a text field's menu, the name a
/// screen reader gives a close button. English unless [Labels] is told
/// otherwise.
///
/// Read more: [Text](https://goldberry.dev/docs/guide/text.html).
public enum ToolkitLabel {
    /// The text menu's Cut.
    CUT("goldberry.cut", "Cut"),
    /// The text menu's Copy.
    COPY("goldberry.copy", "Copy"),
    /// The text menu's Paste.
    PASTE("goldberry.paste", "Paste"),
    /// The text menu's Select all.
    SELECT_ALL("goldberry.selectAll", "Select all"),
    /// A dialog's or a tab's close button, as a screen reader names it.
    CLOSE("goldberry.close", "Close"),
    /// A tab strip's new-tab button, as a screen reader names it.
    NEW_TAB("goldberry.newTab", "New tab"),
    /// A chip's or a message's dismiss button, as a screen reader names it.
    DISMISS("goldberry.dismiss", "Dismiss"),
    /// The breadcrumbs' overflow button, as a screen reader names it.
    SHOW_REST_OF_PATH("goldberry.showRestOfPath", "Show the rest of the path"),
    /// A tour's button to the stop before.
    BACK("goldberry.back", "Back"),
    /// A tour's button to the next stop.
    NEXT("goldberry.next", "Next"),
    /// A tour's button on its last stop.
    DONE("goldberry.done", "Done"),
    /// A media player's box, as a screen reader names it.
    MEDIA_PLAYER("goldberry.mediaPlayer", "Media player"),
    /// A media player's control bar, as a screen reader names it.
    MEDIA_CONTROLS("goldberry.mediaControls", "Media controls"),
    /// A video's picture, as a screen reader names it.
    VIDEO("goldberry.video", "Video"),

    /// A rich field's bold: the formatting bar's button and the Format menu's row.
    BOLD("goldberry.bold", "Bold"),

    /// A rich field's italic.
    ITALIC("goldberry.italic", "Italic"),

    /// A rich field's underline.
    UNDERLINE("goldberry.underline", "Underline"),

    /// A rich field's strike-through.
    STRIKE("goldberry.strike", "Strike-through"),

    /// A rich field's code.
    CODE("goldberry.code", "Code"),

    /// A rich field's spoiler.
    SPOILER("goldberry.spoiler", "Spoiler"),

    /// A rich field's link.
    LINK("goldberry.link", "Link"),

    /// A rich field's clear formatting.
    CLEAR_FORMATTING("goldberry.clearFormatting", "Clear formatting"),

    /// The text menu's submenu of a rich field's formatting.
    FORMAT("goldberry.format", "Format"),

    /// The link editor's address field, read out and shown while empty.
    LINK_ADDRESS("goldberry.linkAddress", "Address"),

    /// The link editor's button that puts the link on the words.
    APPLY("goldberry.apply", "Apply"),

    /// The link editor's button that takes a link off.
    REMOVE_LINK("goldberry.removeLink", "Remove link");

    private final String key;
    private final String english;

    ToolkitLabel(String key, String english) {
        this.key = key;
        this.english = english;
    }

    /// The key a bundle of translations names it by: `goldberry.selectAll`.
    public String key() {
        return key;
    }

    /// The English the toolkit shows when nothing else is given.
    public String english() {
        return english;
    }
}
