package dev.goldberry.css.parse;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.media.MediaCondition;

/// One `@import`, as the parser read it: where the sheet is, and when its
/// rules apply.
///
/// ```css
/// @import "tokens.css";
/// @import url(narrow.css) (max-width: 600px);
/// ```
///
/// The parser does not load anything. A sheet read as a resource resolves its
/// imports beside itself; one read from text has nowhere to resolve them from,
/// and reports each one it skipped.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#strict-and-lenient-sheets).
///
/// @param href   the address as written, relative to the importing sheet
/// @param media  when the imported rules apply: [MediaCondition#ALWAYS] when no
///               media list was written
/// @param layer  the `@layer` the imported rules go in, from `layer(…)`; null
///               for none
/// @param line   1-based line of the `@import`
/// @param column 1-based column of the `@import`
public record Import(
        String href, MediaCondition media, @Nullable String layer, int line, int column) {

    public Import {
        Objects.requireNonNull(href, "href");
        Objects.requireNonNull(media, "media");
        if (href.isBlank()) {
            throw new IllegalArgumentException("an @import names a sheet");
        }
    }

    /// An import into no layer.
    public Import(String href, MediaCondition media, int line, int column) {
        this(href, media, null, line, column);
    }
}
