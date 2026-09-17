package io.github.digitalsmile.goldberry.widgets.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;

/// The binary names of every top-level type under a source root, for the sweeps
/// that read the catalog from its sources rather than from the classpath.
///
/// A relative path's elements are joined with dots, rather than its string form
/// having `/` replaced: `Path.toString()` uses the platform's separator, and on
/// Windows a `replace('/', '.')` over `widgets\controls\Button` changed nothing —
/// so `Class.forName` found no class, and both sweeps reported an empty catalog
/// as though it were the wrong tree (ADR-0338).
final class SourceTree {

    private SourceTree() {}

    /// Every `.java` file under `root` except the package and module
    /// descriptors, as the binary name of its top-level type, sorted.
    static List<String> binaryNames(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".java"))
                    .map(root::relativize)
                    .filter(file -> !file.endsWith("package-info.java") && !file.endsWith("module-info.java"))
                    .map(SourceTree::binaryName)
                    .sorted()
                    .toList();
        }
    }

    /// `widgets/controls/Button.java` → `widgets.controls.Button`, on any platform.
    static String binaryName(Path relativeSource) {
        var name = new StringJoiner(".");
        for (var element : relativeSource) {
            var text = element.toString();
            name.add(text.endsWith(".java") ? text.substring(0, text.length() - ".java".length()) : text);
        }
        return name.toString();
    }
}
