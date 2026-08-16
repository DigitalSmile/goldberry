package io.github.digitalsmile.goldberry.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SvgPathTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    @ParameterizedTest
    @CsvSource({
        // data, expected vertex count
        "'M0 0',                     1", // one move
        "'M0 0L10 10',               2",
        "'M0 0 10 10',               2", // a repeated M is an L
        "'M0 0 10 10 20 20',         3",
        "'M0 0L10 10L20 0Z',         4", // Z contributes a vertex of its own
        "'M0 0H10',                  2",
        "'M0 0V10',                  2",
        "'M0 0C1 1 2 2 3 3',         4", // a cubic is three vertices plus the move
        "'M0 0Q1 1 2 2',             3",
        "'M0 0C1 1 2 2 3 3S4 4 5 5', 7",
        "'M0 0Q1 1 2 2T4 4',         5",
    })
    @DisplayName("each command produces the geometry it should")
    void commandsProduceGeometry(String data, long vertices) {
        try (var path = BlendPath.create()) {
            SvgPath.appendTo(path, data);

            assertEquals(vertices, path.vertexCount(), () -> "for \"" + data + "\"");
        }
    }

    @Test
    @DisplayName("relative commands are relative to the current point")
    void relativeCommandsTrackTheCurrentPoint() {
        // The same outline written absolutely and relatively must produce the
        // same path. Comparing vertex counts alone would pass for a parser that
        // ignored the distinction entirely, so the two are compared by drawn
        // ink in IconPaintTest -- here the assertion is that both parse and
        // produce the same shape of command stream.
        try (var absolute = BlendPath.create(); var relative = BlendPath.create()) {
            SvgPath.appendTo(absolute, "M10 10L20 10L20 20Z");
            SvgPath.appendTo(relative, "m10 10l10 0l0 10z");

            assertEquals(absolute.vertexCount(), relative.vertexCount());
        }
    }

    @Test
    @DisplayName("a command after Z re-opens the figure with an implicit move")
    void closeIsNotTheEndOfThePath() {
        // Six, not five: the move, two lines, the close, then the *implicit*
        // move back to the sub-path start, then the line. Blend2D rejects a
        // line-to straight after a close -- there is no figure to extend -- so
        // that implicit move is issued rather than merely accounted for.
        //
        // Whether it went back to the right point cannot be seen in a count.
        // `IconPaintTest.penReturnsToTheSubPathStartAfterClose` asserts that by
        // looking at where the ink landed.
        try (var path = BlendPath.create()) {
            SvgPath.appendTo(path, "M10 10L20 10L20 20Zl5 0");

            assertEquals(6, path.vertexCount());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "M1.5.5L2.5.5",       // numbers that run together at the decimal point
        "M1-2L3-4",           // a minus sign is its own separator
        "M0 0L1e1 1E1",       // exponents
        "M0,0 L1,1",          // commas
        "M 0 0 L 1 1 ",       // spaces everywhere, and a trailing one
        "M0 0A5 5 0 011 1",   // packed arc flags: large-arc=0, sweep=1, x=1
        "M0 0A5 5 0 1 1 1 1", // and the same spelled out
    })
    @DisplayName("the number grammar is SVG's, not whitespace-splitting")
    void parsesSvgNumberGrammar(String data) {
        try (var path = BlendPath.create()) {
            SvgPath.appendTo(path, data);

            assertTrue(path.vertexCount() >= 2, () -> "\"" + data + "\" produced nothing");
        }
    }

    @Test
    @DisplayName("packed arc flags mean what the specification says, not what they look like")
    void packedArcFlagsAreReadAsFlags() {
        // `011 1` is large-arc=0, sweep=1, x=1, y=1 -- three tokens hiding in
        // one, which is legal precisely because the flags are single
        // characters. The spelled-out form below is the SAME arc, and the
        // large-arc=1 form is a different one; asserting against both is what
        // distinguishes "parsed the flags" from "happened to parse something".
        try (var packed = BlendPath.create();
                var same = BlendPath.create();
                var different = BlendPath.create()) {

            SvgPath.appendTo(packed, "M0 0A5 5 0 011 1");
            SvgPath.appendTo(same, "M0 0A5 5 0 0 1 1 1");
            SvgPath.appendTo(different, "M0 0A5 5 0 1 1 1 1");

            assertEquals(same.vertexCount(), packed.vertexCount());
            assertTrue(different.vertexCount() > packed.vertexCount(),
                    () -> "the large arc should need more vertices than the small one: "
                            + different.vertexCount() + " vs " + packed.vertexCount());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "L10 10",        // a command before any move-to
        "M0 0L",         // a command with no arguments
        "M0 0X10 10",    // a letter that is not a command
        "M0 0A5 5 0 2 1 1 1", // an arc flag that is neither 0 nor 1
        "M0 0L10",       // an odd coordinate
        "M0 0Z 5",       // arguments after Z
        "10 10",         // numbers with no command at all
    })
    @DisplayName("malformed data is refused rather than half-drawn")
    void malformedDataIsRefused(String data) {
        try (var path = BlendPath.create()) {
            assertThrows(
                    IllegalArgumentException.class, () -> SvgPath.appendTo(path, data),
                    () -> "\"" + data + "\" should not have parsed");
        }
    }

    @Test
    @DisplayName("the failure message says where, and quotes only the region")
    void failureMessageIsLocated() {
        var data = "M0 0" + "L1 1".repeat(30) + "X";
        try (var path = BlendPath.create()) {
            var thrown = assertThrows(
                    IllegalArgumentException.class, () -> SvgPath.appendTo(path, data));

            assertTrue(thrown.getMessage().contains("at index"), thrown::getMessage);
            // The excerpt is bounded, so a long icon does not print in full.
            assertTrue(thrown.getMessage().length() < data.length(), thrown::getMessage);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    @DisplayName("a scale that would collapse or mirror the icon is refused")
    void refusesAnUnusableScale(double scale) {
        try (var path = BlendPath.create()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SvgPath.appendTo(path, "M0 0L1 1", scale));
        }
    }

    @Test
    @DisplayName("every bundled icon parses")
    void everyBundledIconParses() {
        // The real assertion of this whole class. 1544 icons compiled by
        // :assets from a checksummed archive: if any one of them uses a command
        // or a number form the reader does not handle, this is where it says so
        // -- rather than one checkbox in a showcase being mysteriously empty.
        var names = io.github.digitalsmile.goldberry.assets.BundledAssets.iconNames();
        assertTrue(names.size() > 1_000, () -> "only " + names.size() + " icons in the table");

        for (var name : names) {
            var data = io.github.digitalsmile.goldberry.assets.BundledAssets.icon(name).orElseThrow();
            try (var path = BlendPath.create()) {
                try {
                    SvgPath.appendTo(path, data);
                } catch (RuntimeException e) {
                    throw new AssertionError("icon \"" + name + "\" did not parse: " + data, e);
                }
                assertTrue(path.vertexCount() > 0, () -> "icon \"" + name + "\" produced no geometry");
            }
        }
    }
}
