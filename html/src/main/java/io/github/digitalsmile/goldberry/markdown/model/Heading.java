package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// `# A heading`.
///
/// @param level 1 to 6, which is CommonMark's range and md4c's
/// @param content the heading's words
public record Heading(int level, List<Inline> content) implements Block {

    public Heading {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("a heading is level 1 to 6, not " + level);
        }
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    @Override
    public List<Inline> children() {
        return content;
    }

    /// The heading's words with every mark dropped — what an outline, a table of
    /// contents or an anchor id is made of.
    public String text() {
        return Inlines.text(content);
    }
}
