package dev.goldberry.css.container;

import java.util.List;
import java.util.Objects;

/// A container a query can be answered against: what it is, and the size of
/// its content box when it was last laid out.
///
/// @param type   the axes it answers for
/// @param names  its `container-name`s
/// @param width  its content box's width, in logical pixels
/// @param height its content box's height
public record QueryContainer(ContainerType type, List<String> names, double width, double height) {

    public QueryContainer {
        Objects.requireNonNull(type, "type");
        names = List.copyOf(Objects.requireNonNull(names, "names"));
    }
}
