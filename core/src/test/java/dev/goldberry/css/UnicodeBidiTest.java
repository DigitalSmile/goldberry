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
import dev.goldberry.layout.Direction;
import dev.goldberry.text.bidi.Directionality;
import dev.goldberry.text.bidi.UnicodeBidi;
import dev.goldberry.text.flow.TextAlign;

/// `unicode-bidi`: where a box's text takes its base direction from, and what
/// that makes of `text-align: start`.
class UnicodeBidiTest {

    private static Map<String, List<Token>> declarations(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        var rule = CssParser.parseSheet("probe { " + css + " }", ParseMode.LENIENT, "UnicodeBidiTest")
                .rules()
                .getFirst();
        for (var declaration : rule.declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return map;
    }

    private static ComputedStyle style(String css) {
        return ComputedStyle.of(declarations(css), CssLength.Context.DEFAULT, null);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "unicode-bidi: normal, NORMAL",
        "unicode-bidi: embed, EMBED",
        "unicode-bidi: isolate, ISOLATE",
        "unicode-bidi: bidi-override, BIDI_OVERRIDE",
        "unicode-bidi: isolate-override, ISOLATE_OVERRIDE",
        "unicode-bidi: plaintext, PLAINTEXT"
    })
    @DisplayName("reads CSS's six keywords")
    void applied(String css, UnicodeBidi expected) {
        assertEquals(expected, style(css).presence().unicodeBidi());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"unicode-bidi: auto", "unicode-bidi: plaintext isolate", "unicode-bidi: 1"})
    @DisplayName("drops anything else, keeping normal")
    void dropped(String css) {
        assertEquals(UnicodeBidi.NORMAL, style(css).presence().unicodeBidi());
    }

    @Test
    @DisplayName("does not inherit, while direction does")
    void notInherited() {
        var parent = style("direction: rtl; unicode-bidi: plaintext");
        var child = ComputedStyle.of(declarations(""), CssLength.Context.DEFAULT, parent);

        assertEquals(UnicodeBidi.NORMAL, child.presence().unicodeBidi());
        assertEquals(Direction.RTL, child.presence().direction());
        assertEquals(Directionality.RTL, child.directionality());
    }

    @Test
    @DisplayName("gives the box's text its directionality")
    void directionality() {
        assertEquals(Directionality.LTR, style("").directionality());
        assertEquals(Directionality.RTL, style("direction: rtl").directionality());
        assertEquals(
                new Directionality(Direction.RTL, Directionality.Source.FIRST_STRONG),
                style("direction: rtl; unicode-bidi: plaintext").directionality());
        assertEquals(
                new Directionality(Direction.LTR, Directionality.Source.OVERRIDE),
                style("unicode-bidi: bidi-override").directionality());
    }

    @Test
    @DisplayName("under plaintext, start and end are left for each paragraph to place")
    void plaintextLeavesStart() {
        assertEquals(TextAlign.RIGHT, style("direction: rtl; text-align: start").lineAlign());
        assertEquals(
                TextAlign.START,
                style("unicode-bidi: plaintext; text-align: start").lineAlign());
        assertEquals(
                TextAlign.END,
                style("direction: rtl; unicode-bidi: plaintext; text-align: end")
                        .lineAlign());
        assertEquals(
                TextAlign.CENTER,
                style("unicode-bidi: plaintext; text-align: center").lineAlign());
        assertEquals(
                TextAlign.JUSTIFY,
                style("unicode-bidi: plaintext; text-align: justify").lineAlign());
        assertEquals(
                TextAlign.START,
                style("unicode-bidi: plaintext; text-align: justify; text-justify: none")
                        .lineAlign(),
                "justify with nothing to justify by is start, left for the paragraph to place");
        assertEquals(
                TextAlign.START, style("unicode-bidi: plaintext").textFlow().textAlign());
    }

    @Test
    @DisplayName("text-align's placing reads justify as start")
    void placing() {
        assertEquals(TextAlign.RIGHT, TextAlign.JUSTIFY.placing(Direction.RTL));
        assertEquals(TextAlign.START, TextAlign.JUSTIFY.placing(Direction.LTR));
        assertEquals(TextAlign.LEFT, TextAlign.END.placing(Direction.RTL));
        assertEquals(TextAlign.CENTER, TextAlign.CENTER.placing(Direction.RTL));
    }
}
