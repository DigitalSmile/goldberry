package dev.goldberry.css.property;

import java.util.List;
import java.util.Objects;

/// A property that writes several others at once: `padding` writes its four
/// sides, `border` the width, colour and style of each.
///
/// A shorthand holds no value of its own. Copying it copies each longhand it
/// writes, which is how `padding: inherit` means the parent's four sides.
///
/// @param name      the property's name
/// @param longhands the names it writes, each a [Longhand] or another shorthand;
///                  never an alias
public record Shorthand(String name, List<String> longhands) implements Property {

    public Shorthand {
        Objects.requireNonNull(name, "name");
        longhands = List.copyOf(longhands);
        if (longhands.isEmpty()) {
            throw new IllegalArgumentException(name + " writes nothing");
        }
    }

    @Override
    public boolean readable() {
        return true;
    }
}
