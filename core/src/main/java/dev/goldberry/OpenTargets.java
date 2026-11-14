package dev.goldberry;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

/// What the system asked an application to open, as a URI: a URL as it came,
/// and a file's path as a `file:` URI. A flag, or text that is neither, is
/// nothing to open.
final class OpenTargets {

    private OpenTargets() {}

    /// `target` as something to open, or empty.
    ///
    /// A URL has a scheme of two letters or more, so `C:\notes.txt` on Windows
    /// is a path and not a URL with the scheme `c`. A path is opened only when
    /// the file is there: anything else on a command line is the application's
    /// own argument.
    static Optional<URI> of(String target) {
        var text = target.strip();
        if (text.isEmpty() || text.startsWith("-")) {
            return Optional.empty();
        }
        try {
            var uri = new URI(text);
            var scheme = uri.getScheme();
            if (scheme != null && scheme.length() > 1) {
                return Optional.of(uri);
            }
        } catch (URISyntaxException e) {
            // Not a URL; perhaps a path with a space in it.
        }
        try {
            var path = Path.of(text);
            return Files.exists(path) ? Optional.of(path.toAbsolutePath().toUri()) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
