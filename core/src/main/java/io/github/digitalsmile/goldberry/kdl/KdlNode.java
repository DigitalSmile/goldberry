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

    /// A boolean property, `false` when absent.
    ///
    /// KDL 2.0 writes booleans `#true` / `#false`, and §9's own examples use them
    /// for exactly this — `default=#true`, `disabled=#true`. A **bare** attribute
    /// is not a KDL thing: `disabled` on its own is an argument, not a property,
    /// so it is not accepted here.
    ///
    /// A value that is not a boolean is `false` rather than an error, for the
    /// same reason an unparseable declaration is dropped rather than fatal
    /// (ADR-0051): a document being edited is broken more often than it is
    /// whole.
    public boolean booleanProperty(String key) {
        return properties.get(key) instanceof KdlValue.Bool bool && bool.value();
    }

    /// A numeric property, or `fallback` when absent.
    ///
    /// The third kind an attribute comes in, and the first widget to need it is
    /// `slider` — `min`, `max`, `value` and `step` are numbers in a way that
    /// `class` and `disabled` are not.
    ///
    /// A value that is not a number is `fallback` rather than an error, for the
    /// reason [#booleanProperty] gives: reload is deliberately forgiving, and a
    /// document being edited is broken more often than it is whole. A `min` that
    /// was mistyped therefore renders a usable control rather than refusing the
    /// window — and the *structural* mistakes a slider can make, `max <= min` and
    /// a negative `step`, are still refused at construction, because those are
    /// not a half-typed number but a contradiction.
    public double numberProperty(String key, double fallback) {
        return properties.get(key) instanceof KdlValue.Num number ? number.value() : fallback;
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
