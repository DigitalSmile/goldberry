package dev.goldberry.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ViewBox")
class ViewBoxTest {

    @Test
    @DisplayName("reads four numbers apart by white space, commas or both")
    void parses() {
        assertEquals(Optional.of(new ViewBox(0, 0, 24, 24)), ViewBox.parse("0 0 24 24"));
        assertEquals(Optional.of(new ViewBox(-5, 1.5f, 10, 2e3f)), ViewBox.parse("  -5,1.5 ,\t10\n2e3 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0 0 24", "0 0 24 24 1", "0 0 0 24", "0 0 24 -1", "a 0 24 24", "NaN 0 1 1"})
    @DisplayName("is empty for anything that is not a view box with a size")
    void refuses(String value) {
        assertEquals(Optional.empty(), ViewBox.parse(value));
    }

    @Test
    @DisplayName("cannot be made without a width and a height above zero")
    void validates() {
        assertThrows(IllegalArgumentException.class, () -> new ViewBox(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ViewBox(0, 0, 1, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ViewBox(Float.POSITIVE_INFINITY, 0, 1, 1));
    }
}
