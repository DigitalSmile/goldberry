package dev.goldberry.example.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the reading size")
class ReadingSizeTest {

    @Test
    @DisplayName("steps up and down and stops at either end")
    void steps() {
        assertEquals(ReadingSize.MEDIUM, ReadingSize.SMALL.larger());
        assertEquals(ReadingSize.LARGE, ReadingSize.MEDIUM.larger());
        assertEquals(ReadingSize.LARGE, ReadingSize.LARGE.larger());
        assertEquals(ReadingSize.MEDIUM, ReadingSize.LARGE.smaller());
        assertEquals(ReadingSize.SMALL, ReadingSize.SMALL.smaller());
    }

    @Test
    @DisplayName("sets body text at 13, 15 and 17")
    void bodySizes() {
        assertEquals(
                List.of(13, 15, 17),
                List.of(ReadingSize.values()).stream().map(ReadingSize::body).toList());
    }

    @Test
    @DisplayName("is a sheet of the theme's type tokens on :root, parsed once")
    void sheets() {
        for (var size : ReadingSize.values()) {
            var sheet = size.stylesheet();
            assertFalse(sheet.rules().isEmpty(), () -> size + " has no rules");
            assertSame(sheet, size.stylesheet(), "a restyle asks again, and the answer is the same sheet");
        }
    }

    @Test
    @DisplayName("reads its name, and falls back to medium for anything else")
    void named() {
        assertEquals(ReadingSize.SMALL, ReadingSize.named("small"));
        assertEquals(ReadingSize.LARGE, ReadingSize.named(" LARGE "));
        assertEquals(ReadingSize.SMALL, ReadingSize.named("s"));
        assertEquals(ReadingSize.MEDIUM, ReadingSize.named("huge"));
    }
}
