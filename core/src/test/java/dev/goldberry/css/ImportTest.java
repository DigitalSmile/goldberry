package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.media.MediaCondition;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.TokenType;

/// `@import`: read by the parser, resolved beside a sheet loaded as a
/// resource.
class ImportTest {

    private static List<String> keys(Stylesheet sheet) {
        return sheet.rules().stream()
                .map(rule -> rule.selectors().getFirst().toString())
                .toList();
    }

    @Test
    @DisplayName("imported rules come first, in import order, nested imports before their importer")
    void order() {
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, ImportTest.class, "imports/main.css");
        assertEquals(List.of("base", ":root", "tokens", "narrow", "main"), keys(sheet));
        for (var i = 0; i < sheet.rules().size(); i++) {
            assertEquals(i, sheet.rules().get(i).order(), "renumbered in place");
        }
    }

    @Test
    @DisplayName("an import's media list applies to its rules, and its urls are beside the imported sheet")
    void mediaAndUrls() {
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, ImportTest.class, "imports/main.css");
        var narrow = sheet.rules().get(3);
        assertNotEquals(MediaCondition.ALWAYS, narrow.media());
        assertEquals(MediaCondition.ALWAYS, sheet.rules().get(4).media());
        var grain = sheet.rules().get(2).declarations().getFirst().value().stream()
                .filter(t -> t.is(TokenType.STRING))
                .findFirst()
                .orElseThrow();
        assertEquals("classpath:/dev/goldberry/css/imports/parts/grain.png", grain.text());
    }

    @Test
    @DisplayName("a missing sheet is dropped with a warning in a lenient sheet, and refused in a strict one")
    void missing() {
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, ImportTest.class, "imports/main.css");
        assertEquals(1, sheet.dropped().size());
        assertEquals("@import missing.css", sheet.dropped().getFirst().text());
        var thrown = assertThrows(
                CssSyntaxException.class,
                () -> Stylesheet.resource(
                        CascadeLayer.APPLICATION, ImportTest.class, "imports/main.css", ParseMode.STRICT));
        assertTrue(thrown.isUnsupportedFeature());
    }

    @Test
    @DisplayName("a cycle is refused where it closes, and both sheets' own rules are kept")
    void cycle() {
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, ImportTest.class, "imports/loop-a.css");
        assertEquals(List.of("b", "a"), keys(sheet));
        assertEquals(1, sheet.dropped().size());
    }

    @Test
    @DisplayName("text has nothing to resolve an import beside, so the import is reported and skipped")
    void fromText() {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "@import \"x.css\"; a { color: red }");
        assertEquals(List.of("a"), keys(sheet));
        assertEquals(1, sheet.dropped().size());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"@import x.css;", "@import \"x.css\""})
    @DisplayName("an import unquoted outside url(), or unterminated, is malformed")
    void malformed(String css) {
        assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet(css, ParseMode.LENIENT, "t"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "a { color: red } @import \"x.css\"; b { color: red }",
                "@media (min-width: 1px) { @import \"x.css\"; } b { color: red }",
            })
    @DisplayName("an import after a rule or inside a block is dropped, as CSS ignores it, and refused when strict")
    void misplaced(String css) {
        var parsed = CssParser.parseSheet(css, ParseMode.LENIENT, "t");
        assertEquals(List.of(), parsed.imports());
        assertEquals(1, parsed.dropped().size());
        assertTrue(assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet(css, ParseMode.STRICT, "t"))
                .isUnsupportedFeature());
    }

    @Test
    @DisplayName("reads url() unquoted, supports() and a media list")
    void prelude() {
        var parsed = CssParser.parseSheet(
                "@import url(a/b.css) supports(display: flex) screen and (min-width: 2px);"
                        + " @import \"c.css\" supports(display: grid);",
                ParseMode.STRICT,
                "t");
        assertEquals("a/b.css", parsed.imports().get(0).href());
        assertNotEquals(MediaCondition.ALWAYS, parsed.imports().get(0).media());
        assertEquals(MediaCondition.NEVER, parsed.imports().get(1).media(), "a condition that does not hold");
        var layered =
                CssParser.parseSheet("@import \"x.css\" layer(a); @import \"y.css\" layer;", ParseMode.STRICT, "t");
        assertEquals("a", layered.imports().get(0).layer());
        assertTrue(layered.layers().contains("a"));
        assertEquals(2, layered.layers().size(), "an unnamed layer of its own for the second");
    }
}
