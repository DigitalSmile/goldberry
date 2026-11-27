package dev.goldberry.content.select;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import dev.goldberry.text.flow.Hyphens;

class DocumentHyphensTest {

    @Test
    void explicitHyphenInAnotherSpanSupersedesAutomaticBreaksInTheEntireWord() {
        var text = "internationalization\u00adless";
        var automatic = new DocumentHyphens.Run(0, 20, Hyphens.AUTO, Locale.US);
        var manual = new DocumentHyphens.Run(20, text.length(), Hyphens.MANUAL, null);
        assertArrayEquals(
                new int[] {21},
                DocumentHyphens.offsets(text, List.of(automatic, manual)).stream()
                        .toArray());
        assertArrayEquals(
                new int[0],
                DocumentHyphens.offsets(text, List.of(automatic)).stream().toArray());
    }

    @Test
    void aLanguageOrStyleBoundaryDoesNotCreateAWordBoundary() {
        var text = "hyphenation123";
        var automatic = new DocumentHyphens.Run(0, 11, Hyphens.AUTO, Locale.US);
        assertArrayEquals(
                new int[0],
                DocumentHyphens.offsets(text, List.of(automatic)).stream().toArray());
    }

    @Test
    void mixedLanguagesUseWholeWordPatternsOnlyBetweenEligibleCharacters() {
        var text = "internationalization";
        var foreign = new DocumentHyphens.Run(0, 7, Hyphens.AUTO, Locale.FRENCH);
        var english = new DocumentHyphens.Run(7, 13, Hyphens.AUTO, Locale.ENGLISH);
        var american = new DocumentHyphens.Run(13, text.length(), Hyphens.AUTO, Locale.US);
        assertArrayEquals(
                new int[] {11, 13, 16},
                DocumentHyphens.offsets(text, List.of(foreign, english, american)).stream()
                        .toArray());
    }
}
