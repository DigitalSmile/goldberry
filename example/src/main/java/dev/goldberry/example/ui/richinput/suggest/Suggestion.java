package dev.goldberry.example.ui.richinput.suggest;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.text.mark.MarkKind;

/// One row of a suggestion list, and what choosing it puts in the field.
///
/// ```java
/// new Suggestion("user-ann", "Ann Lee", "@ann", "@Ann Lee", new MarkKind.Mention("user:ann"), List.of("ann", "ann",
/// "lee"));
/// ```
///
/// @param id     unique within its list; the row's element id is `suggestion-` and this
/// @param title  the row's first words
/// @param detail the row's quieter words beside them
/// @param text   what replaces the query once it is chosen
/// @param kind   the mark `text` goes in under: a mention is an atom, a custom
///               kind is the application's, and null is plain text
/// @param keys   the words a query is matched against, each from its start
public record Suggestion(
        String id,
        String title,
        String detail,
        String text,
        @Nullable MarkKind kind,
        List<String> keys) {

    public Suggestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(text, "text");
        keys = keys.stream().map(key -> key.toLowerCase(Locale.ROOT)).toList();
        if (id.isBlank() || title.isBlank() || text.isBlank()) {
            throw new IllegalArgumentException("a suggestion has an id, a title and the text it puts in");
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("a suggestion nothing can match is never shown: " + id);
        }
    }

    /// Whether `query` starts one of the keys, ignoring case. An empty query
    /// matches every suggestion.
    public boolean matches(String query) {
        var wanted = query.toLowerCase(Locale.ROOT);
        return keys.stream().anyMatch(key -> key.startsWith(wanted));
    }
}
