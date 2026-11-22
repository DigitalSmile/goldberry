package dev.goldberry.widget.attr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.kdl.KdlParser;

/// `spellcheck=` and `lang=`, HTML's two global attributes, on any node.
class SpellingAttributeTest {

    private static Attributes of(String markup) {
        return Attributes.of(KdlParser.parse(markup).getFirst());
    }

    @Test
    @DisplayName("markup reads both, and leaves each unset when it is not written")
    void markup() {
        var both = of("text-area spellcheck=#true lang=\"en-GB\"").spelling();
        assertEquals(Boolean.TRUE, both.check());
        assertEquals(Locale.UK, both.lang());

        var off = of("text-input spellcheck=#false").spelling();
        assertEquals(Boolean.FALSE, off.check());
        assertNull(off.lang());

        assertTrue(of("panel").spelling().isEmpty());
        assertNull(of("panel lang=\"\"").spelling().lang(), "a blank language is no language");
    }

    @Test
    @DisplayName("Java sets them on the value, and every other wither keeps them")
    void java() {
        var attributes = Attributes.NONE.spellcheck(true).lang(Locale.FRENCH);

        assertEquals(new Spelling(true, Locale.FRENCH), attributes.spelling());
        assertEquals(
                attributes.spelling(),
                attributes.id("notes").tooltip("Notes").classes("x").spelling());
        assertEquals(Spelling.NONE, Attributes.NONE.spelling());
    }

    @Test
    @DisplayName("an unset answer is off")
    void resolvedDefault() {
        assertFalse(new Spelling(null, null).checks());
        assertTrue(Spelling.NONE.check(true).checks());
    }
}
