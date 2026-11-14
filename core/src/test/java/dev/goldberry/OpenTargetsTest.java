package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("what the system asks an application to open")
class OpenTargetsTest {

    @Test
    @DisplayName("a URL is opened as it came, an existing file as a file URI, and anything else not at all")
    void targets(@TempDir Path directory) throws IOException {
        assertEquals(Optional.of(URI.create("tessera://invite/42")), OpenTargets.of("tessera://invite/42"));
        assertEquals(
                Optional.of(URI.create("https://example.org/a?b=c")), OpenTargets.of(" https://example.org/a?b=c "));

        var file = Files.writeString(directory.resolve("notes one.txt"), "x");
        assertEquals(Optional.of(file.toAbsolutePath().toUri()), OpenTargets.of(file.toString()));

        assertEquals(Optional.empty(), OpenTargets.of("--minimised"), "a flag is the application's");
        assertEquals(
                Optional.empty(),
                OpenTargets.of(directory.resolve("missing.txt").toString()));
        assertEquals(Optional.empty(), OpenTargets.of(""));
    }
}
