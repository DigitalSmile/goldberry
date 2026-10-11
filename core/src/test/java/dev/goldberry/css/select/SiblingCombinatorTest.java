package dev.goldberry.css.select;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.TestElement;
import dev.goldberry.css.parse.CssParser;

/// `+` and `~`: an element's match read off the siblings before it.
class SiblingCombinatorTest {

    private static Selector selector(String css) {
        return CssParser.parse(css + " { color: red }").getFirst().selectors().getFirst();
    }

    /// `list( a b.on c:hover b a )` under a `card`, each child holding a
    /// `text`.
    private static List<TestElement> children() {
        var card = element("card");
        var list = element("list");
        card.with(list);
        var kids = List.of(element("a"), element("b.on"), element("c:hover"), element("b"), element("a"));
        for (var kid : kids) {
            kid.with(element("text"));
        }
        list.with(kids.toArray(TestElement[]::new));
        return kids;
    }

    /// The 1-based positions among the five children that `css` matches.
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
                "a + b              | 2",
                "b + b              | ''",
                "b ~ b              | 4",
                "a ~ b              | 2,4",
                "a ~ a              | 5",
                ".on + c            | 3",
                "c:hover + b        | 4",
                ":hover ~ *         | 4,5",
                "a + b ~ a          | 5",
                "a + b + c          | 3",
                "a ~ c + b          | 4",
                "list > a + b       | 2",
                "card a ~ b         | 2,4",
                "other a ~ b        | ''",
                ":is(a + b)         | 2",
                ":not(* + *)        | 1",
                ":nth-child(2) ~ b  | 4",
            })
    @DisplayName("reads the siblings before an element, backtracking through ~")
    void matches(String css, String expected) {
        var want = expected.isEmpty()
                ? List.<Integer>of()
                : Arrays.stream(expected.split(",")).map(Integer::valueOf).toList();
        assertEquals(want, matching(css), css);
    }

    @Test
    @DisplayName("a descendant of a sibling is reached through its parent")
    void descendantAfterSibling() {
        var kids = children();
        var selector = selector("a + b text");
        assertEquals(
                List.of(false, true, false, false, false),
                kids.stream()
                        .map(kid -> SelectorMatcher.matches(selector, kid.descend(1)))
                        .toList());
    }

    @Test
    @DisplayName("an element without siblings is placed after nothing")
    void alone() {
        assertFalse(SelectorMatcher.matches(selector("a + b"), element("b")));
        assertFalse(SelectorMatcher.matches(selector("a ~ b"), element("b")));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a + b          | 0,0,2",
                "a ~ .b         | 0,1,1",
                "a + b ~ c > d  | 0,0,4",
            })
    @DisplayName("each compound counts as the other combinators count it")
    void specificity(String css, String expected) {
        var packed = selector(css).specificity();
        assertEquals(expected, (packed >>> 20) + "," + ((packed >>> 10) & 1023) + "," + (packed & 1023), css);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {"a+b | a + b", "a~b.c | a ~ b.c", "a > b + c ~ d e | a > b + c ~ d e"})
    @DisplayName("prints as it was written, spaced")
    void printed(String css, String expected) {
        assertEquals(expected, selector(css).toString());
    }
}
