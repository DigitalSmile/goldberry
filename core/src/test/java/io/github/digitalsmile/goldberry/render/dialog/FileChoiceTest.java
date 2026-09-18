package io.github.digitalsmile.goldberry.render.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The three answers, and the fourth that cannot exist.
class FileChoiceTest {

    @Test
    @DisplayName("a chosen path is the first one, whether or not many were allowed")
    void firstPath() {
        var chosen = FileChoice.of(List.of(Path.of("/a.png"), Path.of("/b.png")));

        assertEquals(Path.of("/a.png"), chosen.path());
        assertEquals(2, chosen.paths().size());
        assertTrue(chosen.filter().isEmpty(), "no platform reported a filter, so none should be claimed");
    }

    @Test
    @DisplayName("refuses an empty choice, which is a cancel wearing the wrong type")
    void refusesAnEmptyChoice() {
        assertThrows(IllegalArgumentException.class, () -> new FileChoice.Chosen(List.of(), Optional.empty()));
    }

    @Test
    @DisplayName("a cancel is one value, because nothing distinguishes two of them")
    void cancelIsOneValue() {
        assertSame(FileChoice.cancelled(), FileChoice.cancelled());
        assertEquals(FileChoice.cancelled(), new FileChoice.Cancelled());
    }

    @Test
    @DisplayName("paths() is empty for a cancel and a failure, so a caller need not switch to ignore them")
    void pathsOfNothing() {
        assertEquals(List.of(), FileChoice.cancelled().paths());
        assertEquals(List.of(), FileChoice.failed("no portal").paths());
    }

    private static String describe(FileChoice choice) {
        return switch (choice) {
            case FileChoice.Chosen chosen -> "chose " + chosen.path();
            case FileChoice.Cancelled _ -> "cancelled";
            case FileChoice.Failed(var message) -> "failed: " + message;
        };
    }
}
