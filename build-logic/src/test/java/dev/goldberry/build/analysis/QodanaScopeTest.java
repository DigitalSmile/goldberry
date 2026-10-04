package dev.goldberry.build.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.goldberry.build.repository.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What Qodana inspects as the toolkit's own code: {@code main}, and nothing a
 * source set of measurement code adds.
 *
 * <p>The dashboard leaves out the tests, which IntelliJ imports as test sources.
 * Any other source set is imported as production code, so the day the
 * benchmarks moved out of the test trees into {@code src/benchmark} they were
 * inspected as the toolkit, and 51 findings in them failed the gate at once.
 * A source set that is neither the toolkit nor a test is excluded by name.
 *
 * <p>By the name of each directory, not by a glob. {@code qodana.yaml} named
 * {@code "**}{@code /src/benchmark"} on 2026-10-03, the linter accepted it and
 * ignored it, and the next run reported 52 findings from the directories it
 * was meant to leave out. An {@code exclude.paths} entry is a path from the
 * project root, and these guards hold the file to that.
 */
@DisplayName("Qodana's scope")
class QodanaScopeTest {

    /** Source sets IntelliJ already knows are the toolkit, or are its tests. */
    private static final Set<String> KNOWN = Set.of("main", "test", "testFixtures");

    @Test
    @DisplayName("leaves out every source set that is not the toolkit or its tests, by its directory")
    void excludesMeasurementCode() {
        var qodana = Repository.read("qodana.yaml");
        var unexcluded = new TreeSet<String>();
        var found = sourceSetDirectories();
        for (var directory : found) {
            if (!qodana.contains("- " + directory + "\n")) {
                unexcluded.add(directory);
            }
        }
        assertFalse(found.isEmpty(), "found no source sets; is the walk wrong?");
        assertEquals(Set.of(), unexcluded, "qodana.yaml inspects these source sets as production code");
    }

    /**
     * The {@code "**}{@code /build"} line is left alone: whether the linter
     * applies it has not been measured, and a build directory is not imported
     * as a source set either way. A glob over {@code src/} was measured, and
     * did nothing.
     */
    @Test
    @DisplayName("names no source set by a glob, which the linter accepts and does not apply")
    void noGlob() {
        var globs = Repository.read("qodana.yaml")
                .lines()
                .map(String::strip)
                .filter(line -> line.startsWith("- ") && line.contains("*") && line.contains("/src/"))
                .toList();
        assertEquals(List.of(), globs, "qodana.yaml excludes a source set by glob, which Qodana ignores");
    }

    /** Every {@code <module>/src/<set>} with a {@code java} directory whose set is not the toolkit or its tests. */
    private static Set<String> sourceSetDirectories() {
        var directories = new TreeSet<String>();
        try (Stream<Path> modules = Files.list(Repository.root())) {
            for (var module : modules.filter(Files::isDirectory).toList()) {
                var src = module.resolve("src");
                if (!Files.isDirectory(src)) {
                    continue;
                }
                try (Stream<Path> children = Files.list(src)) {
                    List<Path> java = children.filter(set -> Files.isDirectory(set.resolve("java"))).toList();
                    for (var set : java) {
                        var name = set.getFileName().toString();
                        if (!KNOWN.contains(name)) {
                            directories.add(module.getFileName() + "/src/" + name);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return directories;
    }
}
