package dev.goldberry.text.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.select.PseudoElement;

/// The registry: which checker a field gets, and what a field hears.
class SpellCheckersTest {

    @AfterEach
    void clear() {
        SpellCheckers.clear();
    }

    @Test
    @DisplayName("a checker answers its locale, its language, and the language in another country")
    void lookup() {
        var us = new WordListChecker(Locale.US, Set.of("color"));
        SpellCheckers.install(us);

        assertSame(us, SpellCheckers.find(Locale.US).orElseThrow());
        assertSame(us, SpellCheckers.find(Locale.ENGLISH).orElseThrow());
        assertSame(us, SpellCheckers.find(Locale.UK).orElseThrow());
        assertEquals(Optional.empty(), SpellCheckers.find(Locale.GERMAN));

        var uk = new WordListChecker(Locale.UK, Set.of("colour"));
        SpellCheckers.install(uk);
        assertSame(uk, SpellCheckers.find(Locale.UK).orElseThrow(), "an exact one wins");
    }

    @Test
    @DisplayName("a field with no language uses the default language, or the only checker there is")
    void forField() {
        var de = new WordListChecker(Locale.GERMAN, Set.of("Farbe"));
        SpellCheckers.install(de);
        assertSame(de, SpellCheckers.forField(null).orElseThrow(), "one checker answers every field");

        var fr = new WordListChecker(Locale.FRENCH, Set.of("couleur"));
        SpellCheckers.install(fr);
        assertEquals(Optional.empty(), SpellCheckers.forField(null), "two, and no default: nobody guesses");

        SpellCheckers.defaultLanguage(Locale.FRANCE);
        assertSame(fr, SpellCheckers.forField(null).orElseThrow());
        assertSame(de, SpellCheckers.forField(Locale.GERMAN).orElseThrow(), "a field's own lang wins");
        assertEquals(Optional.empty(), SpellCheckers.forField(Locale.ITALIAN));
    }

    @Test
    @DisplayName("installing and taking away tells the listeners")
    void listeners() {
        var told = new AtomicInteger();
        try (var _ = SpellCheckers.onChange(told::incrementAndGet)) {
            SpellCheckers.install(WordListChecker.english("a"));
            assertTrue(SpellCheckers.uninstall(Locale.US));
            assertFalse(SpellCheckers.uninstall(Locale.US));
        }
        assertEquals(2, told.get());
    }

    @Test
    @DisplayName("one personal dictionary per language")
    void personalPerLanguage() {
        var us = SpellCheckers.personal(Locale.US);

        assertSame(us, SpellCheckers.personal(Locale.UK));
        assertEquals(Locale.ENGLISH, us.locale());

        us.addAll(List.of("zeta", "alpha"));
        assertEquals(List.of("alpha", "zeta"), us.words());
        assertEquals(1, us.revision(), "addAll tells once");
        assertThrows(IllegalArgumentException.class, () -> us.add(" spaced "));
    }

    @Test
    @DisplayName("loading with no engine on the module path says which module to add")
    void noEngine() {
        var empty = new ByteArrayInputStream(new byte[0]);

        var thrown = assertThrows(IllegalStateException.class, () -> SpellCheckers.load(Locale.US, empty, empty));
        assertTrue(thrown.getMessage().contains("goldberry-spell"), thrown.getMessage());
    }

    @Test
    @DisplayName("::spelling-error and ::grammar-error are pseudo-elements a sheet may write")
    void pseudoElements() {
        assertEquals(PseudoElement.SPELLING_ERROR, PseudoElement.parse("spelling-error"));
        assertEquals(PseudoElement.GRAMMAR_ERROR, PseudoElement.parse("grammar-error"));
        assertEquals("spelling-error", PseudoElement.SPELLING_ERROR.cssName());
    }
}
