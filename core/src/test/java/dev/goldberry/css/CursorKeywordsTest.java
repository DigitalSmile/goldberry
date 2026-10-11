package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.render.Cursor;

/// Every cursor keyword CSS has, read by name.
class CursorKeywordsTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "help, HELP",
        "none, NONE",
        "context-menu, CONTEXT_MENU",
        "cell, CELL",
        "vertical-text, VERTICAL_TEXT",
        "alias, ALIAS",
        "copy, COPY",
        "no-drop, NO_DROP",
        "all-scroll, ALL_SCROLL",
        "col-resize, COL_RESIZE",
        "row-resize, ROW_RESIZE",
        "n-resize, N_RESIZE",
        "se-resize, SE_RESIZE",
        "zoom-in, ZOOM_IN",
        "zoom-out, ZOOM_OUT",
        "grab, GRAB",
    })
    @DisplayName("each keyword is the shape of its name")
    void read(String keyword, Cursor cursor) {
        var value = CssTokenizer.tokenize(keyword).stream()
                .filter(t -> !t.is(TokenType.EOF))
                .toList();
        assertEquals(
                cursor,
                ComputedStyle.of(Map.of("cursor", value), CssLength.Context.DEFAULT)
                        .cursor());
    }
}
