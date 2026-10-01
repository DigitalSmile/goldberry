package dev.goldberry.build.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.goldberry.build.publish.PublishedModules;

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
 * Everything Goldberry names is under {@code dev.goldberry}: the Maven group, every
 * package, every JPMS module and every native-image metadata directory
 * (ADR-0510, which supersedes ADR-0009).
 *
 * <p>The rename to {@code dev.goldberry} was a search and replace over 2,500
 * files. A file pasted in from an older branch, or a downstream snippet, would
 * bring the old root back, and it would still compile, because nothing ties a
 * package to the group. This test ties them together.
 *
 * <p>Read as text, over every top-level module's {@code src/<set>/java} and
 * {@code src/<set>/resources}.
 */
@DisplayName("the namespace")
class NamespaceTest {

    /** The root of every package, module and coordinate. */
    private static final String ROOT = "dev.goldberry";

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final Pattern MODULE = Pattern.compile("^(?:open\\s+)?module\\s+([\\w.]+)\\s*\\{", Pattern.MULTILINE);

    /** A source file's place in the tree: under {@code src/<set>/java}. */
    private record Source(Path file, Path sourceRoot) {

        /** The package the file's directory says it is in. */
        String expectedPackage() {
            var directory = sourceRoot.relativize(file.getParent());
            return directory.toString().replace(directory.getFileSystem().getSeparator(), ".");
        }
    }

    @Test
    @DisplayName("the Maven group is dev.goldberry")
    void groupIsTheRoot() {
        assertEquals(ROOT, PublishedModules.GROUP);
    }

    @Test
    @DisplayName("every Java source declares a package under dev.goldberry, in the directory it names")
    void everyPackageIsUnderTheRoot() {
        var sources = javaSources().stream()
                .filter(source -> !source.file().getFileName().toString().equals("module-info.java"))
                .toList();
        assertFalse(sources.isEmpty(), "no Java sources found under " + Repository.root());

        var wrong = new TreeSet<String>();
        for (var source : sources) {
            var matcher = PACKAGE.matcher(read(source.file()));
            var declared = matcher.find() ? matcher.group(1) : "<none>";
            if (!underRoot(declared) || !declared.equals(source.expectedPackage())) {
                wrong.add(relative(source.file()) + " declares " + declared);
            }
        }
        assertEquals(Set.of(), wrong, "sources outside " + ROOT + " or outside their package's directory");
    }

    @Test
    @DisplayName("every module is named under dev.goldberry")
    void everyModuleIsUnderTheRoot() {
        var descriptors = javaSources().stream()
                .filter(source -> source.file().getFileName().toString().equals("module-info.java"))
                .toList();
        assertFalse(descriptors.isEmpty(), "no module-info.java found under " + Repository.root());

        var wrong = new TreeSet<String>();
        for (var descriptor : descriptors) {
            var matcher = MODULE.matcher(read(descriptor.file()));
            var name = matcher.find() ? matcher.group(1) : "<none>";
            if (!underRoot(name)) {
                wrong.add(relative(descriptor.file()) + " names " + name);
            }
        }
        assertEquals(Set.of(), wrong, "modules outside " + ROOT);
    }

    @Test
    @DisplayName("every native-image metadata directory is keyed by the group")
    void nativeImageMetadataIsUnderTheGroup() {
        var groups = new TreeSet<String>();
        for (var resources : sourceRoots("resources")) {
            var metadata = resources.resolve("META-INF").resolve("native-image");
            if (Files.isDirectory(metadata)) {
                try (Stream<Path> children = Files.list(metadata)) {
                    children.filter(Files::isDirectory)
                            .forEach(group -> groups.add(group.getFileName().toString()));
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot read " + metadata, e);
                }
            }
        }
        assertFalse(groups.isEmpty(), "no META-INF/native-image directory found");
        assertEquals(Set.of(ROOT), groups, "native-image metadata directories");
    }

    @Test
    @DisplayName("a source's expected package is its directory below the source root")
    void expectedPackageIsTheDirectory() {
        var root = Path.of("core", "src", "main", "java");
        var source = new Source(root.resolve(Path.of("dev", "goldberry", "widget", "Widget.java")), root);
        assertEquals("dev.goldberry.widget", source.expectedPackage());
    }

    @Test
    @DisplayName("the root itself and its subpackages are under the root; a prefix that only looks like it is not")
    void underRootIsAPackagePrefix() {
        assertEquals(
                List.of(true, true, false, false),
                Stream.of("dev.goldberry", "dev.goldberry.css", "dev.goldberryx", "io.github.digitalsmile.goldberry")
                        .map(NamespaceTest::underRoot)
                        .toList());
    }

    private static boolean underRoot(String name) {
        return name.equals(ROOT) || name.startsWith(ROOT + ".");
    }

    private static List<Source> javaSources() {
        return sourceRoots("java").stream()
                .flatMap(root -> walk(root)
                        .filter(file -> file.getFileName().toString().endsWith(".java"))
                        .map(file -> new Source(file, root)))
                .toList();
    }

    /**
     * Every {@code <module>/src/<set>/<kind>} directory of a top-level module.
     * Only one level down, so third-party checkouts that carry Java of their own
     * (SDL's Android project under {@code natives/.deps}) are not mistaken for
     * Goldberry's.
     */
    private static List<Path> sourceRoots(String kind) {
        return list(Repository.root())
                .filter(module -> !module.getFileName().toString().startsWith("."))
                .map(module -> module.resolve("src"))
                .filter(Files::isDirectory)
                .flatMap(NamespaceTest::list)
                .map(set -> set.resolve(kind))
                .filter(Files::isDirectory)
                .toList();
    }

    private static Stream<Path> list(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(Files::isDirectory).sorted().toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory, e);
        }
    }

    private static Stream<Path> walk(Path from) {
        try (Stream<Path> files = Files.walk(from)) {
            return files.filter(Files::isRegularFile).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + from, e);
        }
    }

    private static String read(Path file) {
        try {
            return Repository.lineFeeds(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static String relative(Path file) {
        return Repository.root().relativize(file).toString();
    }
}
