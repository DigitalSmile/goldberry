package dev.goldberry.css.container;

import java.util.List;
import java.util.Objects;

/// What a box is as a query container: `container-type` and `container-name`.
/// Neither inherits.
///
/// @param type  whether it is a container, and for which axes
/// @param names the names an `@container` can ask for it by; empty for none
public record Containment(ContainerType type, List<String> names) {

    /// Not a container, and no name.
    public static final Containment NONE = new Containment(ContainerType.NORMAL, List.of());

    public Containment {
        Objects.requireNonNull(type, "type");
        names = List.copyOf(Objects.requireNonNull(names, "names"));
    }

    /// This, of `value`.
    public Containment type(ContainerType value) {
        return new Containment(value, names);
    }

    /// This, called `value`.
    public Containment names(List<String> value) {
        return new Containment(type, value);
    }

    /// Whether this box answers container queries at all.
    public boolean isContainer() {
        return type != ContainerType.NORMAL;
    }
}
