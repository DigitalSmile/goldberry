package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// `` `code` `` — an inline code span.
///
/// A leaf holding text rather than inlines, because nothing inside a code span is
/// markup. That is CommonMark's rule and the whole point of the construct.
///
/// @param code the text, verbatim
public record Code(String code) implements Inline {

    public Code {
        Objects.requireNonNull(code, "code");
    }

    @Override
    public List<Inline> children() {
        return List.of();
    }
}
