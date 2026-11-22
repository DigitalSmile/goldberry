package dev.goldberry.example.ui.forms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.spelling.ShowcaseDictionary;
import dev.goldberry.text.spell.Misspelling;
import dev.goldberry.text.spell.SpellCheck;
import dev.goldberry.text.spell.SpellCheckers;

/// SCOWL's `en_US`, read by `goldberry-spell`: the dictionary the build
/// prepared is there, the engine reads it, and the Spelling card's words have
/// exactly the misspellings they were written with.
class SpellingCardTest {

    @AfterAll
    static void clear() {
        SpellCheckers.clear();
    }

    @Test
    @DisplayName("the showcase's dictionary loads, and knows English from its misspellings")
    void english() {
        var checker = ShowcaseDictionary.english().orElseThrow();

        assertEquals(Locale.US, checker.locale());
        for (var word : List.of("received", "following", "morning", "letter", "hobbit", "don't", "Gandalf")) {
            assertTrue(checker.isCorrect(word), word);
        }
        for (var word : List.of("recieved", "folowing", "jorney", "teh")) {
            assertFalse(checker.isCorrect(word), word);
        }
        var suggestions = checker.suggest("recieved", 5);
        assertTrue(suggestions.contains("received"), suggestions::toString);
    }

    @Test
    @DisplayName("the card's note has exactly its two misspellings, and its title one")
    void theCardsWords() {
        var checker = ShowcaseDictionary.english().orElseThrow();
        try (var check = new SpellCheck(checker, SpellCheckers.personal(Locale.US), SpellCheck.Threads.IMMEDIATE)) {
            var note = SpellingCard.NOTE
                    .lines()
                    .flatMap(line -> check.misspellings(line).stream())
                    .map(Misspelling::word)
                    .toList();
            assertEquals(List.of("recieved", "folowing"), note);
            assertEquals(
                    List.of("jorney"),
                    check.misspellings("A jorney there and back").stream()
                            .map(Misspelling::word)
                            .toList());
        }
    }
}
