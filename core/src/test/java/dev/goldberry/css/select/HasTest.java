package dev.goldberry.css.select;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.TestElement;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;

/// `:has()`: an element's match read off what is below it or after it.
class HasTest {

    private static Selector selector(String css) {
        return CssParser.parse(css + " { color: red }").getFirst().selectors().getFirst();
    }

    /// Under a `page`:
    ///
    /// ```
    /// card#one   ( text, field ( input:checked ) )
    /// card#two   ( text )
    /// note
    /// card#three ( list ( item.x ) )
    /// ```
    private static List<TestElement> children() {
        var page = element("page");
        var kids = List.of(
                element("card#one").with(element("text"), element("field").with(element("input:checked"))),
                element("card#two").with(element("text")),
                element("note"),
                element("card#three").with(element("list").with(element("item.x"))));
        page.with(kids.toArray(TestElement[]::new));
        return kids;
    }

    /// The 1-based positions among the page's four children that `css`
    /// matches.
    private static List<Integer> matching(String css) {
        var selector = selector(css);
        var kids = children();
        var matched = new ArrayList<Integer>();
        for (var i = 0; i < kids.size(); i++) {
            if (SelectorMatcher.matches(selector, kids.get(i))) {
                matched.add(i + 1);
            }
        }
        return matched;
    }

    @ParameterizedTest(name = "{0} matches {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "card:has(input)                    | 1",
                "card:has(> input)                  | ''",
                "card:has(> field > input:checked)  | 1",
                "card:has(field input)              | 1",
                "card:has(item.x)                   | 4",
                "card:has(text, item)               | 1,2,4",
                "card:has(+ card)                   | 1",
                "*:has(+ note)                      | 2",
                "card:has(~ note)                   | 1,2",
                "card:has(~ card item)              | 1,2",
                "card:has(~ card > list)            | 1,2",
                "card:not(:has(input))              | 2,4",
                ":is(card:has(:checked), note)      | 1,3",
                "page > card:has(text)              | 1,2",
                "card:has(page)                     | ''",
                "card:has(card)                     | ''",
            })
    @DisplayName("searches below an element, or along and below its later siblings")
    void matches(String css, String expected) {
        var want = expected.isEmpty()
                ? List.<Integer>of()
                : Arrays.stream(expected.split(",")).map(Integer::valueOf).toList();
        assertEquals(want, matching(css), css);
    }

    @Test
    @DisplayName("a descendant reads an ancestor's :has() through a combinator")
    void inAnAncestorPosition() {
        var kids = children();
        var selector = selector("card:has(:checked) text");
        assertTrue(SelectorMatcher.matches(selector, kids.get(0).descend(1)));
        assertFalse(SelectorMatcher.matches(selector, kids.get(1).descend(1)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a:has(.x, #y)  | 1,0,1",
                ":has(> b c)    | 0,0,2",
                "a:has(+ .x)    | 0,1,1",
            })
    @DisplayName("is as specific as its most specific argument")
    void specificity(String css, String expected) {
        var packed = selector(css).specificity();
        assertEquals(expected, (packed >>> 20) + "," + ((packed >>> 10) & 1023) + "," + (packed & 1023), css);
    }

    @Test
    @DisplayName("prints each argument with the combinator it starts from")
    void printed() {
        assertEquals(
                "a:has(> b, + c, ~ d, e f)", selector("a:has(>b,+c, ~ d, e f)").toString());
    }

    @Test
    @DisplayName("an argument that does not say where it starts is refused")
    void anchored() {
        assertThrows(IllegalArgumentException.class, () -> new Functional.Has(List.of(selector("b"))));
    }

    @Test
    @DisplayName("an empty :has() is malformed, a :has() inside another outside the subset")
    void refused() {
        var empty = assertThrows(
                CssSyntaxException.class, () -> CssParser.parseSheet("a:has() { }", ParseMode.LENIENT, "t"));
        assertFalse(empty.isUnsupportedFeature());
        var nested = assertThrows(
                CssSyntaxException.class, () -> CssParser.parseSheet("a:has(b:has(c)) { }", ParseMode.STRICT, "t"));
        assertTrue(nested.isUnsupportedFeature(), nested::getMessage);
        assertEquals(
                1,
                CssParser.parseSheet("a:has(:is(b:has(c))) { } d:has(e) { }", ParseMode.LENIENT, "t")
                        .rules()
                        .size());
    }
}
