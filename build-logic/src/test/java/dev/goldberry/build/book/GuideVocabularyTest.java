package dev.goldberry.build.book;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GuideVocabulary")
class GuideVocabularyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "| Windows | A child window | Written, unverified |",
        "It is not verified on a Windows desktop.",
        "The path is not yet tested on macOS.",
        "A hand test on the desktop covers it.",
        "Untested on aarch64."
    })
    @DisplayName("finds a line that reports how far something was checked")
    void findsAVerificationState(String line) {
        assertEquals(1, GuideVocabulary.VERIFICATION.in(line).size(), line);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "A test renders the tree and compares it with a golden image.",
        "The frame is measured with `FrameStats`.",
        "While a drag is in progress the pointer is captured."
    })
    @DisplayName("lets a sentence about testing an application through")
    void passesASentenceAboutTesting(String line) {
        assertEquals(List.of(), GuideVocabulary.VERIFICATION.in(line), line);
    }

    @Test
    @DisplayName("finds a library named in a sentence")
    void findsALibrary() {
        assertAll(
                () -> assertEquals(List.of("Yoga"), GuideVocabulary.LIBRARY.in("Layout is flexbox, from Yoga.")),
                () -> assertEquals(
                        List.of("SDL3", "Blend2D"),
                        GuideVocabulary.LIBRARY.in("SDL3 opens the window and Blend2D paints it.")),
                () -> assertEquals(List.of("FFmpeg"), GuideVocabulary.LIBRARY.in("Media is decoded by FFmpeg.")));
    }

    @Test
    @DisplayName("leaves what the reader types or follows: code, link targets and tags")
    void leavesCodeLinksAndTags() {
        assertAll(
                () -> assertEquals(
                        List.of(),
                        GuideVocabulary.LIBRARY.in("Ship the `ffmpeg-linux-x64` classifier, and `SDL_Init` may fail.")),
                () -> assertEquals(
                        List.of(),
                        GuideVocabulary.LIBRARY.in("Icon names are [the bundled set's](https://lucide.dev/icons).")),
                () -> assertEquals(
                        List.of(),
                        GuideVocabulary.LIBRARY.in("<img src=\"../images/yoga.webp\" alt=\"a row\">")));
    }

    @Test
    @DisplayName("matches a library's name as a word, in its own spelling")
    void matchesWholeWords() {
        assertAll(
                () -> assertTrue(GuideVocabulary.LIBRARY.in("A yoga mat is not a layout engine.").isEmpty()),
                () -> assertTrue(GuideVocabulary.LIBRARY.in("SDLC and SDL_ are not the library.").isEmpty()));
    }

    @Test
    @DisplayName("reads a line as the reader sees it")
    void readable() {
        var readable = GuideVocabulary.readable("Read `this` and [the guide](a.md) <b>here</b>.");

        assertAll(
                () -> assertEquals("Read and [the guide] here .", readable.replaceAll("\\s+", " ")),
                () -> assertTrue(readable.contains("[the guide]")));
    }
}
