package dev.goldberry.css.property;

import java.util.Objects;

/// A property the engine reads and checks and that changes nothing here,
/// because CSS says it changes nothing for the boxes there are.
///
/// `justify-items` and `justify-self` are the two: CSS ignores both on a flex
/// item, and every box here is one. A sheet that writes them for a grid it
/// does not have gets no warning, and a value CSS would refuse is still
/// dropped, so a typo is still caught.
///
/// @param name the property's name
public record Inert(String name) implements Property {

    public Inert {
        Objects.requireNonNull(name, "name");
    }

    @Override
    public boolean readable() {
        return true;
    }
}
