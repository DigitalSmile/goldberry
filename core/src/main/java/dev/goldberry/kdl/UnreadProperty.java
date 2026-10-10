package dev.goldberry.kdl;

import java.util.List;
import java.util.Objects;

/// A property a document wrote and nothing that built the document asked for:
/// the `gap=8` of `row gap=8`, where the gap is the stylesheet's and no factory asks for it.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#parsing-and-inflating).
///
/// @param node   the name of the node it was written on
/// @param key    the property's name
/// @param value  what it was set to
/// @param read   the properties of the same node that **were** asked for, in the
///               order they were asked: the likeliest place to find what a typo
///               meant
/// @param line   1-based line of the node
/// @param column 1-based column of the node
public record UnreadProperty(String node, String key, KdlValue value, List<String> read, int line, int column) {

    public UnreadProperty {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        read = List.copyOf(Objects.requireNonNull(read, "read"));
    }

    /// Where the node is, as [KdlNode#position] writes it.
    public String position() {
        return line + ":" + column;
    }

    /// One line for a log or an error: what was ignored, where, and what the
    /// node did read.
    public String describe() {
        var text = new StringBuilder()
                .append(node)
                .append(" at ")
                .append(position())
                .append(" ignores ")
                .append(key)
                .append('=')
                .append(value);
        if (read.isEmpty()) {
            text.append("; it reads no properties");
        } else {
            text.append("; it reads ").append(String.join(", ", read));
        }
        return text.toString();
    }
}
