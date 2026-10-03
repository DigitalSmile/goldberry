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
 */
@DisplayName("Qodana's scope")
class QodanaScopeTest {

    /** Source sets IntelliJ already knows are the toolkit, or are its tests. */
    private static final Set<String> KNOWN = Set.of("main", "test", "testFixtures");

    @Test
    @DisplayName("leaves out every source set that is not the toolkit or its tests")
    void excludesMeasurementCode() {
        var qodana = Repository.read("qodana.yaml");
        var unexcluded = new TreeSet<String>();
        var found = sourceSets();
        for (var set : found) {
            if (!KNOWN.contains(set) && !qodana.contains("- \"**/src/" + set + "\"")) {
                unexcluded.add(set);
            }
        }
        assertFalse(found.isEmpty(), "found no source sets; is the walk wrong?");
        assertEquals(Set.of(), unexcluded, "qodana.yaml inspects these source sets as production code");
    }

    /** The name of every {@code <module>/src/<set>/java} in the repository. */
    private static Set<String> sourceSets() {
        var sets = new TreeSet<String>();
        try (Stream<Path> modules = Files.list(Repository.root())) {
            for (var module : modules.filter(Files::isDirectory).toList()) {
                var src = module.resolve("src");
                if (!Files.isDirectory(src)) {
                    continue;
                }
                try (Stream<Path> children = Files.list(src)) {
                    List<Path> java = children.filter(set -> Files.isDirectory(set.resolve("java"))).toList();
                    java.forEach(set -> sets.add(set.getFileName().toString()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sets;
    }
}
