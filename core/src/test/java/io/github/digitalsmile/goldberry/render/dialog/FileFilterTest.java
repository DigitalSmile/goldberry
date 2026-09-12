package io.github.digitalsmile.goldberry.render.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a type dropdown row is, and what it refuses to be.
///
/// The normalisation is the part worth pinning: every platform spells a filter
/// differently, so the toolkit's vocabulary is the part they agree on — a bare,
/// lower-cased extension — and anything that looks like one platform's dialect is
/// rejected here rather than silently shown to a user as a dropdown that lists no
/// files (ADR-0287).
class FileFilterTest {

    @Test
    @DisplayName("strips a leading dot, because that is what a Path shows you")
    void stripsTheDot() {
        assertEquals(List.of("md"), FileFilter.of("Markdown", ".md").extensions());
        assertEquals(
                List.of("png", "jpg"), FileFilter.of("Images", "png", ".jpg").extensions());
    }

    @Test
    @DisplayName("strips a leading star, because that is what a Windows filter looks like")
    void stripsTheStar() {
        assertEquals(List.of("png"), FileFilter.of("Images", "*.png").extensions());
    }

    @Test
    @DisplayName("lower-cases, because no platform matches case-sensitively")
    void lowerCases() {
        assertEquals(List.of("png"), FileFilter.of("Images", "PNG").extensions());
    }

    @Test
    @DisplayName("drops a duplicate, which two spellings of one extension are")
    void deduplicates() {
        assertEquals(
                List.of("png"), FileFilter.of("Images", "png", ".PNG", "*.png").extensions());
    }

    @Test
    @DisplayName("refuses a pattern or a MIME type, rather than passing it to the platform")
    void refusesPatterns() {
        assertThrows(IllegalArgumentException.class, () -> FileFilter.of("Images", "*.png, *.jpg"));
        assertThrows(IllegalArgumentException.class, () -> FileFilter.of("Images", "image/png"));
        assertThrows(IllegalArgumentException.class, () -> FileFilter.of("Images", "png;jpg"));
    }

    @Test
    @DisplayName("refuses an extension that is only punctuation")
    void refusesNothing() {
        assertThrows(IllegalArgumentException.class, () -> FileFilter.of("Images", "."));
        assertThrows(IllegalArgumentException.class, () -> FileFilter.of("Images", "*."));
        assertThrows(IllegalArgumentException.class, () -> FileFilter.of("Images", "  "));
    }

    @Test
    @DisplayName("refuses a blank label, which would be an unreadable dropdown row")
    void refusesABlankLabel() {
        assertThrows(IllegalArgumentException.class, () -> FileFilter.of(" ", "png"));
    }

    @Test
    @DisplayName("everything() is the row with no extensions, and it matches anything")
    void everythingMatchesAnything() {
        var all = FileFilter.everything("All files");

        assertTrue(all.matchesEverything());
        assertTrue(all.matches("board.png"));
        assertTrue(all.matches("no-extension"));
    }

    @Test
    @DisplayName("matches() is case-insensitive and wants the dot back")
    void matchesByExtension() {
        var images = FileFilter.of("Images", "png", "jpg");

        assertTrue(images.matches("board.png"));
        assertTrue(images.matches("BOARD.PNG"));
        assertFalse(images.matches("board.gif"));
        // "png" is not ".png": a file called exactly the extension is not a match,
        // which is what a save dialog's "add the extension if it is missing" leans
        // on.
        assertFalse(images.matches("png"));
    }
}
