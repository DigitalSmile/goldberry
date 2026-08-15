package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The SVG basic-shape conversions, checked against coordinates a reader can
/// verify by hand.
///
/// These are the kind of function that is easy to write, easy to get subtly
/// wrong, and impossible to notice being wrong: a rectangle whose last corner is
/// off by a radius still renders as a rectangle. Hence numbers rather than
/// "it produced something".
class SvgShapesTest {

    @Test
    @DisplayName("a line is a moveto and a lineto")
    void lineIsTwoCommands() {
        assertEquals("M1 2L3 4", SvgShapes.line(1, 2, 3, 4));
    }

    @Test
    @DisplayName("a polyline is open and a polygon is closed")
    void polylinesAndPolygonsDifferOnlyInClosure() {
        assertEquals("M1 2L3 4L5 6", SvgShapes.polyline("1,2 3,4 5,6"));
        assertEquals("M1 2L3 4L5 6Z", SvgShapes.polygon("1,2 3,4 5,6"));
    }

    @Test
    @DisplayName("point lists accept any mix of commas and whitespace")
    void pointSeparatorsAreFlexible() {
        // All three spellings appear in real icon sets, and SVG permits all of
        // them. A parser that split on commas alone would silently produce half
        // an icon.
        var expected = "M1 2L3 4";
        assertEquals(expected, SvgShapes.polyline("1,2 3,4"));
        assertEquals(expected, SvgShapes.polyline("1 2 3 4"));
        assertEquals(expected, SvgShapes.polyline("1 , 2\n3,4"));
    }

    @Test
    @DisplayName("a trailing odd coordinate is dropped, as the spec says")
    void oddPointCountsDropTheRemainder() {
        assertEquals("M1 2", SvgShapes.polyline("1,2,3"));
        assertEquals(1, SvgShapes.points("1,2,3").size());
    }

    @Test
    @DisplayName("a circle is two half-arcs, because one arc would draw nothing")
    void circlesAreTwoArcs() {
        // An SVG arc whose endpoints coincide is a no-op, so a circle drawn as a
        // single arc from a point back to itself renders as nothing at all.
        var path = SvgShapes.circle(12, 12, 10);

        assertEquals("M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12Z", path);
        assertEquals(2, path.chars().filter(c -> c == 'A').count(), "two arcs, not one");
    }

    @Test
    @DisplayName("an ellipse uses both radii")
    void ellipsesUseBothRadii() {
        assertEquals("M2 12A10 6 0 1 0 22 12A10 6 0 1 0 2 12Z", SvgShapes.ellipse(12, 12, 10, 6));
    }

    @Test
    @DisplayName("a zero radius disables the shape rather than drawing a dot")
    void degenerateEllipsesDrawNothing() {
        assertEquals("", SvgShapes.circle(12, 12, 0));
        assertEquals("", SvgShapes.ellipse(12, 12, 10, 0));
    }

    @Test
    @DisplayName("a square-cornered rect closes back on itself")
    void plainRectsAreFourEdges() {
        assertEquals("M3 4H13V24H3Z", SvgShapes.rect(3, 4, 10, 20, -1, -1));
        // An explicit zero radius is a square corner too.
        assertEquals("M0 0H2V2H0Z", SvgShapes.rect(0, 0, 2, 2, 0, 0));
    }

    @Test
    @DisplayName("a rounded rect has four arcs and returns to where it began")
    void roundedRectsCloseCleanly() {
        var path = SvgShapes.rect(3, 3, 18, 18, 2, 2);

        assertEquals(4, path.chars().filter(c -> c == 'A').count(), "one arc per corner");
        assertTrue(path.startsWith("M5 3"), path);
        assertTrue(path.endsWith("A2 2 0 0 1 5 3Z"), () -> "must return to the start: " + path);
    }

    @Test
    @DisplayName("an omitted radius mirrors the one that was given")
    void omittedRadiusMirrorsTheOther() {
        // SVG's rule. Treating "not given" as zero instead would square off
        // every corner of every rounded rectangle in the set.
        var fromRx = SvgShapes.rect(0, 0, 10, 10, 2, -1);
        var fromRy = SvgShapes.rect(0, 0, 10, 10, -1, 2);
        var both = SvgShapes.rect(0, 0, 10, 10, 2, 2);

        assertEquals(both, fromRx);
        assertEquals(both, fromRy);
    }

    @Test
    @DisplayName("a radius larger than the rect is clamped, not an error")
    void oversizedRadiiAreClamped() {
        // Legal input: it produces a stadium. Half of each side is the limit.
        var clamped = SvgShapes.rect(0, 0, 10, 20, 99, 99);
        var exact = SvgShapes.rect(0, 0, 10, 20, 5, 10);

        assertEquals(exact, clamped);
    }

    @Test
    @DisplayName("an empty rect draws nothing")
    void emptyRectsDrawNothing() {
        assertEquals("", SvgShapes.rect(0, 0, 0, 10, -1, -1));
        assertEquals("", SvgShapes.rect(0, 0, 10, -1, -1, -1));
    }

    @Test
    @DisplayName("whole numbers lose their decimal point")
    void coordinatesAreFormattedShort() {
        // 1544 icons of mostly-integer coordinates; ".0" on every one of them is
        // a measurable fraction of the table.
        assertEquals("12", SvgShapes.n(12.0));
        assertEquals("-3", SvgShapes.n(-3.0));
        assertEquals("0.5", SvgShapes.n(0.5));
    }

    @Test
    @DisplayName("a fractional coordinate never uses a decimal comma")
    void formattingIsLocaleIndependent() {
        // A decimal comma would turn one coordinate into two, and the icons
        // would be subtly wrong only on machines in certain locales.
        var previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertEquals("0.5", SvgShapes.n(0.5));
            assertEquals("M0.5 1L2 2.5", SvgShapes.line(0.5, 1, 2, 2.5));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
