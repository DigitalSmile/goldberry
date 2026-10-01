package dev.goldberry.build.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every shipped package says what it is, and every package a module compiles
 * under NullAway has opted in to it (ADR-0497).
 *
 * <p>The adoption this closes went a package at a time for a month and stopped
 * at 103 of 215, with nothing to say whether the next package would be marked.
 * {@code NullAway:OnlyNullMarked} makes an unmarked package invisible rather
 * than wrong, so a new one that forgot the file compiled, passed and was
 * checked by nothing. Qodana found the result: most of its 408 findings were
 * nullness in the packages NullAway never saw. This makes forgetting a red
 * build.
 *
 * <p>Read as text, over {@code src/main/java} and {@code src/testFixtures/java}.
 * Tests are left out: nothing links to a test package, and Error Prone is off
 * for them. A test-fixtures package that shares its name with a main package is
 * covered by the main one's {@code package-info}, which javac finds on the class
 * path.
 */
@DisplayName("package-info")
class PackageInfoTest {

    /** The source sets whose packages are shipped or shared. */
    private static final List<String> SOURCE_SETS = List.of("src/main/java", "src/testFixtures/java");

    /** A module compiled with Error Prone and NullAway applies the conventions. */
    private static final Pattern CONVENTIONS = Pattern.compile("id\\s+'goldberry\\.java-conventions'");

    /** The annotation, however it is spelled. */
    private static final Pattern NULL_MARKED =
            Pattern.compile("^@(?:org\\.jspecify\\.annotations\\.)?NullMarked\\s*$", Pattern.MULTILINE);

    /** A doc comment before the {@code package} line, in either syntax. */
    private static final Pattern DOCUMENTED = Pattern.compile("\\A\\s*(?:///|/\\*\\*)");

    @Test
    @DisplayName("every package with a class in it has a package-info.java")
    void everyPackageHasOne() {
        var missing = new TreeSet<String>();
        for (var pkg : packages()) {
            if (!Files.isRegularFile(pkg.directory().resolve("package-info.java"))) {
                missing.add(pkg.toString());
            }
        }
        assertEquals(Set.of(), missing, "packages with no package-info.java");
    }

    @Test
    @DisplayName("every package-info opens with a doc comment that says what the package is")
    void everyOneIsDocumented() {
        var undocumented = new TreeSet<String>();
        for (var pkg : packages()) {
            var info = pkg.directory().resolve("package-info.java");
            if (Files.isRegularFile(info) && !DOCUMENTED.matcher(read(info)).find()) {
                undocumented.add(pkg.toString());
            }
        }
        assertEquals(Set.of(), undocumented, "package-info.java with no doc comment");
    }

    @Test
    @DisplayName("every package of a module under NullAway is @NullMarked")
    void everyCheckedPackageIsMarked() {
        var unmarked = new TreeSet<String>();
        var checked = 0;
        for (var pkg : packages()) {
            var info = pkg.directory().resolve("package-info.java");
            if (!pkg.underNullAway() || !Files.isRegularFile(info)) {
                continue;
            }
            checked++;
            if (!NULL_MARKED.matcher(read(info)).find()) {
                unmarked.add(pkg.toString());
            }
        }
        assertTrue(checked > 200, "found only " + checked + " packages under NullAway; is the walk wrong?");
        assertEquals(Set.of(), unmarked, "packages NullAway cannot see");
    }

    /**
     * A directory of Java sources in one module's source set.
     *
     * @param module       the Gradle project, by directory name
     * @param directory    the package's directory
     * @param name         the package, dotted
     * @param underNullAway whether the module applies the conventions, and so NullAway
     */
    private record Package(String module, Path directory, String name, boolean underNullAway) {
        @Override
        public String toString() {
            return module + ": " + name;
        }
    }

    /** Every package with a class in it, across every module's shipped source sets. */
    private static List<Package> packages() {
        var root = Repository.root();
        try (var modules = Files.list(root)) {
            return modules.filter(module -> Files.isRegularFile(module.resolve("build.gradle")))
                    .flatMap(module -> packagesOf(module).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Package> packagesOf(Path module) {
        var underNullAway = CONVENTIONS.matcher(read(module.resolve("build.gradle"))).find();
        var mainRoot = module.resolve(SOURCE_SETS.getFirst());
        return SOURCE_SETS.stream()
                .map(module::resolve)
                .filter(Files::isDirectory)
                .flatMap(sourceRoot -> directoriesWithClasses(sourceRoot).stream()
                        .filter(directory -> sourceRoot.equals(mainRoot)
                                || !Files.isDirectory(mainRoot.resolve(sourceRoot.relativize(directory))))
                        .map(directory -> new Package(
                                module.getFileName().toString(),
                                directory,
                                sourceRoot.relativize(directory).toString().replace('/', '.'),
                                underNullAway)))
                .toList();
    }

    /** Directories holding a class, record or interface; the unnamed package's module-info is not one. */
    private static List<Path> directoriesWithClasses(Path sourceRoot) {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.getParent().equals(sourceRoot))
                    .filter(file -> {
                        var name = file.getFileName().toString();
                        return !name.equals("package-info.java") && !name.equals("module-info.java");
                    })
                    .map(Path::getParent)
                    .distinct()
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
