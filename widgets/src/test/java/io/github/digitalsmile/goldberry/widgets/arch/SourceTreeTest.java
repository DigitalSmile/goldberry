package io.github.digitalsmile.goldberry.widgets.arch;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [SourceTree] is what two catalog sweeps stand on: a helper that named no
/// class would make both of them fail over an empty list, which is exactly what
/// happened on Windows.
@DisplayName("the source tree")
class SourceTreeTest {

    @Test
    @DisplayName("names a type by its path elements, whatever the separator was")
    void binaryNameFromElements() {
        assertEquals(
                "io.github.digitalsmile.goldberry.widgets.controls.Button",
                SourceTree.binaryName(
                        Path.of("io", "github", "digitalsmile", "goldberry", "widgets", "controls", "Button.java")));
    }

    @Test
    @DisplayName("reads this module's catalog and finds a class that is in it")
    void readsTheCatalog() throws IOException {
        var names = SourceTree.binaryNames(Path.of("src/main/java"));
        assertAll(
                () -> assertTrue(
                        names.contains("io.github.digitalsmile.goldberry.widgets.controls.button.Button"),
                        names.toString()),
                () -> assertFalse(
                        names.stream().anyMatch(name -> name.contains("/") || name.contains("\\")),
                        "a separator survived"),
                () -> assertFalse(names.stream()
                        .anyMatch(name -> name.endsWith("package-info") || name.endsWith("module-info"))));
    }
}
