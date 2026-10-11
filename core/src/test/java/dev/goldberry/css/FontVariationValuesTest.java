package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.text.font.FontVariation;

/// `font-stretch` and `font-variation-settings`, read into the typography.
class FontVariationValuesTest {

    private static Map<String, List<Token>> declarations(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        var rule = CssParser.parseSheet("probe { " + css + " }", ParseMode.LENIENT, "FontVariationValuesTest")
                .rules()
                .getFirst();
        for (var declaration : rule.declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return map;
    }

    private static FontVariation variation(String css, ComputedStyle parent) {
        return ComputedStyle.of(declarations(css), CssLength.Context.DEFAULT, parent)
                .typography()
                .variation();
    }

    /// A parent whose values differ from the initial ones.
    private static final ComputedStyle PARENT = ComputedStyle.of(
            declarations("font-stretch: 112.5%; font-variation-settings: \"GRAD\" 50"), CssLength.Context.DEFAULT);

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "font-stretch: condensed, 75",
        "font-stretch: ultra-expanded, 200",
        "font-stretch: 87.5%, 87.5",
        "font-stretch: normal, 100",
        "font-width: semi-condensed, 87.5",
    })
    @DisplayName("font-stretch reads a keyword or a percentage")
    void stretch(String css, double expected) {
        assertEquals(expected, variation(css, PARENT).stretch());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"font-stretch: 40%", "font-stretch: 250%", "font-stretch: wide", "font-stretch: 90"})
    @DisplayName("drops a stretch outside 50% to 200% or outside the keywords, keeping the inherited one")
    void stretchDropped(String css) {
        assertEquals(112.5, variation(css, PARENT).stretch());
    }

    @Test
    @DisplayName("font-variation-settings reads tag and number pairs, the last of a tag winning")
    void settings() {
        assertEquals(
                List.of(new FontVariation.Setting("wdth", 90), new FontVariation.Setting("wght", 550)),
                variation("font-variation-settings: \"wght\" 500, \"wdth\" 90, \"wght\" 550", PARENT)
                        .settings());
        assertEquals(
                List.of(), variation("font-variation-settings: normal", PARENT).settings());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "font-variation-settings: wght 500",
                "font-variation-settings: \"wgt\" 500",
                "font-variation-settings: \"wght\"",
                "font-variation-settings: \"wght\" 5px",
                "font-variation-settings: \"wght\" 500,",
            })
    @DisplayName("drops a malformed list whole, keeping the inherited settings")
    void settingsDropped(String css) {
        assertEquals(
                List.of(new FontVariation.Setting("GRAD", 50)),
                variation(css, PARENT).settings());
    }

    @Test
    @DisplayName("both inherit")
    void inherited() {
        assertEquals(PARENT.typography().variation(), variation("", PARENT));
        assertEquals(FontVariation.NONE, ComputedStyle.INITIAL.typography().variation());
    }
}
