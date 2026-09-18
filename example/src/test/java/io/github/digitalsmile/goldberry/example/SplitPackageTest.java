package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// No two Goldberry modules contain the same package — [ADR-0387].
///
/// ## The bug this exists for
///
/// `goldberry-emoji` shipped its font as
/// `io/github/digitalsmile/goldberry/assets/fonts/OpenMoji-black.ttf`, beside the
/// faces `goldberry-core` still ships. A **resource directory is a package** to
/// the module system exactly as a directory of classes is, so two modules
/// contained `io.github.digitalsmile.goldberry.assets.fonts` and the application
/// died before its first frame:
///
/// ```
/// java.lang.LayerInstantiationException: Package io.github.digitalsmile.goldberry.assets.fonts
///     in both module io.github.digitalsmile.goldberry.emoji and module …core
/// ```
///
/// Every test passed. Tests run on a **class path**, where a split package is
/// merely two directories and nothing objects; `./gradlew :example:run` puts the
/// modules on a module path, which is where the rule lives. So the suite was
/// green and the application would not open — which is the shape of failure this
/// repository writes tests for.
///
/// ## Why it is a walk of the artifacts and not `ModuleDescriptor#packages`
///
/// That set comes from the `ModulePackages` attribute javac wrote, which is
/// about the *classes* it compiled. What the runtime objected to came from the
/// jar's own contents. So this asks the same question the JVM asks: which
/// directories hold files.
///
/// This is `:example`'s test because `:example` is the module that depends on
/// every other one — the only place in this build where the whole module graph
/// is on one path.
class SplitPackageTest {

    /// Directories that are not packages and never were.
    private static final Set<String> NOT_PACKAGES = Set.of("META-INF");

    @Test
    @DisplayName("no package is in two modules, which is what the module path refuses")
    void noSplitPackages() {
        var byModule = packagesByModule();

        assertTrue(
                byModule.size() > 4,
                () -> "found only " + byModule.keySet() + " on the class path; this test discovers the modules"
                        + " by walking it, and finding almost none means it checks nothing");

        var owners = new TreeMap<String, Set<String>>();
        byModule.forEach((module, packages) -> packages.forEach(
                name -> owners.computeIfAbsent(name, key -> new TreeSet<>()).add(module)));

        var split = new TreeMap<String, Set<String>>();
        owners.forEach((name, modules) -> {
            if (modules.size() > 1) {
                split.put(name, modules);
            }
        });

        assertTrue(
                split.isEmpty(),
                () -> "a package in two modules is a LayerInstantiationException before the first frame,"
                        + " and a class path hides it (ADR-0387). A module's resources belong under its own"
                        + " package: " + split);
    }

    /// Every Goldberry module's packages, taken from what its build outputs
    /// actually contain.
    ///
    /// The class path in a Gradle test run is this repository's own
    /// `…/<module>/build/classes/java/main` and `…/build/resources/main`
    /// directories plus the external jars. The module a directory belongs to is
    /// the path segment before `build`, which is the Gradle project's name.
    private static Map<String, Set<String>> packagesByModule() {
        var byModule = new LinkedHashMap<String, Set<String>>();
        for (var entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            var path = Path.of(entry);
            var module = moduleOf(path);
            if (module == null) {
                continue;
            }
            var packages = Files.isDirectory(path) ? packagesIn(path) : packagesInJar(path);
            byModule.computeIfAbsent(module, key -> new LinkedHashSet<>()).addAll(packages);
        }
        return byModule;
    }

    /// The Gradle project a build output belongs to, or null for anything else —
    /// an external jar, a test fixture, a directory of test classes.
    ///
    /// Two shapes, because Gradle hands a project dependency over as either: the
    /// `…/<project>/build/classes/java/main` directory, or the jar in
    /// `…/<project>/build/libs`. Both are the module's content and both are what
    /// the module path would be given.
    private static String moduleOf(Path path) {
        var names = new java.util.ArrayList<String>();
        path.forEach(part -> names.add(part.toString()));
        var build = names.lastIndexOf("build");
        if (build <= 0) {
            return null;
        }
        var module = names.get(build - 1);
        if (names.contains("libs")) {
            var file = names.getLast();
            // The module's own jar, and not its sources, its javadoc or its test
            // fixtures — none of which is on a module path.
            return file.endsWith(".jar")
                            && !file.contains("-sources")
                            && !file.contains("-javadoc")
                            && !file.contains("-test-fixtures")
                    ? module
                    : null;
        }
        // `main` and nothing else: test classes and test fixtures are not part of
        // any published module, and two modules' *tests* sharing a package is
        // ordinary.
        return names.contains("main") ? module : null;
    }

    /// The same, for a module handed over as a jar.
    private static Set<String> packagesInJar(Path jar) {
        var packages = new TreeSet<String>();
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                var slash = entry.getName().lastIndexOf('/');
                if (slash < 0) {
                    continue;
                }
                var name = entry.getName().substring(0, slash).replace('/', '.');
                if (NOT_PACKAGES.stream().anyMatch(skipped -> name.equals(skipped) || name.startsWith(skipped + "."))) {
                    continue;
                }
                packages.add(name);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return packages;
    }

    /// The packages a directory of build output holds — every directory with a
    /// file in it, which is the rule the module system applies.
    private static Set<String> packagesIn(Path root) {
        var packages = new TreeSet<String>();
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                var relative = root.relativize(file).getParent();
                if (relative == null) {
                    // A file at the root — `module-info.class` is the one that
                    // matters and it is in no package.
                    return;
                }
                var name = new StringBuilder();
                for (var part : relative) {
                    var segment = part.toString();
                    if (NOT_PACKAGES.contains(segment)) {
                        return;
                    }
                    if (!name.isEmpty()) {
                        name.append('.');
                    }
                    name.append(segment);
                }
                packages.add(name.toString());
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return packages;
    }
}
