package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// A run of plain words.
///
/// Entities are already resolved: `AT&amp;T` is one of these holding `AT&T`,
/// because a reader cannot read an entity and every consumer of this model would
/// otherwise have to resolve it for itself. The HTML writer escapes on the way out,
/// which is the same information travelling in the other direction.
///
/// @param text the words
public record Text(String text) implements Inline {

    public Text {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public List<Inline> children() {
        return List.of();
    }
}
