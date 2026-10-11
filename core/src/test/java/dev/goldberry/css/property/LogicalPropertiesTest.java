package dev.goldberry.css.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;

/// The logical properties, which with one writing mode are the physical ones:
/// the inline axis across from the left, the block axis down from the top.
class LogicalPropertiesTest {

    private static Map<String, List<Token>> declarations(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        var rule = CssParser.parseSheet("probe { " + css + " }", ParseMode.STRICT, "LogicalPropertiesTest")
                .rules()
                .getFirst();
        for (var declaration : rule.declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return map;
    }

    private static ComputedStyle style(String css) {
        return ComputedStyle.INITIAL.applied(declarations(css), CssLength.Context.DEFAULT);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "margin-inline: 4px 8px | margin-left: 4px; margin-right: 8px",
                "margin-inline: 4px | margin-left: 4px; margin-right: 4px",
                "margin-block: 1px 2px | margin-top: 1px; margin-bottom: 2px",
                "padding-inline: 4px 8px | padding-left: 4px; padding-right: 8px",
                "padding-block: 3px | padding-top: 3px; padding-bottom: 3px",
                "padding-inline-start: 5px | padding-left: 5px",
                "padding-block-end: 5px | padding-bottom: 5px",
                "margin-inline-end: auto | margin-right: auto",
                "inset-inline: 1px 2px | left: 1px; right: 2px",
                "inset-block-start: 6px | top: 6px",
                "inline-size: 50% | width: 50%",
                "block-size: 10px | height: 10px",
                "min-inline-size: 10px | min-width: 10px",
                "max-block-size: 10px | max-height: 10px"
            })
    @DisplayName("each is the physical property it names, or the start and the end")
    void physical(String logical, String physical) {
        assertEquals(style(physical), style(logical));
    }

    @Test
    @DisplayName("a shorthand of two takes one value or two, and no more")
    void counts() {
        assertNull(LogicalProperties.physical(
                "margin-inline", declarations("a: 1px 2px 3px").get("a")));
        assertThrows(IllegalArgumentException.class, () -> LogicalProperties.physical("margin", List.of()));
        assertTrue(LogicalProperties.isLogical("inset-block-end"));
    }

    @Test
    @DisplayName("the table knows them, so inherit and initial mean the physical ones'")
    void table() {
        assertEquals(
                List.of("margin-left", "margin-right"),
                Properties.expand("margin-inline").stream().map(Longhand::name).toList());
        assertEquals(
                List.of("width"),
                Properties.expand("inline-size").stream().map(Longhand::name).toList());
    }
}
