package dev.goldberry.build.testing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.build.repository.Repository;

@DisplayName("ForwardedProperties")
class ForwardedPropertiesTest {

    private static Optional<String> from(Map<String, String> values, String name) {
        return Optional.ofNullable(values.get(name));
    }

    @Test
    @DisplayName("hands on what was set, a -D winning over a -P, in the list's order")
    void systemWins() {
        var system = Map.of("goldberry.golden.update", "true", "goldberry.gpu.required", "true");
        var gradle = Map.of("goldberry.gpu.required", "false", "goldberry.native.required", "true");
        var resolved = ForwardedProperties.resolve(
                List.of("goldberry.native.required", "goldberry.gpu.required", "goldberry.golden.update", "unset"),
                name -> from(system, name),
                name -> from(gradle, name));
        assertEquals(
                List.of(
                        Map.entry("goldberry.native.required", "true"),
                        Map.entry("goldberry.gpu.required", "true"),
                        Map.entry("goldberry.golden.update", "true")),
                List.copyOf(resolved.entrySet()));
    }

    @Test
    @DisplayName("leaves the library itself to NativeTests, which knows what none given means")
    void libraryIsNotForwarded() {
        assertAll(
                () -> assertFalse(ForwardedProperties.TO_TESTS.contains(NativeTests.LIBRARY_PROPERTY)),
                () -> assertFalse(ForwardedProperties.TO_TESTS.contains(NativeTests.SKIP_PROPERTY)),
                () -> assertEquals(
                        ForwardedProperties.TO_TESTS.size(),
                        ForwardedProperties.TO_TESTS.stream().distinct().count(),
                        "a property listed twice"));
    }

    /// The guard that replaces seventeen hand-written loops. A property a test
    /// reads with `System.getProperty("goldberry...")` and that the command line
    /// sets is on the list, or a CI job's `-Dgoldberry.x.required=true` reaches the
    /// daemon and stops there -- the outcome every one of those loops was written
    /// to prevent, one module at a time.
    @Test
    @DisplayName("forwards every switch the verify jobs set on the command line")
    void coversWhatCiSets() {
        var flag = Pattern.compile("-[DP](goldberry\\.[A-Za-z.]+)=");
        var set = Repository.workflowNames().stream()
                .map(Repository::workflow)
                .flatMap(text -> flag.matcher(text).results().map(match -> match.group(1)))
                .filter(name -> name.endsWith(".required") || name.startsWith("goldberry.golden.")
                        || name.equals("goldberry.gpu.videoDriver"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertTrue(set.contains("goldberry.native.required"), "no workflow sets native.required: " + set);
        var missing = set.stream().filter(name -> !ForwardedProperties.TO_TESTS.contains(name)).toList();
        assertTrue(missing.isEmpty(), "set by a workflow and never handed to a test JVM: " + missing);
    }
}
