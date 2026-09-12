package io.github.digitalsmile.goldberry.natives.sdl.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SDL's filter dialect, and the shapes of it that are undefined behaviour.
///
/// SDL's own header says a pattern containing a dot, a space or a `*` anywhere
/// but alone is undefined, and what "undefined" means in practice is a dialog
/// that lists nothing — the hardest kind of bug to read backwards. So it is
/// refused here, where the stack trace names the caller.
class SdlFileFilterTest {

    @Test
    @DisplayName("joins extensions with semicolons, which is what SDL reads")
    void joinsWithSemicolons() {
        assertEquals(
                "png;jpg;jpeg", SdlFileFilter.of("Images", "png", "jpg", "jpeg").pattern());
    }

    @Test
    @DisplayName("'*' alone is the every-file row")
    void everyFile() {
        assertEquals(SdlFileFilter.ALL, SdlFileFilter.all("All files").pattern());
    }

    @Test
    @DisplayName("refuses a glob, a dot or a MIME type")
    void refusesUndefinedPatterns() {
        assertThrows(IllegalArgumentException.class, () -> new SdlFileFilter("Images", "*.png"));
        assertThrows(IllegalArgumentException.class, () -> new SdlFileFilter("Images", ".png"));
        assertThrows(IllegalArgumentException.class, () -> new SdlFileFilter("Images", "image/png"));
        assertThrows(IllegalArgumentException.class, () -> new SdlFileFilter("Images", "png, jpg"));
    }

    @Test
    @DisplayName("refuses an empty entry, which a trailing separator leaves behind")
    void refusesEmptyEntries() {
        assertThrows(IllegalArgumentException.class, () -> new SdlFileFilter("Images", "png;"));
        assertThrows(IllegalArgumentException.class, () -> new SdlFileFilter("Images", ""));
    }

    @Test
    @DisplayName("refuses a blank name, which would be a blank dropdown row")
    void refusesABlankName() {
        assertThrows(IllegalArgumentException.class, () -> new SdlFileFilter(" ", "png"));
    }
}
