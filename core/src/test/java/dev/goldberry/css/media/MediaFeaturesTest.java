package dev.goldberry.css.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.render.desktop.SystemTheme;

/// The range forms, and the features beyond size, theme and motion.
class MediaFeaturesTest {

    private static List<Token> tokens(String text) {
        return CssTokenizer.tokenize(text).stream()
                .filter(t -> !t.is(TokenType.EOF))
                .toList();
    }

    private static boolean matches(String query, MediaContext context) {
        var condition = MediaQueries.parse(tokens(query));
        assertEquals(java.util.Optional.empty(), MediaCondition.unsupported(condition), query);
        return condition.matches(context);
    }

    private static MediaContext window(double width, double height) {
        return MediaContext.UNKNOWN.size(width, height);
    }

    @ParameterizedTest(name = "{0} at {1}x{2} is {3}")
    @CsvSource(
            delimiter = '|',
            value = {
                "(400px <= width)                | 500  | 300 | true",
                "(400px <= width)                | 300  | 300 | false",
                "(400px <= width <= 800px)       | 600  | 300 | true",
                "(400px <= width <= 800px)       | 900  | 300 | false",
                "(800px > width >= 400px)        | 400  | 300 | true",
                "(800px > width >= 400px)        | 800  | 300 | false",
                "(200px < height)                | 500  | 300 | true",
                "(aspect-ratio: 16/9)            | 1600 | 900 | true",
                "(aspect-ratio: 16 / 9)          | 1601 | 900 | false",
                "(min-aspect-ratio: 1)           | 300  | 600 | false",
                "(4/3 < aspect-ratio)            | 1600 | 900 | true",
                "(aspect-ratio > 2)              | 1600 | 900 | false",
                "(width)                         | 10   | 10  | true",
            })
    @DisplayName("ranges and ratios")
    void ranges(String query, double width, double height, boolean expected) {
        assertEquals(expected, matches(query, window(width, height)), query);
    }

    @ParameterizedTest(name = "{0} at {1}dppx is {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "(min-resolution: 2dppx)        | 2    | true",
                "(min-resolution: 2dppx)        | 1    | false",
                "(resolution: 192dpi)           | 2    | true",
                "(resolution >= 1.5x)           | 1.25 | false",
                "(max-resolution: 37.8dpcm)     | 1    | true",
            })
    @DisplayName("resolution, from the window's scale")
    void resolution(String query, double dppx, boolean expected) {
        assertEquals(expected, matches(query, MediaContext.UNKNOWN.resolution(dppx)), query);
    }

    @Test
    @DisplayName("a resolution nobody has said holds no comparison")
    void unknownResolution() {
        assertEquals(false, matches("(min-resolution: 1dppx)", MediaContext.UNKNOWN));
        assertEquals(false, matches("(max-resolution: 9dppx)", MediaContext.UNKNOWN));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "(pointer: fine)          | true  | false",
                "(pointer: coarse)        | false | true",
                "(pointer: none)          | false | false",
                "(pointer)                | true  | true",
                "(any-pointer: coarse)    | false | true",
                "(any-pointer: fine)      | true  | false",
                "(hover: hover)           | true  | false",
                "(hover: none)            | false | true",
                "(hover)                  | true  | false",
                "(any-hover: hover)       | true  | false",
            })
    @DisplayName("pointer and hover, on a desktop and on a touch screen")
    void input(String query, boolean desktop, boolean touch) {
        assertEquals(desktop, matches(query, MediaContext.UNKNOWN), query + " on a desktop");
        assertEquals(touch, matches(query, MediaContext.UNKNOWN.input(MediaContext.Input.TOUCH)), query + " by touch");
    }

    @Test
    @DisplayName("the desktop's preferences")
    void preferences() {
        var none = MediaContext.UNKNOWN;
        var more = none.preferences(new MediaContext.Preferences(MediaContext.Contrast.MORE, false, false));
        var forced = none.preferences(new MediaContext.Preferences(MediaContext.Contrast.MORE, true, false));
        var clear = none.preferences(new MediaContext.Preferences(MediaContext.Contrast.NO_PREFERENCE, false, true));

        assertEquals(false, matches("(prefers-contrast)", none));
        assertEquals(true, matches("(prefers-contrast: no-preference)", none));
        assertEquals(true, matches("(prefers-contrast: more)", more));
        assertEquals(true, matches("(prefers-contrast)", more));
        assertEquals(false, matches("(prefers-contrast: custom)", more));
        assertEquals(true, matches("(prefers-contrast: custom)", forced));
        assertEquals(true, matches("(forced-colors: active)", forced));
        assertEquals(true, matches("(forced-colors: none)", more));
        assertEquals(true, matches("(prefers-reduced-transparency: reduce)", clear));
        assertEquals(true, matches("(prefers-reduced-transparency)", clear));
        assertEquals(false, matches("(prefers-reduced-transparency)", none));
    }

    @Test
    @DisplayName("color-gamut says sRGB, which is what everything is drawn to")
    void gamut() {
        assertEquals(true, matches("(color-gamut: srgb)", MediaContext.UNKNOWN));
        assertEquals(false, matches("(color-gamut: p3)", MediaContext.UNKNOWN));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "(400px < width > 800px)",
                "(400px = width = 800px)",
                "(2dppx <= width)",
                "(hover: maybe)",
                "(aspect-ratio: 0/1)",
                "(resolution: 2px)",
                "(400px <= depth)",
            })
    @DisplayName("refuses what is not a query it can answer")
    void unreadable(String query) {
        assertInstanceOf(MediaCondition.Unsupported.class, MediaQueries.parse(tokens(query)), query);
    }

    @Test
    @DisplayName("a new context field leaves the four the window had")
    void contextWithers() {
        var context = MediaContext.UNKNOWN
                .size(1, 2)
                .colorScheme(SystemTheme.DARK)
                .reducedMotion(true)
                .resolution(3);
        assertEquals(
                new MediaContext(
                        1, 2, SystemTheme.DARK, true, 3, MediaContext.Input.DESKTOP, MediaContext.Preferences.NONE),
                context);
    }
}
