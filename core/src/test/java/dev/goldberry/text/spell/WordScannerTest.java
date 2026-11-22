package dev.goldberry.text.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// Which words of a line a checker is asked about.
class WordScannerTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Hello, world!                    | Hello world",
                "don't stop                       | don't stop",
                "l’eau froide                     | l’eau froide",
                "'quoted'                         | quoted",
                "well-known                       | well known",
                "see https://example.com/teh now  | see now",
                "www.example.com teh              | teh",
                "mail me@example.com tomorow      | mail tomorow",
                "ask @anna about #release         | ask about",
                "mp3 2nd v8 flac                  | flac",
                "a I x ox                         | ox",
                "NASA and HTTP                    | and",
                "iPhone getValue Name             | Name",
                "naïve café                       | naïve café",
                "Привет мир                       | Привет мир",
                "日本語のテキスト words              | words",
                "and/or                           | and or",
            })
    @DisplayName("finds the words worth asking about")
    void table(String line, String expected) {
        var words = WordScanner.words(line).stream().map(WordScanner.Word::text).toList();

        assertEquals(List.of(expected.split(" ")), words);
    }

    @Test
    @DisplayName("an apostrophe that ends a word is not part of it")
    void trailingApostrophe() {
        var words = WordScanner.words("the cats' toys");

        assertEquals(
                List.of("the", "cats", "toys"),
                words.stream().map(WordScanner.Word::text).toList());
    }

    @Test
    @DisplayName("each word is the range of the line it came from")
    void ranges() {
        var line = "  teh, quick";
        var words = WordScanner.words(line);

        assertEquals(new WordScanner.Word(2, 5, "teh"), words.getFirst());
        assertEquals(new WordScanner.Word(7, 12, "quick"), words.get(1));
        for (var word : words) {
            assertEquals(word.text(), line.substring(word.start(), word.end()));
        }
    }

    @Test
    @DisplayName("an empty or blank line has no words")
    void empty() {
        assertEquals(List.of(), WordScanner.words(""));
        assertEquals(List.of(), WordScanner.words("   \t "));
    }
}
