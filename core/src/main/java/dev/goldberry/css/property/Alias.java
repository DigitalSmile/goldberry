package dev.goldberry.css.property;

import java.util.Objects;

/// Another name for a property, which CSS keeps for compatibility:
/// `word-wrap` is `overflow-wrap`.
///
/// @param name   the other name
/// @param target the property it names, never an alias itself
public record Alias(String name, String target) implements Property {

    public Alias {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public boolean readable() {
        return true;
    }
}
