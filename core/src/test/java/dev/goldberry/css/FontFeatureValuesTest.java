package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.text.font.FontFeatures;
import dev.goldberry.text.font.FontFeatures.Setting;

/// `font-feature-settings` and `font-variant-numeric`, read into the
/// typography.
class FontFeatureValuesTest {

    private static Map<String, List<Token>> declarations(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        var rule = CssParser.parseSheet("probe { " + css + " }", ParseMode.LENIENT, "FontFeatureValuesTest")
                .rules()
                .getFirst();
        for (var declaration : rule.declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return map;
    }

    private static FontFeatures features(String css, ComputedStyle parent) {
        return ComputedStyle.of(declarations(css), CssLength.Context.DEFAULT, parent)
                .typography()
                .features();
    }

    private static FontFeatures features(String css) {
        return ComputedStyle.of(declarations(css), CssLength.Context.DEFAULT)
                .typography()
                .features();
    }

    /// A parent whose features differ from the initial ones.
    private static final ComputedStyle PARENT = ComputedStyle.of(
            declarations("font-feature-settings: \"ss01\"; font-variant-numeric: oldstyle-nums"),
            CssLength.Context.DEFAULT);

    @Test
    @DisplayName("a tag alone is on, and on, off and an integer are read")
    void settings() {
        assertEquals(
                List.of(new Setting("tnum", 1), new Setting("liga", 0), new Setting("calt", 1), new Setting("salt", 3)),
                features("font-feature-settings: \"tnum\", \"liga\" off, \"calt\" on, \"salt\" 3")
                        .settings());
    }

    @Test
    @DisplayName("a tag written twice keeps its last value, at its last place")
    void lastWins() {
        assertEquals(
                List.of(new Setting("liga", 1), new Setting("tnum", 0)),
                features("font-feature-settings: \"tnum\", \"liga\", \"tnum\" 0")
                        .settings());
    }

    @Test
    @DisplayName("normal asks for nothing")
    void normal() {
        assertTrue(features("font-feature-settings: normal; font-variant-numeric: normal", PARENT)
                .isNone());
        assertTrue(FontFeatures.NONE.isNone());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "font-feature-settings: \"tnu\"",
                "font-feature-settings: tnum",
                "font-feature-settings: \"tnum\" -1",
                "font-feature-settings: \"tnum\" 1.5",
                "font-feature-settings: \"tnum\" 1 2",
                "font-feature-settings: \"tnum\",",
                "font-variant-numeric: tabular-nums proportional-nums",
                "font-variant-numeric: lining-nums oldstyle-nums",
                "font-variant-numeric: tabular",
                "font-variant-numeric: \"tnum\"",
            })
    @DisplayName("a value outside the grammar is dropped, keeping the inherited one")
    void dropped(String css) {
        assertEquals(PARENT.typography().features(), features(css, PARENT));
    }

    @Test
    @DisplayName("each numeric keyword turns on its feature, in the order written")
    void numeric() {
        assertEquals(
                List.of(
                        new Setting("zero", 1),
                        new Setting("tnum", 1),
                        new Setting("lnum", 1),
                        new Setting("frac", 1),
                        new Setting("ordn", 1)),
                features("font-variant-numeric: slashed-zero tabular-nums lining-nums diagonal-fractions ordinal")
                        .numeric());
        assertEquals(
                List.of(new Setting("onum", 1), new Setting("pnum", 1), new Setting("afrc", 1)),
                features("font-variant-numeric: oldstyle-nums proportional-nums stacked-fractions")
                        .numeric());
    }

    @Test
    @DisplayName("both inherit, separately")
    void inherit() {
        var child = features("font-variant-numeric: tabular-nums", PARENT);
        assertEquals(List.of(new Setting("ss01", 1)), child.settings());
        assertEquals(List.of(new Setting("tnum", 1)), child.numeric());
    }

    @Test
    @DisplayName("the settings have the last word on a tag both name")
    void settingsOverNumeric() {
        var both = features("font-variant-numeric: tabular-nums slashed-zero; font-feature-settings: \"tnum\" 0");
        assertEquals(List.of(new Setting("tnum", 0), new Setting("zero", 1)), both.resolved());
    }

    @Test
    @DisplayName("the font shorthand resets both")
    void shorthandResets() {
        assertTrue(features("font: 12px Inter", PARENT).isNone());
    }
}
