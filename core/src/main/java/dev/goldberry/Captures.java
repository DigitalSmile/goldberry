package dev.goldberry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/// A window's picture written to a file: what `--capture=` and
/// [Host#capture(Path)] share.
final class Captures {

    private Captures() {}

    /// Writes `window`'s last frame to `path` as a PNG, making the directories
    /// on the way.
    ///
    /// @return false when the window has no frame to capture yet
    /// @throws UncheckedIOException when the file cannot be written
    static boolean write(Window window, Path path) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(path, "path");
        var picture = window.capture();
        if (picture.isEmpty()) {
            return false;
        }
        try {
            var parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, picture.get().encodePng());
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(path + " could not be written", e);
        }
    }
}
