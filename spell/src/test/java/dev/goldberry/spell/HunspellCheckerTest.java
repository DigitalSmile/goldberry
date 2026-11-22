package dev.goldberry.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.text.spell.Misspelling;
import dev.goldberry.text.spell.SpellCheck;
import dev.goldberry.text.spell.SpellCheckers;

/// Lucene's Hunspell over two dictionaries written for these tests: a UTF-8
/// English one with a suffix, a prefix and replacement rules, and a Latin-1
/// German one.
class HunspellCheckerTest {

    private static HunspellChecker english;

    @BeforeAll
    static void load() throws IOException {
        try (var aff = resource("tiny.aff");
                var dic = resource("tiny.dic")) {
            english = HunspellChecker.load(Locale.US, aff, dic);
        }
    }

    @Nested
    @DisplayName("checking")
    class Checking {

        @ParameterizedTest
        @ValueSource(strings = {"the", "quick", "fox", "dog", "dogs", "do", "undo", "naïve", "Paris", "The", "QUICK"})
        @DisplayName("accepts the words, their affixed forms and their capitals")
        void correct(String word) {
            assertTrue(english.isCorrect(word), word);
        }

        @ParameterizedTest
        @ValueSource(strings = {"recieve", "teh", "quikc", "paris", "unthe", "foxs", "naive"})
        @DisplayName("refuses a misspelling, a proper noun in small letters, and an affix a word does not take")
        void incorrect(String word) {
            assertFalse(english.isCorrect(word), word);
        }

        @Test
        @DisplayName("reads a dictionary in the encoding its SET line names")
        void latin1() throws IOException {
            HunspellChecker german;
            try (var aff = resource("latin.aff");
                    var dic = resource("latin.dic")) {
                german = HunspellChecker.load(Locale.GERMAN, aff, dic);
            }

            assertTrue(german.isCorrect("straße"));
            assertTrue(german.isCorrect("über"));
            assertFalse(german.isCorrect("strasse"));
            assertEquals(Locale.GERMAN, german.locale());
        }
    }

    @Nested
    @DisplayName("suggesting")
    class Suggesting {

        @Test
        @DisplayName("finds the word a replacement rule leads to, first")
        void replacement() {
            assertEquals("receive", english.suggest("recieve", 5).getFirst());
        }

        @Test
        @DisplayName("finds a transposition")
        void transposition() {
            assertTrue(
                    english.suggest("qucik", 5).contains("quick"),
                    english.suggest("qucik", 5).toString());
        }

        @Test
        @DisplayName("keeps to the limit, and suggests nothing for a correct word")
        void limits() {
            assertTrue(english.suggest("teh", 1).size() <= 1);
            assertEquals(List.of(), english.suggest("the", 5));
            assertEquals(List.of(), english.suggest("teh", 0));
        }
    }

    @Test
    @DisplayName("loads from files as from streams")
    void fromFiles(@TempDir Path dir) throws IOException {
        var aff = dir.resolve("en.aff");
        var dic = dir.resolve("en.dic");
        try (var in = resource("tiny.aff")) {
            Files.copy(in, aff);
        }
        try (var in = resource("tiny.dic")) {
            Files.copy(in, dic);
        }

        assertTrue(HunspellChecker.load(Locale.UK, aff, dic).isCorrect("brown"));
    }

    @Test
    @DisplayName("a file that is not a dictionary is an IOException that says so")
    void notADictionary() {
        var aff = new ByteArrayInputStream("SET UTF-8\nSFX S Y nonsense\n".getBytes(StandardCharsets.UTF_8));
        var dic = new ByteArrayInputStream("not a count\nword\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> HunspellChecker.load(Locale.US, aff, dic));
    }

    @Test
    @DisplayName("answers the same from many threads at once")
    void concurrent() throws Exception {
        var words = List.of("the", "recieve", "dogs", "teh", "naïve", "quikc");
        var expected = words.stream().map(english::isCorrect).toList();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var answers = new ArrayList<Future<List<Boolean>>>();
            for (var i = 0; i < 16; i++) {
                Callable<List<Boolean>> task = () -> {
                    var mine = new ArrayList<Boolean>();
                    for (var round = 0; round < 50; round++) {
                        mine.clear();
                        for (var word : words) {
                            mine.add(english.isCorrect(word));
                        }
                        english.suggest("recieve", 3);
                    }
                    return mine;
                };
                answers.add(pool.submit(task));
            }
            for (var answer : answers) {
                assertEquals(expected, answer.get());
            }
        }
    }

    /// The service: `SpellCheckers.load` finds this module's provider, and a
    /// field's check works over what it installed.
    @Nested
    @DisplayName("as the engine on the module path")
    class AsAService {

        @AfterEach
        void clear() {
            SpellCheckers.clear();
        }

        @Test
        @DisplayName("SpellCheckers.load finds it and installs the checker")
        void found() throws IOException {
            try (var aff = resource("tiny.aff");
                    var dic = resource("tiny.dic")) {
                var checker = SpellCheckers.load(Locale.US, aff, dic);
                assertInstanceOf(HunspellChecker.class, checker);
            }

            var installed = SpellCheckers.forField(Locale.US).orElseThrow();
            try (var check =
                    new SpellCheck(installed, SpellCheckers.personal(Locale.US), SpellCheck.Threads.IMMEDIATE)) {
                var found = check.misspellings("The quick brown fox could not recieve it");
                assertEquals(
                        List.of("could", "not", "recieve", "it"),
                        found.stream().map(Misspelling::word).toList(),
                        "every word the tiny dictionary lacks, and nothing it has");
            }
        }

        @Test
        @DisplayName("names itself")
        void name() {
            assertEquals("Lucene Hunspell", new HunspellProvider().name());
        }
    }

    private static InputStream resource(String name) {
        var stream = HunspellCheckerTest.class.getResourceAsStream(name);
        if (stream == null) {
            throw new UncheckedIOException(new IOException("missing test dictionary " + name));
        }
        return stream;
    }
}
