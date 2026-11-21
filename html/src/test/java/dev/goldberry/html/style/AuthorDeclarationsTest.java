package dev.goldberry.html.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.Declaration;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.html.Html;

/// What a mail may say about its own appearance, and what it may not.
@DisplayName("AuthorDeclarations")
class AuthorDeclarationsTest {

    private static String kept(String list) {
        return AuthorDeclarations.parse(list).stream()
                .map(AuthorDeclarationsTest::written)
                .collect(Collectors.joining("; "));
    }

    /// `property: value`, each token as CSS writes it.
    private static String written(Declaration declaration) {
        var value = new StringBuilder();
        for (var token : declaration.value()) {
            value.append(token.is(TokenType.WHITESPACE) ? " " : token.cssText());
        }
        return declaration.property() + ": " + value;
    }

    @Test
    @DisplayName("keeps colours, type, spacing, boxes, borders and corners")
    void keeps() {
        assertEquals(
                "color: #333; font-size: 14px; font-weight: bold; padding: 8px 16px; border: 1px solid #ccc;"
                        + " border-radius: 4px; text-align: center; max-width: 600px",
                kept("color:#333; font-size:14px; font-weight:bold; padding:8px 16px; border:1px solid #ccc;"
                        + " border-radius:4px; text-align:center; max-width:600px"));
    }

    @Test
    @DisplayName("turns a background that is a colour into background-color, and drops one that is not")
    void background() {
        assertEquals("background-color: #f4f4f4", kept("background: #f4f4f4"));
        assertEquals("", kept("background: #fff url(https://tracker.example/p.gif)"));
    }

    @Test
    @DisplayName("keeps display: none, which is how a mail hides its preview line, and no other display")
    void display() {
        assertEquals("display: none", kept("display: none"));
        assertEquals("", kept("display: flex"));
    }

    @Test
    @DisplayName("drops !important rather than the declaration")
    void important() {
        var declaration = AuthorDeclarations.parse("color: red !important").getFirst();

        assertFalse(declaration.important());
        assertEquals("color: red", written(declaration));
    }

    @ParameterizedTest(name = "refuses {0}")
    @ValueSource(
            strings = {
                "position: fixed",
                "position: absolute",
                "z-index: 9999",
                "transform: translateY(-100px)",
                "margin-top: -40px",
                "font-family: Comic Sans MS",
                "--gb-accent: red",
                "color: var(--gb-accent)",
                "background-color: url(x)",
                "color: expression(alert(1))",
                "color: env(safe-area-inset-top)",
                "color: attr(data-x)",
                "color: r\\65 d",
                "color: red} .shell { color: blue",
                "color: revert",
                "animation: spin 1s",
                "mso-line-height-rule: exactly",
                "color",
                ": red",
            })
    void refuses(String written) {
        assertNull(AuthorDeclarations.declaration(written));
    }

    @Test
    @DisplayName("reads bgcolor and a font's color, and refuses one that tries to say more")
    void presentational() {
        var cell = Html.parse("<table><tr><td bgcolor=\"ffcc00\">x</td></tr></table>")
                .find("td")
                .getFirst();
        var font = Html.parse("<font color=\"red\">x</font>").find("font").getFirst();
        var injected = Html.parse("<td bgcolor=\"red; position: fixed\">x</td>")
                .find("td")
                .getFirst();

        assertEquals(List.of("background-color: #ffcc00"), strings(AuthorDeclarations.presentational(cell)));
        assertEquals(List.of("color: red"), strings(AuthorDeclarations.presentational(font)));
        assertEquals(List.of(), strings(AuthorDeclarations.presentational(injected)));
    }

    private static List<String> strings(List<Declaration> declarations) {
        return declarations.stream().map(AuthorDeclarationsTest::written).toList();
    }
}
