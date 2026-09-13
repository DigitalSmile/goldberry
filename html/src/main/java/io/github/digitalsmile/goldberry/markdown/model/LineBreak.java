package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;

/// A break inside a paragraph.
///
/// @param hard whether the author asked for one — two trailing spaces, or a
///        backslash. A soft break is a newline in the source that means nothing:
///        HTML emits it as whitespace, a widget renderer lets the line wrap where
///        it likes, and only a hard break forces one
public record LineBreak(boolean hard) implements Inline {

    @Override
    public List<Inline> children() {
        return List.of();
    }
}
