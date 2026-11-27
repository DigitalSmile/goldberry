package dev.goldberry.example.nativeimage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.Showcase;

class NativeLibraryCheckTest {

    @Test
    @DisplayName("the showcase verification command fails if the media library is missing")
    void missingMediaFailsTheCheck() {
        // :example:test sets goldberry.media.libdir to a missing directory.
        var failure = assertThrows(
                IllegalStateException.class, () -> Showcase.main(new String[] {"--verify-native-libraries"}));

        assertTrue(failure.getMessage().contains("libgoldberry-media"), failure::getMessage);
        assertTrue(failure.getMessage().contains("media-layout.properties"), failure::getMessage);
    }
}
