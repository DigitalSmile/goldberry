package dev.goldberry.css.select;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.TestElement;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;

/// `::placeholder` and `::selection`: a selector ending in one matches the
/// part below the element that stands for it.
class PseudoElementTest {

    private static Selector selector(String css) {
        return CssParser.parse(css + " { color: red }").getFirst().selectors().getFirst();
    }

    /// `form > field:hover > [ text-selection(::selection),
    /// text-value.placeholder(::placeholder) ]`, and the value.
    private static TestElement value() {
        var value = element("text-value.placeholder").standingFor(PseudoElement.PLACEHOLDER);
        element("form")
                .with(element("field:hover")
                        .with(element("text-selection").standingFor(PseudoElement.SELECTION), value));
        return value;
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "field::placeholder              | true",
                "field:hover::placeholder        | true",
                "field:focus::placeholder        | false",
                "form > field::placeholder       | true",
                "other::placeholder              | false",
                "::placeholder                   | true",
                "*::placeholder                  | true",
                "form ::placeholder              | true",
                "field::selection                | false",
                "text-value.placeholder          | true",
                "field text-value::placeholder   | false",
            })
    @DisplayName("matches the part below the element before the ::, which is not the part itself")
    void matches(String css, boolean expected) {
        assertEquals(expected, SelectorMatcher.matches(selector(css), value()), css);
    }

    @Test
    @DisplayName("a part that stands for nothing is not one")
    void standsForNothing() {
        var plain = element("text-value");
        element("field").with(plain);
        assertFalse(SelectorMatcher.matches(selector("field::placeholder"), plain));
        assertTrue(SelectorMatcher.matches(selector("field text-value"), plain));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "::selection                | 0,0,1",
                "text-input::placeholder    | 0,0,2",
                "field:focus::placeholder   | 0,1,2",
            })
    @DisplayName("counts as a type, as CSS counts a pseudo-element")
    void specificity(String css, String expected) {
        var packed = selector(css).specificity();
        assertEquals(expected, (packed >>> 20) + "," + ((packed >>> 10) & 1023) + "," + (packed & 1023), css);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"text-input::placeholder", "::selection", "a > b:hover::placeholder", "a ::selection"})
    @DisplayName("prints as it was written")
    void printed(String css) {
        assertEquals(css, selector(css).toString());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "a::before { color: red }",
                "a::placeholder:hover { color: red }",
                "a::placeholder b { color: red }",
                "a::placeholder > b { color: red }",
                ":is(::placeholder) { color: red }",
                "a:has(::selection) { color: red }",
                "a::-webkit-input-placeholder { color: red }",
            })
    @DisplayName("refuses what follows one, one inside a pseudo-class's argument, and the others")
    void refused(String css) {
        var thrown = assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet(css, ParseMode.STRICT, "t"));
        assertTrue(thrown.isUnsupportedFeature(), thrown::getMessage);
        assertEquals(
                1,
                CssParser.parseSheet(css + " b { color: blue }", ParseMode.LENIENT, "t")
                        .rules()
                        .size(),
                css);
    }

    @Test
    @DisplayName("a nested rule writes one after &")
    void nested() {
        var rule =
                CssParser.parse("text-input { &::placeholder { color: red } }").getLast();
        assertEquals("text-input::placeholder", rule.selectors().getFirst().toString());
    }
}
