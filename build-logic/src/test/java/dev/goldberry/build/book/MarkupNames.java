package dev.goldberry.build.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.goldberry.build.repository.Repository;

/**
 * Every node name a document can write, read off the {@code @Markup} annotations
 * in the shipped modules.
 *
 * <p>Read from the sources rather than from a catalogue the build generated,
 * because this test runs in {@code build-logic}, which builds before any module
 * does and sees none of their output. The annotation is one token on one line,
 * and the weaver's own test fixture, which annotates a class to test the
 * annotation, is under {@code src/test} and therefore not here.
 */
final class MarkupNames {

    /** The modules that ship widgets. A new one joins this list and the catalogue chapter. */
    static final List<String> MODULES = List.of("widgets", "html", "media", "gpu");

    private static final Pattern MARKUP = Pattern.compile("@Markup\\(\"([a-z0-9-]+)\"\\)");

    private MarkupNames() {
    }

    /** Each name once, sorted, from every main source file of the shipped modules. */
    static List<String> all() {
        return MODULES.stream()
                .flatMap(MarkupNames::sources)
                .flatMap(source -> MARKUP.matcher(source).results().map(match -> match.group(1)))
                .distinct()
                .sorted()
                .toList();
    }

    private static Stream<String> sources(String module) {
        var root = Repository.root().resolve(module).resolve("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .map(MarkupNames::read)
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + root, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
