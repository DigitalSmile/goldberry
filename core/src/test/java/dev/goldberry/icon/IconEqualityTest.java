package dev.goldberry.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Icons compare by value, so a box built from a second lookup of the same
/// icon is a box the render tree leaves alone.
class IconEqualityTest {

    @Test
    @DisplayName("two lookups of one name at one size are equal, with equal hashes")
    void sameLookup() {
        var first = Icon.find("plus", 16).orElseThrow();
        var second = Icon.find("plus", 16).orElseThrow();
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    @DisplayName("another size, another name or another outline is another icon")
    void differences() {
        var plus = Icon.bundled("plus", 16);
        assertNotEquals(plus, Icon.bundled("plus", 24));
        assertNotEquals(plus, Icon.bundled("minus", 16));
        // One name, two shapes: `of` takes any name, so the outline decides.
        assertNotEquals(Icon.of("mark", "M0 0 L24 24", 16), Icon.of("mark", "M0 24 L24 0", 16));
        assertEquals(Icon.of("mark", "M0 0 L24 24", 16), Icon.of("mark", "M0 0 L24 24", 16));
    }
}
