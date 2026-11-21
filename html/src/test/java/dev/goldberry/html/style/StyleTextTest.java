package dev.goldberry.html.style;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A mail's CSS, cut into rules, selectors and declarations.
@DisplayName("StyleText")
class StyleTextTest {

    @Test
    @DisplayName("reads each rule and steps over every at-rule, block and all")
    void rules() {
        var rules = StyleText.rules("""
                @import url(https://tracker.example/x.css);
                p { color: red }
                @media (max-width: 600px) { p { color: blue } .x { padding: 0 } }
                @font-face { font-family: X; src: url(x.woff) }
                td.a, th { padding: 4px }
                """);

        assertEquals(
                List.of(new StyleText.Rule("p", " color: red "), new StyleText.Rule("td.a, th", " padding: 4px ")),
                rules);
    }

    @Test
    @DisplayName("ignores comments and the HTML comment old mail wraps a style block in")
    void comments() {
        var rules = StyleText.rules("<!-- /* p { color: red } */ .a { color: blue } -->");

        assertEquals(List.of(new StyleText.Rule(".a", " color: blue ")), rules);
    }

    @Test
    @DisplayName("keeps a brace inside a string where it is, and ends at a rule nobody closed")
    void strings() {
        var rules = StyleText.rules(".a { content: \"}\" } .b { color: red");

        assertEquals(2, rules.size());
        assertEquals(" content: \"}\" ", rules.getFirst().body());
        assertEquals(" color: red", rules.get(1).body());
    }

    @Test
    @DisplayName("splits declarations at semicolons and selectors at commas, outside brackets")
    void splits() {
        assertEquals(
                List.of("color: red", "font: 12px \"a;b\"", "margin: calc(1px + 2px)"),
                StyleText.declarations("color: red;; font: 12px \"a;b\" ; margin: calc(1px + 2px);"));
        assertEquals(List.of("a", "p:is(.b, .c)", "td"), StyleText.selectors("a, p:is(.b, .c) ,td"));
    }
}
