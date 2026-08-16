package io.github.digitalsmile.goldberry.kdl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// One node in a KDL document — which, per §9, is one widget.
///
/// The mapping the architecture states, in one place: node name is the widget
/// type, string arguments are the primary content, properties are attributes,
/// children are children.
///
/// @param name       the node name
/// @param arguments  positional values, in source order
/// @param properties `key=value` pairs; a repeated key keeps the **last**, which
///                   is what KDL specifies
/// @param children   child nodes, in source order
/// @param line       1-based line the name is on
/// @param column     1-based column
public record KdlNode(
        String name,
        List<KdlValue> arguments,
        Map<String, KdlValue> properties,
        List<KdlNode> children,
        int line,
        int column) {

    public KdlNode {
        Objects.requireNonNull(name, "name");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        // Copied into a LinkedHashMap rather than Map.copyOf: source order is
        // what an error message should list attributes in, and Map.copyOf does
        // not keep it.
        properties = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(properties, "properties")));
        children = List.copyOf(Objects.requireNonNull(children, "children"));
    }

    /// The first argument, which §9 calls the node's primary content — the
    /// `"Apply"` of `button "Apply"`.
    public Optional<KdlValue> argument() {
        return arguments.isEmpty() ? Optional.empty() : Optional.of(arguments.getFirst());
    }

    /// A property by name.
    public Optional<KdlValue> property(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    /// A string property, or `null` if it is absent.
    ///
    /// Attributes are overwhelmingly strings — `class`, `id`, `icon`, `action` —
    /// so this is the accessor an inflater reaches for.
    public String stringProperty(String key) {
        var value = properties.get(key);
        return value == null ? null : value.asString();
    }

    /// Child nodes with this name.
    public List<KdlNode> childrenNamed(String name) {
        return children.stream().filter(child -> child.name().equals(name)).toList();
    }

    /// Where this node came from, for a message that has to point at it.
    public String position() {
        return line + ":" + column;
    }

    @Override
    public String toString() {
        var text = new StringBuilder(name);
        arguments.forEach(argument -> text.append(' ').append(argument));
        properties.forEach((key, value) -> text.append(' ').append(key).append('=').append(value));
        if (!children.isEmpty()) {
            text.append(" { ").append(children.size()).append(" child(ren) }");
        }
        return text.toString();
    }
}
