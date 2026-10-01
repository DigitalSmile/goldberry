package dev.goldberry.example.book.pictures;

import java.util.Objects;

/// One picture the guide shows of a widget: the markup under the widget's own
/// heading, which is what the picture is a render of.
///
/// @param name    the markup name, which is the heading and the file stem
/// @param chapter the chapter under `book/src` the heading is in
/// @param line    the line the sample's fence opens on, for a message
/// @param markup  the `kdl` sample, verbatim
public record WidgetPicture(String name, String chapter, int line, String markup) {

    public WidgetPicture {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(chapter, "chapter");
        Objects.requireNonNull(markup, "markup");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a picture needs a name");
        }
    }

    /// The file the picture is committed as for `theme`, relative to
    /// `book/src/images`: `button-dark.webp`.
    public String file(Shade shade) {
        return name + "-" + shade.suffix() + ".webp";
    }

    /// Where the sample is, for a failure message.
    public String where() {
        return chapter + ":" + line;
    }
}
