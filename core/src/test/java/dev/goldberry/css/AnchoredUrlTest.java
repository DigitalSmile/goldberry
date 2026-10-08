package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;

/// A sheet read as a resource resolves its relative `url()`s beside itself.
class AnchoredUrlTest {

    private static List<String> strings(Stylesheet sheet, int rule) {
        return sheet.rules().get(rule).declarations().getFirst().value().stream()
                .filter(token -> token.is(TokenType.STRING))
                .map(Token::text)
                .toList();
    }

    @Test
    @DisplayName("a plain name is beside the sheet, / is the root, and a scheme is left alone")
    void anchored() {
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, AnchoredUrlTest.class, "anchored/sheet.css");

        assertEquals(List.of("classpath:/dev/goldberry/css/anchored/leather.png"), strings(sheet, 0));
        assertEquals(List.of("classpath:/dev/goldberry/css/ui/panel.png"), strings(sheet, 1));
        assertEquals(List.of("classpath:/abs/x.png", "classpath:/kept.png"), strings(sheet, 2));
    }

    @Test
    @DisplayName("a sheet from text has nothing to be beside, and is kept as written")
    void textIsUnchanged() {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, ".a { background-image: url(\"leather.png\") }");

        assertEquals(List.of("leather.png"), strings(sheet, 0));
    }
}
