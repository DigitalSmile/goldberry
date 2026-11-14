package dev.goldberry.motion.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.value.Affine;

/// The axis-aligned maps a layout move is made of.
@DisplayName("a stretch")
class StretchTest {

    private static final Place FROM = new Place(10, 20, 40, 10);
    private static final Place TO = new Place(100, 50, 80, 30);

    @Test
    @DisplayName("carries one rectangle onto another")
    void onto() {
        assertEquals(TO, Stretch.onto(FROM, TO).map(FROM));
    }

    @Test
    @DisplayName("composes in order, and undoes itself")
    void composeAndInvert() {
        var a = Stretch.onto(FROM, TO);
        var b = new Stretch(2, 0.5, -3, 7);

        var place = new Place(1, 2, 3, 4);
        assertEquals(b.map(a.map(place)), a.then(b).map(place));
        assertTrue(a.then(a.inverse()).isIdentity());
        assertTrue(a.inverse().then(a).isIdentity());
    }

    @Test
    @DisplayName("counts rounding and a negative zero as no move")
    void identity() {
        assertTrue(new Stretch(1, 1, -0.0, -0.0).isIdentity());
        assertTrue(Stretch.onto(FROM, FROM).then(Stretch.IDENTITY.inverse()).isIdentity());
    }

    @Test
    @DisplayName("moves an empty box rather than dividing by its size")
    void empty() {
        var empty = new Place(0, 0, 0, 10);

        assertEquals(
                new Place(5, 0, 0, 10),
                Stretch.onto(empty, new Place(5, 0, 20, 10)).map(empty));
    }

    @Test
    @DisplayName("is the same map as a matrix")
    void affine() {
        var stretch = new Stretch(2, 3, 4, 5);

        assertEquals(new Affine(2, 0, 0, 3, 4, 5), stretch.affine());
    }
}
