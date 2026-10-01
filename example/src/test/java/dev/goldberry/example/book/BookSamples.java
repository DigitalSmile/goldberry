package dev.goldberry.example.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// The fenced samples under `book/src`, found from wherever the test runs.
///
/// The repository root is found by walking up to the directory that holds
/// `book/src/SUMMARY.md`, because Gradle runs a test in the module's directory
/// and an IDE runs it wherever it likes.
final class BookSamples {

    /// The decision log, whose samples are history rather than documentation.
    private static final String LOG = "adr/";

    private static final Pattern FENCE = Pattern.compile("^```(\\S*)\\s*$");

    private BookSamples() {}

    /// A fenced sample: the chapter it is in, the line its fence opens on, the
    /// info string after the fence, and the text between the fences.
    record Sample(String chapter, int line, String info, String text) {

        /// The language before any comma: `kdl` for `kdl,ignore`.
        String language() {
            var comma = info.indexOf(',');
            return comma < 0 ? info : info.substring(0, comma);
        }

        /// Whether the author said the block is not a whole document.
        boolean ignored() {
            return info.contains("ignore");
        }
    }

    /// Every `kdl` sample in the guide that is not marked `ignore`.
    static List<Sample> kdl() {
        return pages().stream()
                .flatMap(page -> samples(page).stream())
                .filter(sample -> sample.language().equals("kdl") && !sample.ignored())
                .toList();
    }

    static Path source() {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            var summary = directory.resolve("book/src/SUMMARY.md");
            if (Files.isRegularFile(summary)) {
                return summary.getParent();
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException(
                "cannot find book/src/SUMMARY.md above " + Path.of("").toAbsolutePath());
    }

    private static List<Path> pages() {
        var root = source();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path ->
                            !root.relativize(path).toString().replace('\\', '/').startsWith(LOG))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + root, e);
        }
    }

    private static List<Sample> samples(Path page) {
        var chapter = source().relativize(page).toString().replace('\\', '/');
        List<String> lines;
        try {
            lines = Files.readAllLines(page);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + page, e);
        }
        var samples = new ArrayList<Sample>();
        String info = null;
        var body = new StringBuilder();
        var opened = 0;
        for (var number = 0; number < lines.size(); number++) {
            var line = lines.get(number);
            if (info == null) {
                var fence = FENCE.matcher(line);
                if (fence.matches()) {
                    info = fence.group(1);
                    opened = number + 1;
                    body.setLength(0);
                }
            } else if (line.strip().equals("```")) {
                samples.add(new Sample(chapter, opened, info, body.toString()));
                info = null;
            } else {
                body.append(line).append('\n');
            }
        }
        return samples;
    }
}
